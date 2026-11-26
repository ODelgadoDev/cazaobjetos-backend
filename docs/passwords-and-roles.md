# Módulo 5: recuperación, sesiones, roles y SMTP

Se conserva el registro institucional y la verificación del módulo de cuentas. El registro público sigue asignando STUDENT: el prefijo del correo no concede privilegios. No hay una API pública para asignar roles.

## Recuperación de contraseña

1. GET `/api/v1/auth/csrf` para obtener token y cookie.
2. POST `/api/v1/auth/forgot-password` con `{"email":"persona@utch.edu.mx"}` y el header CSRF → 202.
3. Leer el código recibido por correo (Mailpit en desarrollo).
4. POST `/api/v1/auth/reset-password` con `{"token":"código","newPassword":"Tu-nueva-clave-2026"}` y CSRF → 204.
5. Volver a iniciar sesión; el restablecimiento no inicia sesión automáticamente.

Una solicitud con email válido devuelve el mismo 202 si la cuenta existe, está ausente, deshabilitada o dentro del período de espera. Solo emite correo para cuentas habilitadas. Nunca habilita una cuenta pendiente ni una cuenta desactivada. Las cuentas demo habilitadas también pueden recuperar su contraseña en desarrollo.

Los códigos contienen 256 bits aleatorios, vencen en 30 minutos y se consumen una sola vez, incluso ante solicitudes simultáneas. `password_reset` solo almacena el hash SHA-256. Se aplica un intervalo de 60 segundos entre emisiones; emitir otro código invalida el anterior. Los mensajes de la outbox contienen el código para entregarlo, por lo que la base y Mailpit son recursos privados. No se devuelve el código desde la API ni se imprime en logs.

Código inválido, vencido, reemplazado o consumido → 400. Contraseña: mínimo 8 caracteres, un número, un símbolo y máximo 72 bytes UTF-8; espacios iniciales/finales forman parte de la contraseña y no se recortan. BCrypt conserva la política existente.

## Cambio con sesión iniciada

POST `/api/v1/auth/change-password`:

```json
{"currentPassword":"Tu-clave-actual-2026","newPassword":"Tu-nueva-clave-2026"}
```

Requiere sesión y CSRF. Devuelve 204; contraseña actual incorrecta → 400; sin sesión → 401; sin CSRF → 403. Invalida también cualquier código de recuperación pendiente. Las operaciones registran PASSWORD_CHANGED en auditoría sin guardar contraseñas ni códigos.

## Revocación de sesiones

La cuenta tiene `credentials_version`. El login guarda esa versión en el principal de la sesión; las peticiones autenticadas la comparan con la base. Recuperar/cambiar contraseña o cambiar el rol mediante el script incrementa la versión. Las sesiones antiguas se invalidan al intentar su siguiente petición y reciben 401, incluida la sesión que cambió la contraseña. Una cuenta deshabilitada también pierde acceso en su siguiente petición. No cancela operaciones que ya estuvieran en curso.

Después de ese 401, solicita un nuevo CSRF y vuelve a iniciar sesión. Si la primera petición tras el cambio es GET csrf, esa primera petición puede devolver 401; repítela después de descartar la sesión anterior. No basta con conservar el token CSRF previo.

El límite compartido de 30 POST por IP/minuto incluye login, registro, verificación, reenvío, recuperación, restablecimiento y cambio de contraseña. Es local a una sola instancia y no sustituye la estrategia de despliegue distribuido.

## Asignación de roles por el operador de la base

`scripts/set-role.sql` requiere una cuenta habilitada que haya verificado el correo. Permite STUDENT, TEACHER o SECRETARY; registra el rol anterior/nuevo y el usuario de PostgreSQL en `account_role_audit`. Repetir el mismo rol no crea otro evento ni incrementa la versión. Un rol inválido o una cuenta no elegible produce un error y no cambia nada.

Ejemplo desde la carpeta del proyecto, sustituyendo el correo por el de la persona autorizada:

```bash
docker compose exec -T db psql -U cazaobjetos -d cazaobjetos \
  -v email='persona@utch.edu.mx' -v new_role='TEACHER' < scripts/set-role.sql
```

La autorización proviene del acceso administrativo a PostgreSQL, no de ser secretaria en la app. El script no habilita cuentas ni verifica correos. No se asignó ni cambió el rol de una persona real durante este desarrollo. En producción usa credenciales individuales del operador si necesitas atribución individual; `database_actor` identifica el usuario de la conexión, no a una persona por sí solo.

## SMTP externo preparado, sin activar

El perfil `smtp` crea un emisor autenticado con STARTTLS obligatorio y comprobación del certificado/hostname. Tiene timeouts de 5 segundos y reutiliza outbox con reintentos. El emisor usa SMTP_FROM. No se activa con dev ni test. `run.sh` continúa arrancando en dev y entregando únicamente a Mailpit.

Configura mediante variables del entorno o un gestor de secretos:

- SMTP_HOST y SMTP_PORT (587 por defecto).
- SMTP_USERNAME y SMTP_PASSWORD.
- SMTP_FROM: remitente autorizado por el proveedor.
- Las variables de base de datos y despliegue habituales.

Con una configuración autorizada, el arranque es `SPRING_PROFILES_ACTIVE=smtp java -jar target/cazaobjetos-0.1.0-SNAPSHOT.jar`. El servicio necesita HTTPS para las cookies seguras en ese perfil. No uses `run.sh` para activar SMTP externo.

No se verificó entrega con un proveedor real: faltan servidor, credenciales y remitente autorizados. Esta implementación espera un relay con usuario/contraseña y STARTTLS; no implementa OAuth ni SMTPS implícito en 465. Antes de activarlo utiliza una base separada de producción: el trabajador enviará los mensajes pendientes, por lo que no debes conectar un proveedor real a la outbox de cuentas ficticias de desarrollo. No se enviaron mensajes fuera de Mailpit en las pruebas.

Referencia de configuración y límites de espera: [Spring Boot: Sending Email](https://docs.spring.io/spring-boot/reference/io/email.html).

## Migración y Postman

Flyway V5 agrega `credentials_version`, `password_reset` y `account_role_audit`; no elimina datos ni cambia contraseñas existentes.

Importa `Cuentas.postman_collection.json` y `Cuentas-local.postman_environment.json`. Selecciona **CazaObjetos · Cuentas local** y ejecuta Runner. Las 29 peticiones lógicas crean/verifican una cuenta ficticia, recuperan la contraseña por Mailpit, comprueban el rechazo de la clave anterior, cambian la contraseña autenticados y prueban la revocación. La búsqueda del correo se repite mientras espera su entrega; por ello el número real de peticiones puede ser mayor. La clave final de la cuenta ficticia queda en la variable `finalPassword`. No cambia las cuentas demo existentes.

Verificado el 10 de noviembre de 2026: 57 pruebas Java sin fallos; colección de cuentas: 34 peticiones HTTP (incluyendo esperas del buzón) y 40 assertions sin fallos. Script de roles validado con cuenta ficticia: asignación, idempotencia, auditoría y errores. SMTP externo solo tiene comprobación de configuración, sin prueba de entrega con un proveedor.

Pendientes operativos: credenciales y prueba de entrega SMTP real, política de retención/limpieza de códigos y mensajes, y conexión de las pantallas del frontend.

Regresión adicional: la colección original completó 40 peticiones y 47 assertions sin fallos con la comprobación nueva de sesiones.
