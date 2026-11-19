# Registro de alumnos y datos académicos

El registro público crea cuentas STUDENT con un correo de la forma `a` + 10 dígitos + `@utch.edu.mx`. El correo se guarda en minúsculas. La matrícula se obtiene de esos diez dígitos al consultar el perfil; no existe un campo editable ni se confía en una matrícula enviada por el cliente. Se conservan los ceros iniciales.

`POST /api/v1/auth/register` requiere:

```json
{
  "email": "a1234567890@utch.edu.mx",
  "displayName": "Alumno de prueba",
  "degree": "Tecnologías de la Información",
  "building": "I",
  "password": "Una-clave-de-prueba-2026"
}
```

Carrera (máximo 160 caracteres) y edificio (máximo 80) son obligatorios, se guardan recortados y no admiten saltos de línea. `PUT /api/v1/auth/me` también requiere ambos campos, por lo que no pueden borrarse accidentalmente al editar el perfil. Los datos de las cuentas previas permanecen intactos; si están incompletos, el formulario solicita completarlos al guardar.

En modo normal, la cuenta permanece deshabilitada hasta consumir el código de verificación. La extracción de matrícula reconoce el formato y no comprueba la inscripción del alumno.

En el entorno local con perfil `dev` y `LOCAL_MAIL_ENABLED=true`, los mensajes se entregan exclusivamente al SMTP de loopback `127.0.0.1:1025`. Se pueden usar correos ficticios del formato indicado y consultar sus códigos en Mailpit, `http://127.0.0.1:8025`. Esta configuración local no envía correos a Internet.

Se verificaron 162 pruebas Java y 17 comprobaciones de navegador: campos obligatorios, matrícula y ceros iniciales, código en Mailpit, acceso posterior, datos del perfil, carrera en publicaciones y diseño móvil/escritorio.

## Activación temporal sin correo

Para las pruebas locales se puede usar el perfil `dev` con `DEMO_EMAIL_VERIFICATION=true`. El valor predeterminado es `false`; fuera de `dev` la activación de prueba permanece deshabilitada aunque se configure la propiedad.

`GET /api/v1/auth/registration-options` informa `demoVerification`. La interfaz consulta esta opción para mostrar «Verificar correo» sin campo de código, reenvío ni temporizador. `POST /api/v1/auth/verify-email/demo` recibe `{ "email": "a1234567890@utch.edu.mx" }`, activa un registro pendiente y consume su verificación anterior. Es idempotente para cuentas ya activas. No crea cuentas, cambia contraseñas ni rehabilita cuentas verificadas deshabilitadas. Mantiene la protección CSRF y la limitación de intentos.

Los registros nuevos de este modo no generan correos de verificación. Los pendientes anteriores también pueden activarse sin recuperar su código. Tras activar, la app lleva a iniciar sesión con el correo rellenado y la contraseña elegida durante el registro.

Para volver al flujo con código, configura `DEMO_EMAIL_VERIFICATION=false` y reinicia el backend. La verificación normal sigue disponible y probada.

Verificación de este incremento: 166 pruebas Java y 12 comprobaciones de navegador, con cuentas ficticias y bases aisladas. Se probó registro, activación, inicio de sesión, conservación de datos, cuentas pendientes, modo normal y diseño móvil/escritorio.
