# Módulo 3: fotografías

Cada hallazgo o extravío admite hasta 3 fotografías opcionales. Se publican primero los datos del objeto; después se suben las fotos individualmente. No se cambian los DTOs existentes del catálogo ni de publicación.

## API

Para hallazgos usa `/api/v1/found-items/{parent}/photos`; para extravíos, `/api/v1/lost-reports/{parent}/photos`.

| Método | Ruta relativa | Resultado |
|---|---|---|
| POST | /photos | Multipart con campo `file`; 201 con metadatos |
| GET | /photos | 200, array ordenado por fecha de carga e ID |
| GET | /photos/{photoId} | 200, bytes JPEG/PNG |
| DELETE | /photos/{photoId} | 204; volver a eliminar devuelve 404 |

Metadatos: `{ "id": "UUID", "contentType": "image/png", "sizeBytes": 1234, "createdAt": "..." }`. `sizeBytes` corresponde a la imagen procesada. Construye la URL de consulta usando el ID de publicación y el de foto; no hay enlaces públicos permanentes.

Se requiere sesión en todas las rutas. POST y DELETE también requieren el header CSRF. En navegador, utiliza FormData con `file`; deja que el navegador establezca Content-Type y el boundary. Conserva las cookies (`credentials: 'include'` si aplica). Desde el módulo 6, CORS puede configurarse con orígenes explícitos; consulta integration.md.

## Permisos y estados

Solo el responsable actual del hallazgo o el dueño del extravío puede subir o eliminar fotos. Reclamos pendientes o aceptados bloquean modificaciones con 409, al igual que en el módulo 2. Publicaciones devueltas, recuperadas o retiradas no admiten cambios de fotos.

Los usuarios autenticados pueden consultar las fotos de publicaciones no retiradas. Al retirar una publicación se conservan sus fotos; solo el reportante/responsable del hallazgo o el dueño del extravío puede consultarlas para su historial. Los demás reciben 404. Una foto consultada bajo otro ID de publicación devuelve 404. La imagen se responde con `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`.

## Validación y almacenamiento

- JPEG y PNG, comprobados por su contenido; el nombre y MIME enviado no determinan el formato.
- Máximo 5 MiB (5.242.880 bytes) por archivo y también por imagen procesada; 6 MiB para la petición multipart.
- Máximo 12 millones de píxeles y 6000 píxeles por lado.
- El servidor decodifica y vuelve a codificar la imagen, sin copiar metadatos originales ni el nombre del archivo. Se descartan EXIF/GPS y contenido añadido al final.
- Solo se conserva la primera imagen; no hay soporte para animaciones, SVG, PDF, GIF, HEIC ni WebP. No se generan miniaturas. La orientación EXIF no se aplica automáticamente: las fotos deben enviarse ya orientadas.
- Archivo vacío o dañado: 400; formato no permitido: 415; tamaño excesivo: 413; límite de tres fotos o estado bloqueado: 409; usuario sin permiso: 403.

Flyway V4 crea `publication_photo` con claves foráneas, metadatos y contenido `bytea`. Para este MVP se almacena en PostgreSQL: fotos, auditoría y operaciones quedan en la misma transacción; no se usan rutas del usuario ni carpetas públicas. Los respaldos de la base incluyen fotos. Si el volumen crece, deberá evaluarse migrar los bytes a almacenamiento de objetos. Las consultas de listas solo seleccionan metadatos. Las cargas toman el bloqueo del padre para proteger el máximo de tres, incluso con solicitudes simultáneas.

## Postman

Importa `Fotos.postman_collection.json`, selecciona **CazaObjetos · Local** y configura `devPassword`. En las dos peticiones “Subir PNG”, abre Body → form-data, campo `file` de tipo File, y selecciona `postman/fixtures/photo.png` o tu propia imagen PNG. No agregues manualmente Content-Type a esas peticiones.

Ejecuta las 20 peticiones en orden. Crean un hallazgo y un extravío ficticios, suben/consultan/eliminan una foto de cada uno. Las publicaciones de prueba permanecen en la base. Para Newman usa `--working-dir postman` para resolver `fixtures/photo.png`.

Verificado el 10 de noviembre de 2026: `mvn verify` completó 44 pruebas sin fallos contra PostgreSQL temporal, con migración V4, validación de contenido y tamaño, permisos, estados, CSRF, descarga y concurrencia. Newman completó las 20 peticiones de fotos y 26 assertions sin fallos.
