# Grupo 4: hallazgos vinculados, integración y correo

Actualizado el 18 de noviembre de 2026. Incluye los módulos 8, 9 y la validación local del 10. Las pruebas reales de push en dispositivo dependen del grupo 3, todavía pendiente.

## Flujo de usuario

1. Una persona abre el extravío activo de otra y pulsa **Reportar que lo encontré**.
2. El formulario conserva el ID del reporte en la URL y precarga título/categoría. La ubicación real, fecha del hallazgo, descripción, resguardo y foto los completa quien encontró el objeto; no se copia la ubicación supuesta del extravío como si fuera un hallazgo confirmado.
3. La foto real JPEG/PNG es obligatoria. La ficha, el archivo, el resguardo, la referencia y los avisos se confirman en una sola transacción. Un archivo inválido o un resguardo incompleto revierte todo.
4. El propietario recibe **Posible hallazgo para tu extravío**, con enlace directo a la ficha. El vínculo no prueba propiedad ni crea o cierra reclamos.
5. En Tu reporte aparece una lista persistente de posibles hallazgos; limpiar una notificación no elimina esa relación. El listado excluye hallazgos retirados y conserva los entregados como historial.
6. Al reclamar ese hallazgo, si el dueño todavía puede vincular su reporte, se preselecciona ese extravío. Puede revisar o cambiar la selección; el backend sigue validando que el reporte elegido le pertenece y está activo.
7. Revisión, pruebas y acuerdos siguen en el expediente privado. El responsable acepta; el solicitante confirma la recepción física. Esa confirmación cierra hallazgo, extravío vinculado y otras solicitudes pendientes.

El docente sigue siendo una referencia de palabra. Registrar su nombre no transfiere gestión ni le asigna avisos o aprobaciones. El resguardo en oficina permanece irreversible hasta la entrega.

## API

Todas las rutas requieren sesión. Las escrituras necesitan CSRF; no se acepta un actor elegido por el cliente.

### Publicación completa

`POST /api/v1/found-items/publish`, `multipart/form-data`:

- Parte **request** con `Content-Type: application/json`:

```json
{
  "report": {
    "title": "Mochila verde",
    "description": "Encontrada después de clase",
    "category": "CLOTHING",
    "location": "Edificio I, salón 17",
    "occurredOn": "2026-11-18"
  },
  "sourceLostReportId": "UUID del extravío original, o null",
  "custody": {
    "custody": "WITH_TEACHER",
    "teacherName": "Nombre de referencia",
    "location": "Salón I-18"
  }
}
```

- Parte **file**: foto real JPEG/PNG, hasta 5 MiB, 12 megapíxeles y 6000 píxeles por lado. Se revalida el contenido y se recodifica sin metadatos.
- Header opcional `Idempotency-Key`, 8..100 caracteres con las reglas anteriores. La clave abarca datos y bytes de la foto. El front reutiliza la misma clave si falla la red.

Devuelve 201 `{id}`. Si el extravío se recuperó antes de confirmar la publicación, devuelve 409 y revierte todo; retirado/inexistente devuelve 404. No se puede publicar un hallazgo vinculado al propio extravío: la cuenta debe cerrarlo como recuperado desde su reporte. La UI ofrece publicar de forma independiente mediante una acción explícita, sin quitar silenciosamente el vínculo.

El servidor bloquea primero el reporte de origen y después el hallazgo nuevo, todavía no visible. Si publicar gana frente al cierre concurrente, el aviso describe una posible coincidencia válida en ese momento; si el cierre gana, no hay publicación ni aviso. El reintento de una publicación ya confirmada devuelve su ID original aunque el reporte se haya cerrado después, sin repetir fotos, correos ni avisos.

El `POST /found-items` JSON anterior se conserva para clientes existentes y publicaciones independientes. La interfaz nueva utiliza la publicación completa. El DTO público de hallazgo agrega `sourceLostReportId` nullable. No permite editar la referencia con el PUT de ficha.

### Posibles hallazgos

`GET /api/v1/lost-reports/{id}/matches?page=0&pageSize=20`

Solo el dueño del reporte puede consultar este listado (ajeno: 404). Devuelve una página de FoundItemResponseDto con los hallazgos cuya referencia coincide exactamente; excluye WITHDRAWN, permite RETURNED. Orden por creación descendente e ID ascendente. Página 0..10000 y tamaño 1..100. No devuelve reclamos, evidencias ni comentarios ajenos.

## Correo por hitos

| Hito | Destinatarios |
| --- | --- |
| Hallazgo o extravío publicado | La persona que publicó. |
| Reclamo iniciado | Solicitante y responsable actual. |
| Rechazo o cancelación | Participantes actuales, como confirmación de finalización. |
| Entrega confirmada | Solicitante y responsable; también el reportante original si una delegación anterior lo separó de la gestión. |
| Cierre de otra solicitud porque el hallazgo fue entregado | Solo su solicitante; sin revelar el reclamo ganador. |

Aceptación, comentarios, imágenes, cambios de resguardo, correcciones de ficha, posible coincidencia y lecturas/limpieza de avisos no generan correos individuales. Los avisos internos sí siguen sus reglas del grupo 2. Se conservan verificación de correo y recuperación de contraseña. No se avisa por cada inicio de sesión.

Los mensajes son resúmenes genéricos con folio y enlace. No contienen descripción privada, pruebas, comentarios ni motivo del rechazo. El reportante que perdió acceso por delegación recibe contexto del hallazgo público y no el folio/enlace del expediente privado.

Las filas de outbox se confirman junto con el evento y se deduplican por hito, entidad y destinatario. Los reintentos de creación o entrega no agregan otra fila. El worker y sus reintentos SMTP existentes se conservan; la entrega final depende del servidor SMTP. `FRONTEND_URL` define el origen para abrir la app. En dev apunta a localhost:5173; en despliegue debe configurarse con la URL real. Sin URL configurada fuera de dev, el mensaje indica entrar a la app sin inventar un dominio.

## Migración y compatibilidad

V12 agrega `found_item.source_lost_report_id` con FK a lost_report e índice; amplía el tipo de aviso con POSSIBLE_MATCH. Los hallazgos anteriores mantienen null: no se infieren vínculos por título, categoría ni proximidad. No se modifica el historial anterior, los estados de lectura/limpieza ni los correos existentes. No se reenvían correos antiguos al actualizar.

Las fotos opcionales de extravíos siguen independientes: si no hay foto, la ficha evita un bloque vacío de imagen. El almacenamiento local en PostgreSQL se conserva. No se incorpora un bucket externo.

La validación ejecutada y sus límites están en [la matriz del grupo 4](validation-group4.md).
