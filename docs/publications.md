# Módulo 2: gestión de publicaciones

Todas las rutas llevan `/api/v1`, requieren sesión y las escrituras requieren el header CSRF. Se conserva la arquitectura Controller → Service → DataService → Repository.

| Método y ruta | Acción | Permiso |
|---|---|---|
| GET /found-items/mine | Hallazgos publicados por mí o bajo mi responsabilidad | Usuario de la sesión |
| GET /lost-reports/mine | Mis extravíos | Usuario de la sesión |
| GET /lost-reports/{id} | Detalle del extravío | Usuario autenticado |
| PUT /found-items/{id} | Reemplazar los campos editables | Responsable actual |
| PUT /lost-reports/{id} | Reemplazar los campos editables | Dueño |
| POST /found-items/{id}/withdraw | Retirar hallazgo | Responsable actual |
| POST /lost-reports/{id}/withdraw | Retirar extravío | Dueño |

Las rutas `/mine` admiten los mismos filtros, órdenes y paginación del [catálogo](catalog.md). Incluyen retirados por defecto; `status=WITHDRAWN` los selecciona. No aceptan un ID de usuario para cambiar el dueño de la consulta. Si un hallazgo se delega, el reportante lo conserva en su listado, pero solo el responsable actual puede editarlo o retirarlo.

## Edición

PUT exige todos estos campos; no es una actualización parcial:

```json
{
  "title": "Mochila negra",
  "description": "Con cierre gris",
  "category": "CLOTHING",
  "location": "Edificio I, salón 3",
  "occurredOn": "2026-10-01"
}
```

Devuelve 204. Conserva IDs, autor, responsable, estado, resguardo, fecha de creación y reclamos. Aplica las mismas validaciones de publicación (incluida fecha no futura). Edición y retiro generan eventos de auditoría con actor, entidad y fecha; no se almacenan versiones anteriores del texto.

## Retiro e historial

Retirar cambia el estado a `WITHDRAWN`, sin borrar filas ni relaciones. No significa que el dueño haya recuperado el objeto y no envía un aviso de recuperación. Repetir el retiro por el usuario autorizado devuelve 204 sin duplicar el evento. No hay restauración en este módulo.

El catálogo general excluye retirados, incluso con `status=WITHDRAWN`. El detalle de una publicación retirada devuelve 404. Su información permanece en `/mine`. Los reclamos históricos conservan sus reglas de visibilidad.

Solo pueden editarse o retirarse hallazgos AVAILABLE y extravíos ACTIVE. Un reclamo PENDING o ACCEPTED bloquea ambas operaciones con 409, incluso si el extravío está vinculado al reclamo. Primero se debe rechazar o cancelar según los permisos existentes. Tampoco se permite recuperar, delegar, marcar oficina o reclamar publicaciones retiradas. Las publicaciones RETURNED/RECOVERED son finales.

Las escrituras usan el bloqueo de fila existente; la creación de reclamos toma el mismo bloqueo para evitar que una retirada y un nuevo reclamo tengan éxito simultáneamente. Las operaciones sobre extravíos vinculados usan el bloqueo del extravío.

## Base de datos y pruebas

Flyway V3 amplía los estados permitidos y agrega índices para autor/responsable. No elimina publicaciones ni modifica las migraciones anteriores.

Importa `postman/Publicaciones.postman_collection.json`, selecciona **CazaObjetos · Local** y configura `devPassword`. Ejecuta las 26 peticiones en orden con Runner. Crea dos publicaciones ficticias, las edita y las retira; los registros quedan en tu historial. Cada ejecución utiliza una etiqueta única para identificar sus registros.

Frontend: agregar acciones Editar/Retirar según permisos y estado, usar los endpoints `/mine` para la vista personal, traducir WITHDRAWN como “Retirada” y mostrar el mensaje de conflicto 409 si existe un reclamo activo. Conserva cookies y CSRF; no se modificó el frontend en este módulo.

Verificado el 9 de noviembre de 2026: `mvn verify` completó 38 pruebas sin fallos en PostgreSQL temporal, incluyendo permisos, conservación del historial, CSRF, validación HTTP y concurrencia entre retiro y reclamo. Newman completó 26 peticiones/34 assertions de este módulo y 40 peticiones/47 assertions del flujo original, sin fallos.
