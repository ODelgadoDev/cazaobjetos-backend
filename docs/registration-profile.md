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

La cuenta permanece deshabilitada hasta consumir el código de verificación. La extracción de matrícula reconoce el formato y no comprueba la inscripción del alumno.

En el entorno local con perfil `dev` y `LOCAL_MAIL_ENABLED=true`, los mensajes se entregan exclusivamente al SMTP de loopback `127.0.0.1:1025`. Se pueden usar correos ficticios del formato indicado y consultar sus códigos en Mailpit, `http://127.0.0.1:8025`. Esta configuración local no envía correos a Internet.

Se verificaron 162 pruebas Java y 17 comprobaciones de navegador: campos obligatorios, matrícula y ceros iniciales, código en Mailpit, acceso posterior, datos del perfil, carrera en publicaciones y diseño móvil/escritorio.
