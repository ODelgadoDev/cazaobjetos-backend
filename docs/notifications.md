# Notificaciones internas — grupo 2

Implementado localmente el 18 de noviembre de 2026. Corresponde a los módulos 4 y 5 de la segunda etapa. No agrega dependencias ni un proveedor externo.

## Comportamiento

- Cada cuenta tiene su estación en `/notificaciones`, accesible desde la campana de la cabecera, en móvil y escritorio.
- Campana y botón de navegación Reclamos muestran el mismo total de pendientes. El indicador permanece al cambiar de pantalla y no sustituye el resaltado de la ruta actual.
- Consultar la estación, abrir la campana o entrar directamente a un reclamo **no marca** avisos como leídos.
- Pulsar un aviso marca **solo ese aviso**, conserva la fecha de su primera lectura y abre el expediente o publicación correspondiente. Se puede abrir de nuevo uno ya leído.
- Los filtros son Todas y Pendientes. Los avisos leídos permanecen hasta pulsar **Limpiar leídas**.
- Limpiar archiva únicamente leídos, sin consumir pendientes ni eliminar comentarios, pruebas, eventos del expediente o filas de notificación. Es idempotente.
- Se actualiza al escribir en la app, cambiar de pantalla, volver al primer plano y recuperar conexión. Mientras la app está visible consulta novedades cada **30 segundos**. Los avisos y contadores se conservan entre sesiones.
- Las consultas se cancelan y el estado se descarta al cerrar sesión o cambiar de cuenta. Un fallo de red conserva el último contador conocido y permite reintentar; no lo convierte en cero.

## Destinatarios

| Evento | Aviso interno |
| --- | --- |
| Publicar hallazgo o extravío | Confirmación para quien publicó; no se avisa a toda la comunidad. |
| Iniciar reclamo | Confirmación al solicitante y aviso de revisión al responsable actual. |
| Comentario o nueva imagen | Al otro participante; no al autor de su propia aportación. |
| Aceptar o rechazar | Al solicitante. |
| Cancelar | Al responsable actual. |
| Confirmar recepción | A solicitante y responsable. |
| Cierre de otra solicitud por entrega del hallazgo | Al solicitante de esa solicitud; no revela quién recuperó el objeto. |
| Cambiar resguardo o corregir una ficha vinculada | Al otro participante de cada expediente activo afectado. |
| Delegación antigua | A quienes participan con la nueva gestión; los avisos del gestor anterior dejan de ser visibles si perdió acceso. |

Dejar un objeto con docente sigue siendo una referencia manual de palabra. No se busca una cuenta por ese nombre, no se le envían avisos ni se le asigna revisión. Ser docente o secretaría no permite leer avisos de otras cuentas.

El aviso usa un resumen genérico; no incluye el comentario, la descripción privada, el motivo de rechazo ni una imagen. Los detalles permanecen en el expediente autorizado. Las notificaciones no crean nuevas reglas de aceptación ni cierran trámites por sí solas.

## API

