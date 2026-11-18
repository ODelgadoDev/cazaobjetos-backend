# Base del perfil — módulo 1

## Diagnóstico

La captura de Mi cuenta muestra un HTTP 500, pero no se reprodujo en el entorno local actual: el acceso demo y 20 consultas seguidas a GET /api/v1/auth/me, a través del proxy del frontend, respondieron correctamente. No se encontró una traza que permita atribuir ese 500 a una causa concreta. Esta revisión no da por corregido un error del servidor que no se pudo reproducir.

El contrato anterior solo devolvía id, email, displayName, role y createdAt. El frontend conservaba los datos de la sesión si fallaba la consulta, pero mostraba un error genérico sin reintento. Además, un 401 en /auth/me no emitía el mismo aviso de sesión vencida que otras consultas protegidas.

## Cambios de este módulo

- GET /api/v1/auth/me conserva sus campos actuales y añade seis datos opcionales. Solo consulta la cuenta autenticada; no acepta un identificador de otra cuenta.
- UserDataService obtiene la cuenta habilitada en una consulta en lugar de buscar primero el ID y después volver a cargar la misma cuenta.
- Mi cuenta identifica cuándo muestra los datos de la sesión por un fallo, ofrece Reintentar y anuncia la carga. Una sesión rechazada con 401 devuelve al acceso.
- La migración V8 añade columnas opcionales a app_user. No modifica identificadores, correos, nombres, contraseñas, roles ni publicaciones existentes. No modifica migraciones anteriores.

| Campo REST | Columna | Límite | Adaptador del frontend |
| --- | --- | --- | --- |
| alias | alias | 60 | alias |
| classroom | classroom | 80 | salon |
| groupName | group_name | 80 | grupo |
| course | course | 120 | curso |
| degree | degree | 160 | carrera |
| building | building | 80 | edificio |

Los campos ausentes son null; las columnas no aceptan cadenas que solo contengan espacios ASCII. No se exige completar datos nuevos a las cuentas anteriores. El alias no es único ni sirve para iniciar sesión.

Estos datos solo se añaden a la respuesta privada de la sesión. El directorio de docentes y secretaria mantiene su contrato reducido y no los publica. Las respuestas no incluyen hashes de contraseña, códigos ni versión de credenciales. OpenAPI describe los nuevos campos y su nulabilidad.

## Límites del incremento

Todavía no existe un endpoint de edición de perfil. Nombre y alias se habilitarán en el módulo 2; información académica y matrícula en el 3; foto en el 4; formulario completo en el 5. La matrícula no se almacena ni se infiere todavía: el ejemplo a1123150066@utch.edu.mx no demuestra una regla universal para estudiantes o docentes. No se añaden RFC, CURP ni fecha de nacimiento de la captura: no son necesarios para esta aplicación.

La foto de perfil tendrá almacenamiento y ciclo de reemplazo propios; no se reutilizan fotografías de publicaciones ni se guardan imágenes como texto en app_user.

## Verificación

ProfileControllerTest cubre acceso anónimo, cuentas anteriores con datos opcionales nulos, lectura de los nuevos campos, aislamiento entre cuentas, privacidad del directorio y rechazo de edición no implementada. Las pruebas se ejecutan contra una base temporal, nunca contra la base local de uso.

## Resultado local — 17 de noviembre de 2026

- `mvn verify`: 72 pruebas, sin fallos, errores ni omisiones, contra PostgreSQL temporal con TEST_DB_URL independiente. Incluye las cuatro pruebas nuevas de perfil.
- `npm run build`: compilación y generación de la PWA correctas.
- Navegador Chromium: consulta real de Mi cuenta correcta; fallo 500 simulado conserva la información de sesión y permite recuperar la consulta con Reintentar; respuesta 401 simulada descarta la sesión y muestra el acceso. Sin errores de JavaScript. Comprobado el aviso en escritorio y sin desbordamiento horizontal a 390 px.
- Flyway local: actualización de V7 a V8 correcta. Comparación completa antes/después: se conservaron exactamente 11 cuentas, 6 hallazgos, 2 reportes de extravío, 4 reclamos y 2 fotografías. Los seis campos nuevos quedaron nulos en las cuentas anteriores.
- Se creó un respaldo local de PostgreSQL antes de la migración y se conservaron los archivos/JAR anteriores. Ninguna publicación en GitHub.

## Continuación

La edición y fotografía descritas como pendientes en este diagnóstico del módulo 1 se implementaron en los módulos 2–5. Consulta el contrato vigente en [perfil completo](profile-editing.md).
