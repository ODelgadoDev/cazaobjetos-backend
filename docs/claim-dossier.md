# Expediente privado del reclamo — grupo 1

Actualizado: 18 de noviembre de 2026. Las rutas llevan `/api/v1`; requieren sesión y CSRF en cada escritura. La identidad procede de la sesión, nunca de un ID enviado por el cliente.

## Participantes y proceso

1. El solicitante describe datos privados que prueban su propiedad. Puede adjuntar imágenes al iniciar la solicitud.
2. Solo ese solicitante y el responsable actual del hallazgo consultan y aportan al expediente. Ser docente o secretaría por sí solo no concede acceso. Otros usuarios reciben 404, incluso al pedir una imagen por UUID.
3. PENDING y ACCEPTED permiten comentarios y evidencias si el hallazgo sigue AVAILABLE. El responsable acepta o rechaza; el solicitante cancela o, después de la aceptación, confirma la recepción física.
4. COMPLETED, REJECTED, CANCELLED y CLOSED conservan imágenes, comentarios y eventos como lectura. No hay edición ni eliminación de aportaciones publicadas.
5. Completar cierra atómicamente el hallazgo, el extravío vinculado y los otros reclamos pendientes. El historial de estos últimos registra el cierre como Sistema, sin revelar quién completó el otro reclamo.

El docente es una referencia de palabra: no se selecciona una cuenta, no recibe tareas ni cambia el responsable. La oficina tampoco necesita aprobar para que los participantes terminen el trámite. Se conservan las delegaciones antiguas y sus permisos; no se reasignan registros existentes.

## API

| Método | Ruta | Entrada y resultado |
| --- | --- | --- |
| POST | `/found-items/{id}/claims` | JSON anterior, o multipart con parte JSON `request` y hasta cinco partes `files`; 201 `{id}`. La solicitud, las imágenes y los eventos se guardan juntos. |
| GET | `/claims/{id}` | Contrato anterior más `canContribute`. |
| GET | `/claims/{id}/activity?page=0&pageSize=20` | Página del historial, más reciente primero; orden estable por fecha y secuencia. Máximo 100 por página. |
| POST | `/claims/{id}/comments` | JSON `{"body":"Texto"}`; 201 entrada del historial. Entre 1 y 2000 caracteres tras quitar espacios exteriores; acepta saltos de línea y tabulaciones. |
| GET | `/claims/{id}/evidence` | Metadatos privados de las imágenes. |
| POST | `/claims/{id}/evidence` | Multipart `file`; 201 metadatos. Se permite a cualquiera de los dos participantes mientras esté activo. |
| GET | `/claims/{id}/evidence/{imageId}` | Imagen validada con `Cache-Control: no-store`, `nosniff` y nombre genérico. No se expone una ruta pública. |
| PUT | `/found-items/{id}/custody` | `{"custody":"WITH_TEACHER","teacherName":"Nombre","location":"Salón I-12"}`; 204. |
| POST | `/claims/{id}/reject` | Cuerpo opcional `{"reason":"Motivo"}` de hasta 2000 caracteres. Se conserva en la bitácora. Las llamadas antiguas sin cuerpo siguen válidas. |

Crear el reclamo admite `Idempotency-Key` con las reglas anteriores. Comentarios y cargas individuales admiten también una clave opcional de 8 a 100 caracteres. Reenviar la misma clave y contenido devuelve la aportación original; cambiar su contenido da 409. La interfaz conserva la clave y el borrador si falla la red. Una repetición de creación con su misma clave no agrega imágenes otra vez, incluso después del cierre.

La parte JSON `request` multipart tiene los mismos campos de ClaimRequestDto que la solicitud JSON (consulta OpenAPI). Todas las imágenes se validan antes de crear filas; un archivo inválido cancela el lote completo.

## Límites de imágenes

- Hasta **cinco por expediente**, compartidas entre ambos participantes, incluyendo las enviadas al inicio.
- JPEG o PNG reales: formato detectado a partir del contenido y MIME normalizado, hasta **5 MiB por archivo**, 12 megapíxeles y 6000 píxeles por lado.
- Petición multipart completa de hasta 26 MiB para el lote inicial.
- Se decodifican y vuelven a codificar sin metadatos; se conserva la proporción, sin recortar como avatar. No se guardan nombres originales.
- Almacenamiento privado en PostgreSQL, coherente con el almacenamiento local actual. No se incorpora un proveedor externo ni cuotas de nube.
- PDF, GIF, SVG y URL externa no son pruebas válidas en este módulo. Las imágenes públicas de un reporte siguen su contrato independiente.

