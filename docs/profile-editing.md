# Perfil completo — módulos 2, 3, 4 y 5

El perfil se consulta y edita por la sesión autenticada. No se envían identificadores de usuario, roles ni correos para seleccionar la cuenta que se modifica. Los cambios de perfil no cambian credenciales ni cierran sesiones.

## Datos personales y académicos

GET /api/v1/auth/me conserva los campos anteriores y añade studentNumber y avatarVersion, ambos nulos cuando no corresponden. PUT /api/v1/auth/me acepta únicamente:

```json
{
  "displayName": "Usuario de prueba",
  "alias": "Alex",
  "classroom": "I-12",
  "groupName": "TI-51",
  "course": "5.º cuatrimestre",
  "degree": "Tecnologías de la información",
  "building": "I"
}
```

La respuesta es el perfil actualizado (200). Se requiere cookie de sesión y token CSRF. displayName es obligatorio (3..120 caracteres después de quitar espacios extremos). Alias admite 60; salón, grupo y edificio 80; curso 120; carrera 160. Los opcionales vacíos o con espacios se guardan como null. Se rechazan caracteres de control internos y campos ajenos al contrato. Esta operación reemplaza los datos editables: opcionales omitidos también quedan en null. La app siempre envía el formulario completo.

El correo, rol, matrícula y credenciales no se pueden modificar por este endpoint. Un alias no es único ni sirve para iniciar sesión. El directorio de responsables conserva su respuesta limitada; no se publican los campos académicos, alias ni fotografía.

## Matrícula

Se presenta studentNumber solo para STUDENT cuyo correo coincida exactamente con a + 10 dígitos + @utch.edu.mx. Se conservan los ceros iniciales. La regla reconoce el ejemplo aportado para el MVP; no se presenta como validación oficial de inscripción ni impide registrar otros correos institucionales. Otros formatos devuelven null y la app lo indica. Para docentes y secretaria devuelve null; no se inventa una matrícula. La matrícula es derivada, no existe una segunda copia editable en la base.

## Foto de perfil

- PUT /api/v1/auth/me/photo: multipart con campo file, respuesta 200 con perfil actualizado y avatarVersion.
- GET /api/v1/auth/me/photo: contenido privado JPEG de la cuenta autenticada. Sin foto: 404; sin sesión: 401. La app añade ?v=UUID para reemplazar la imagen visible cuando cambia.
- DELETE /api/v1/auth/me/photo: elimina la foto y devuelve el perfil actualizado; repetir sin foto también devuelve 200.

Se requiere CSRF en PUT y DELETE. La entrada acepta JPEG/PNG reales de hasta 5 MiB, 12 megapíxeles y 6000 píxeles por lado. El servidor decodifica, recorta al centro y escala a 512 × 512, con fondo blanco para transparencias. Se vuelve a codificar como JPEG para excluir metadatos y datos añadidos. Las respuestas son no-store y nosniff.

La migración V9 añade user_profile_photo, una fila por usuario, con bytes, versión UUID y fecha de actualización. Reemplazar actualiza esa fila; quitar elimina los bytes. No se reutilizan fotos de publicaciones. Los cambios de perfil/foto se auditan sin registrar su contenido. El bloqueo de la cuenta serializa escrituras concurrentes.

## Pantalla Mi cuenta

La tarjeta superior muestra nombre, rol, alias, foto, correo y matrícula. La fotografía tiene vista previa y Guardar foto/Cancelar foto independientes del formulario. Quitar también queda pendiente hasta guardar. Seleccionar o cancelar una foto no cambia el servidor.

Tus datos e Información académica comparten Guardar cambios/Cancelar cambios. El formulario conserva los datos escritos si falla un guardado y bloquea otras operaciones de cuenta mientras guarda. Cambiar la foto no descarta los datos personales que se estaban editando. Al guardar, el contexto de sesión actualiza el nombre y avatar de la barra superior sin recargar. Los cambios se recuperan al volver a iniciar sesión.

Contraseña y acceso se abre para mostrar el formulario de contraseña, con controles Mostrar/Ocultar independientes para los tres campos. Conserva el flujo anterior de revocación de sesiones al cambiarla. Mi cuenta mantiene el reintento de consulta del módulo 1 y no habilita edición con una consulta fallida.

## Comprobaciones

Las pruebas de backend cubren validación, normalización de vacíos, rechazo de cambios de identidad/rol, CSRF, privacidad entre cuentas, matrícula y ciclo de foto (alta/reemplazo/eliminación, límites, normalización y sesión deshabilitada). Las comprobaciones de navegador usan datos ficticios y una base temporal separada para revisar guardado, cancelación, persistencia, errores y distribución adaptable.

## Resultado de verificación

- `mvn verify`: 80 pruebas, sin fallos ni errores. Se separaron los contextos de pruebas de perfil para no consumir el límite de autenticación de otros casos; el límite real no se modificó. Se repitieron las siete pruebas de perfil tras reforzar el caso de campos de identidad/rol ajenos al contrato.
- Compilación de frontend y PWA correcta. OpenAPI publica 38 rutas y 49 operaciones.
- Navegador real contra base temporal: nombre y alias, cinco datos académicos, normalización, cancelar sin escrituras, rechazo de nombre vacío, conservar borrador tras error simulado, avatar (vista previa/cancelar/guardar/reemplazar/eliminar), archivo inválido y exceso de tamaño, persistencia tras recargar y entrar de nuevo, cuenta docente independiente, matrícula con ceros iniciales, mostrar/ocultar contraseñas y cambio de contraseña con revocación.
- Diseño revisado sin desbordamiento entre 320 y 1920 px; capturas de escritorio y móvil. Sin errores de JavaScript. Una respuesta tardía del perfil no vuelve a autenticar una sesión vencida.

- Instalación local: migración V8 a V9 aplicada con respaldo previo. Las 11 cuentas, 6 hallazgos, 2 reportes de extravío, 4 reclamos y 2 fotografías de publicaciones se conservaron exactamente. La nueva tabla de avatares empezó vacía. Se comprobó la pantalla instalada con una cuenta demo sin guardar cambios en esos datos.
