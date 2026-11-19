# Respuestas a extravíos y entrega

Una respuesta a un extravío pertenece al reporte original y es privada entre su dueño y quien responde. No crea una publicación de hallazgo adicional.

## Entrega

Al crear una respuesta, el JSON de la parte `request` admite `custody` (`WITH_FINDER` u `OFFICE`) y `custodyLocation` (máximo 200 caracteres). Son opcionales para mantener compatibilidad; sin ellos se considera entrega personal. Para oficina sin lugar específico se usa «Oficina de profesores · edificio I».

`PUT /api/v1/lost-reports/{lossId}/responses/{responseId}/custody` recibe `{ "custody": "OFFICE", "location": "Secretaría del edificio I" }`. Solo puede modificarlo quien respondió, mientras ambos registros estén abiertos. Dejar en oficina impide volver a entrega personal, pero permite precisar el lugar. Cada cambio efectivo agrega un comentario al historial y notifica al dueño; repetir el mismo cambio no duplica mensajes.

El dueño confirma la recepción desde la respuesta. Esto recupera el reporte original y cierra sus otras respuestas; su historial permanece disponible para sus participantes.

## Identidad en conversaciones

Las respuestas y comentarios incluyen la versión actual de la foto y la carrera opcional de su autor. Las publicaciones de hallazgo y extravío incluyen la carrera opcional de quien las publicó. No se deriva de otros datos ni se completa si el perfil no la tiene.

Las fotos se consultan con sesión activa en:

- `/api/v1/lost-reports/{lossId}/responses/{responseId}/authors/{authorId}/photo`
- `/api/v1/claims/{claimId}/authors/{authorId}/photo`

Estos recursos verifican el acceso a la conversación y que el autor le pertenezca. No permiten consultar fotos de usuarios ajenos. Las respuestas usan `Cache-Control: no-store`; el cliente usa iniciales cuando no existe foto o no puede cargarse.

## Verificación

La suite Java contiene 160 pruebas. Se verifican permisos, transiciones, idempotencia, notificaciones, privacidad y cambios de foto/carrera. La comprobación de navegador cubre entrega personal y oficina, conversación y reclamo, imágenes y carrera, tres cuentas y tamaños móvil/escritorio. Las pruebas se ejecutan en bases aisladas, nunca sobre los datos de la demostración.