## Resguardo y edición

`WITH_FINDER`: con quien lo encontró. `WITH_TEACHER`: requiere nombre de referencia, ubicación opcional. `OFFICE`: ubicación opcional, con valor predeterminado de oficina de profesores del edificio I. La marcación guarda actor y fecha sin transferir `responsibleId`. El reportante o responsable autorizado pueden registrar resguardo hasta la entrega; una vez OFFICE, solo se puede precisar su ubicación, sin volver a otra modalidad. El POST anterior `/office` sigue siendo compatible.

La ficha textual del hallazgo y del extravío puede corregirse con reclamos activos; antes/después se registran en sus expedientes activos. El retiro y los cambios de fotos públicas siguen protegidos durante el trámite. RETURNED/RECOVERED son finales.

## Historial y migración

Flyway V10 añade `claim_activity`, `claim_evidence` y los campos del resguardo manual. No cambia IDs, dueños, responsables, estados, credenciales ni relaciones existentes. Registra solicitud, decisiones, evidencias, comentarios, cambios de resguardo y correcciones de ficha. El nombre del actor se conserva como era al aportar.

En reclamos previos se importan la creación y las decisiones que tienen fecha conocida en auditoría. La ficha disponible al migrar se etiqueta como **expediente previo**, con la aclaración de que no representa la ficha al inicio del reclamo. No se inventan datos históricos. Las marcas de oficina anteriores se conservan y se trasladan a los campos genéricos nuevos.

## Interfaz

La solicitud permite elegir, previsualizar y quitar imágenes antes de enviarlas. Al guardarla abre el expediente, que reúne objeto/resguardo, descripción privada, galería de pruebas, comentarios, eventos y siguiente acción. El botón Actualizar expediente trae aportaciones nuevas; no se implementa chat en tiempo real. En móvil y escritorio, las acciones siguen los permisos de cada participante. Al cerrar desaparecen el editor y la carga; la galería y el historial siguen disponibles.

## Validación

`mvn verify` contra PostgreSQL 18 temporal: **96 pruebas, cero fallos**. Incluye privacidad entre tres cuentas, CSRF, validación de archivos, lote atómico de más de 6 MiB, permisos después de delegación antigua, paginación, cuota, idempotencia, concurrencia entre aportaciones/cierre y actualización V9→V10 conservando los registros.

Se comprobó además en Chrome un recorrido entre tres cuentas: publicación con docente manual, solicitud con imágenes, comentarios, reintento de red sin duplicación, aceptación, oficina irreversible, edición activa, entrega y cierre de extravío. También se verificaron rechazo con motivo, cancelación, paginación del historial, tema oscuro y anchos de 320, 390, 635, 820 y 1440 píxeles sin desbordamiento horizontal.

Actualización: el grupo 2 implementa estación y contadores internos, y el grupo 4 integra el aviso de posible hallazgo y el correo por hitos. Las suscripciones Web Push y recordatorios del grupo 3 siguen pendientes.


### Recorrido manual con dos cuentas

1. Cuenta A: Reportar → Encontré algo. Publicar con foto y elegir Lo tengo yo o un docente por nombre.
2. Cuenta B: abrir ese hallazgo, describir un detalle privado, adjuntar imágenes opcionales y enviar. Se abre el expediente.
3. Alternar las dos cuentas: aportar un comentario, actualizar el expediente en la otra cuenta y comprobar que aparecen autor y fecha. Adjuntar otra imagen durante la revisión.
4. Cuenta A: aceptar y cambiar el resguardo a oficina. Verificar que continúa como responsable y que ya no se ofrece volver a Lo tengo yo.
5. Cuenta B: después de la entrega física, pulsar Ya recibí mi objeto. Volver al expediente con ambas cuentas: conserva pruebas e historial y ya no permite aportar.
6. Con un hallazgo nuevo, probar rechazo con motivo y cancelación; en ambos casos también se conserva el expediente.
7. Una tercera cuenta no debe abrir ese expediente ni sus imágenes, aunque copie sus enlaces.