Todas las rutas llevan `/api/v1`, requieren sesión y las escrituras usan CSRF. El destinatario procede de la sesión y no de un parámetro del cliente.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/notifications?filter=ALL&page=0&pageSize=20` | Página `{items,total,page,pageSize,totalPages,hasNext,hasPrevious}`. `filter`: ALL o UNREAD. |
| GET | `/notifications/summary` | `{unread,total}` de la bandeja visible de la cuenta. |
| POST | `/notifications/{id}/read` | Aviso con `readAt` persistido. Repetir conserva la primera fecha. |
| POST | `/notifications/clear-read` | `{cleared}` con la cantidad archivada en esta operación. |

Un aviso devuelve `id`, `kind`, `targetType`, `targetId`, `title`, `body`, `createdAt` y `readAt` (null mientras esté pendiente). `targetType` vale CLAIM, FOUND_ITEM o LOST_REPORT. El cliente construye rutas internas con esa enumeración; no ejecuta enlaces arbitrarios enviados en el mensaje.

Página desde 0 hasta 10000 y tamaño 1..100; defecto 20. Orden descendente por fecha e ID. Los filtros y el permiso se aplican antes de contar o paginar. Un UUID ajeno, limpiado o que ya no es visible devuelve 404 al intentar leerlo.

## Persistencia, privacidad y reintentos

Flyway V11 crea `user_notification`, con destinatario, clave del evento de origen, referencia opcional a `claim_activity`, destino, resumen y fechas de creación/lectura/limpieza. No cambia los datos de usuarios, publicaciones, reclamos ni expedientes anteriores.

El aviso se inserta en la misma transacción del evento. Si falla la publicación o aportación, también se revierten sus avisos. La restricción única `(recipient_id,event_key)` y `ON CONFLICT DO NOTHING` deduplican el origen, incluso si el aviso ya se leyó o limpió. Repetir una creación con Idempotency-Key, un comentario o una imagen no genera otra notificación.

El acceso a avisos de tipo CLAIM se comprueba contra los participantes actuales tanto al listar como al contar y marcar lectura. La delegación del flujo anterior puede retirar la visibilidad al responsable previo, sin borrar las filas. El destino vuelve a aplicar las reglas de acceso del expediente.

En la actualización se crea una única introducción pendiente por participante de cada reclamo PENDING/ACCEPTED cuyo hallazgo siga AVAILABLE, etiquetada **Reclamo en curso**. No se reproducen comentarios antiguos ni se inventa que acaba de iniciarse una solicitud. Los reclamos ya finalizados no generan este aviso inicial.

## Verificación

La suite Java comprende 111 pruebas sin fallos sobre PostgreSQL temporal. Incluye aislamiento entre cuentas, autenticación y CSRF, destinatarios de cada estado, ausencia de texto privado, reintentos sin duplicación, primera fecha de lectura estable, archivo de leídos, rollback conjunto, concurrencia entre limpieza y nuevos avisos, filtros, paginación y migración V10→V11 con datos anteriores.

También se verificó en Chrome el recorrido entre tres cuentas, la actualización automática de otra cuenta, lectura individual, limpieza con pendientes, navegación al expediente correcto incluso cerrado, cambio de usuario en el mismo navegador, conservación del contador sin conexión y anchos de 320, 350, 390, 635, 820 y 1440 píxeles sin desbordamiento. Se revisaron los temas claro y oscuro.

## Recorrido manual

1. Cuenta A publica un hallazgo. La campana muestra su confirmación; abrir la estación mantiene el pendiente.
2. Cuenta B inicia un reclamo y aporta un comentario. A recibe avisos y el indicador en Reclamos se mantiene hasta abrirlos individualmente.
3. Pulsar un comentario abre el expediente correspondiente y marca solo ese aviso. Los demás siguen pendientes.
4. Volver a la estación y pulsar Limpiar leídas. Comprobar que los pendientes permanecen y el comentario sigue en el expediente.
5. A acepta; B abre el aviso de aceptación y confirma cuando recibe físicamente el objeto. Ambos reciben confirmación de cierre y pueden consultar el historial.
6. Entrar con una tercera cuenta: no debe ver ni abrir esos avisos. Cambiar de cuenta en el mismo navegador también debe mostrar solo la bandeja correspondiente.

La estación interna funciona independientemente del permiso del sistema. El grupo 3 ya añade Web Push y recordatorios del servidor para dispositivos autorizados; [activación y pruebas](web-push.md). Mostrar o descartar un push no lee sus avisos; pulsarlo abre y lee solo el aviso correspondiente. Los recordatorios abren la estación sin consumir pendientes. Actualización grupo 4: ya se incorpora POSSIBLE_MATCH al publicar desde un extravío, y el correo queda limitado a publicaciones e inicio/finalización de reclamos, además de seguridad de cuenta. [Detalle](found-matches.md).
