# Validación del grupo 4

Fecha: 18 de noviembre de 2026. Desarrollo local sin subir a GitHub.

## Comprobaciones ejecutadas

- Backend: 129 pruebas Java sobre PostgreSQL 18 temporal, sin fallos. Incluyen los grupos anteriores y los nuevos contratos/estados de publicación y correo.
- Frontend: compilación Vite/PWA correcta; recorrido automatizado en Chrome con tres sesiones aisladas.
- Correo: SMTP real contra un Mailpit temporal independiente. No se envió correo institucional externo ni se mezclaron los mensajes de prueba con el buzón de desarrollo habitual.

| Caso | Resultado esperado | Verificado |
| --- | --- | --- |
| Publicar desde un extravío | Referencia persistente y ubicación real aportada por quien encuentra | Java + Chrome |
| Foto inválida o resguardo incompleto | Ningún hallazgo, foto, clave, correo ni aviso parcial | Java + HTTP + Chrome |
| Fallo de red después de guardar | Reintento con mismo ID, una foto y un aviso de posible coincidencia | Java concurrente + Chrome |
| Extravío cerrado, retirado, inexistente o propio | Rechazo consistente sin falso aviso | Java + HTTP; cerrado también Chrome |
| Cierre concurrente con publicación | Publicación válida antes del cierre, o rechazo completo si el cierre ganó | Java concurrente |
| Vínculo a posible hallazgo | No crea reclamo ni recupera automáticamente el extravío | Java + Chrome |
| Listado del dueño | Solo vínculos exactos, sin retirados ni expedientes ajenos | Java + HTTP + Chrome |
| Reclamar desde el aviso | Extravío original preseleccionado si sigue elegible | Chrome |
| Pruebas y foro | Solo los participantes autorizados, sin correos por aportación | Suite Java + Chrome |
| Entrega | Requiere aceptación previa y confirmación del solicitante | Java + HTTP + Chrome |
| Otra persona también reclama | Su solicitud se cierra sin revelar el reclamo ganador | Java + Chrome + SMTP |
| Historial final | Fotos/comentarios consultables sin nuevas aportaciones; vínculo conservado | Java + Chrome |
| Correos por hitos | Publicaciones, inicios y finales; sin datos privados | Java + SMTP real local |
| Delegación antigua | El reportante separado recibe solo contexto público al finalizar | Java |
| Migración V11→V12 | Filas anteriores preservadas, fuentes null, sin replay de correo/avisos | Java + comprobación al instalar |
| Diseño adaptable | Sin desbordamiento a 320, 390, 635, 820 y 1440 píxeles; claro/oscuro | Chrome |

El recorrido SMTP creó un extravío, un hallazgo vinculado y dos solicitudes; una terminó en entrega y la otra se cerró automáticamente. El buzón recibió **9 mensajes**: 1 confirmación de extravío, 1 de hallazgo, 4 de inicio (dos destinatarios por solicitud) y 3 de finalización. Comentarios, aceptación y cambio a oficina no agregaron mensajes. Se revisó el texto de todos para descartar pruebas y comentarios privados.

## Recorrido manual recomendado

1. B publica un extravío, con foto opcional.
2. A abre el reporte de B y pulsa Reportar que lo encontré. Comprueba título/categoría precargados, escribe dónde lo encontró, adjunta su foto real y elige resguardo.
3. B abre el aviso Posible hallazgo para tu extravío o la lista de su reporte, compara el hallazgo y decide reclamar. El extravío debería aparecer seleccionado.
4. B describe un detalle privado y adjunta una prueba. A revisa el expediente; ambos comentan dentro de la app.
5. A acepta, puede dejarlo en oficina si hace falta y registra el resguardo. B confirma Ya recibí mi objeto únicamente después de la entrega física.
6. Comprueba el cierre del hallazgo/extravío, la lectura del historial y los correos en el buzón local de desarrollo.
7. Repite con rechazo y cancelación; el expediente debe conservarse y aparecer el correo de finalización correspondiente.

## Actualización tras el grupo 3

Web Push y recordatorios implementados. La suite conjunta tiene **154 pruebas Java sin fallos**. Se recibió un push real cifrado en Chrome con cero ventanas de la app abiertas y se comprobaron reapertura, reconexión, preferencias, permiso rechazado y cierre de sesión/cambio de cuenta. Los horarios y reintentos del servidor se verificaron con reloj controlado. [Contrato y validación del grupo 3](web-push.md).

Quedan la recepción física en un celular (incluida pantalla bloqueada), la instalación/permisos de Android/iPhone y la dirección HTTPS accesible desde ese dispositivo. La entrega institucional de correo también requiere configurar y verificar el SMTP externo: Mailpit demuestra generación y transporte local.

El módulo 10 queda validado para el flujo local y Web Push real de escritorio; la comprobación física de teléfono permanece pendiente. No se representa una simulación como prueba de celular.
