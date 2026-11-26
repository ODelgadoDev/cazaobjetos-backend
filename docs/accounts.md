# Registro y verificación de correo

Incremento del 5 de noviembre de 2026. Se conserva la estructura controller → service → repository/model con DTOs record y migraciones Flyway.

## Flujo

1. Obtener CSRF con GET `/api/v1/auth/csrf`.
2. POST `/api/v1/auth/register`, JSON con `email`, `password` y `displayName` → 202.
3. La cuenta queda deshabilitada. Se registra un mensaje con código en outbox dentro de la misma transacción.
4. Leer el código en Mailpit local.
5. POST `/api/v1/auth/verify-email`, JSON `{"token":"código"}` → 204.
6. Login habitual por formulario; GET CSRF de nuevo después del login.

Todos los POST requieren token CSRF y la cookie asociada. El registro acepta únicamente el dominio exacto `utch.edu.mx`, sin imponer el prefijo `a` ni una matrícula que todavía no hemos confirmado. La contraseña requiere al menos 8 caracteres, un número y un símbolo, con un máximo de 72 bytes UTF-8 por BCrypt. El rol se fija en STUDENT; docente/secretaria requieren asignación autorizada.

Ejemplo de registro (cuenta ficticia, buzón de pruebas):

```json
{
  "email": "prueba.registro@utch.edu.mx",
  "password": "Clave-prueba-2026",
  "displayName": "Alumno de prueba"
}
```

El código contiene 256 bits aleatorios, vence en 30 minutos y se acepta una sola vez, incluso si llegan dos verificaciones simultáneas. En `email_verification` se guarda su hash SHA-256. El mensaje pendiente contiene el código para permitir su entrega; la outbox y el buzón local son datos privados de desarrollo, no endpoints públicos del backend. No se devuelven códigos por la API de registro ni se imprimen en logs.

POST `/api/v1/auth/resend-verification` acepta `{"email":"prueba.registro@utch.edu.mx"}`. Devuelve 202 tanto si reenvía como si no aplica. Solo reenvía tras 60 segundos desde la emisión anterior; el nuevo código invalida el anterior. No reactiva cuentas verificadas que después hayan sido deshabilitadas.

## Arranque y buzón local

`./run.sh` inicia PostgreSQL y Mailpit con Compose y activa el trabajador de correo local. Mailpit captura mensajes; no los entrega a los correos institucionales reales.

- API: http://localhost:8080
- Buzón local: http://localhost:8025
- SMTP local: 127.0.0.1:1025

Mailpit está publicado únicamente en loopback, sin configuración de relay. El emisor solo se crea en el perfil dev con `LOCAL_MAIL_ENABLED=true` y usa 127.0.0.1. El backend sin este modo conserva los mensajes pendientes.

Para `./run.sh --no-db`, inicia Mailpit aparte (`docker compose up -d mailpit`) y exporta `LOCAL_MAIL_ENABLED=true`; usa `MAIL_SMTP_PORT` si cambiaste el puerto. No uses las cuentas ficticias para intentar probar correos institucionales reales. La configuración SMTP externa se describe en [módulo 5](passwords-and-roles.md); la entrega institucional real sigue pendiente de credenciales y prueba.

El trabajador consulta cada 5 segundos, bloquea lotes con SKIP LOCKED y reintenta fallos hasta 5 veces con esperas crecientes de 30 segundos. El estado termina en SENT o FAILED. No duplica la fila de aviso al repetir recuperación; SMTP es entrega al menos una vez: un fallo entre envío y commit puede producir mensajes repetidos. Para un mensaje FAILED de verificación, solicita otro código; los avisos de devolución fallidos todavía requieren gestión operativa.

## Pruebas en Postman

Importa `postman/Registro.postman_collection.json` y `postman/Registro-local.postman_environment.json`. Selecciona **CazaObjetos · Registro local** y ejecuta **Run collection**. Crea una dirección ficticia única, comprueba que el login inicialmente falle, consulta el mensaje local, verifica el código e inicia sesión. La consulta del buzón se repite hasta 20 segundos con Runner; si lo haces con Send manualmente, repite el paso de buscar correo hasta que aparezca.

También puedes abrir Mailpit en el navegador, copiar el código del mensaje y pegarlo en `verificationToken` para aprender el flujo manualmente.

## Límites del incremento

- 30 intentos POST por IP/minuto en login, registro, verificación, reenvío y los endpoints de contraseñas. Límite en memoria para una sola instancia; un despliegue con proxy o varias réplicas necesitará otra estrategia. Respuesta 429 y Retry-After: 60.
- El registro duplicado devuelve 409; reenvío no distingue cuentas existentes. La política de ocultación de existencia de cuentas queda por revisar para producción.
- Cuentas demo del perfil dev siguen habilitadas para compatibilidad con las pruebas anteriores.
- Recuperación, cambio de contraseña, revocación de sesiones y asignación local de roles implementados en [módulo 5](passwords-and-roles.md). Pendiente: política de retención, entrega SMTP institucional real y frontend.

Referencias: https://docs.spring.io/spring-boot/reference/io/email.html y https://mailpit.axllent.org/docs/install/docker/.
