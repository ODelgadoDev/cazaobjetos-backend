# Segunda etapa: expediente de reclamo, comentarios y notificaciones

Estado: grupos 1, 2, 3 y 4 implementados localmente. Web Push real comprobado en Chrome con la app cerrada; quedan las pruebas físicas de celular con HTTPS accesible.
Fecha de actualización: 18 de noviembre de 2026.

## Reglas del proceso

- El solicitante inicia el reclamo con descripción privada y evidencias opcionales en imágenes.
- Los comentarios forman un pequeño foro privado dentro del reclamo, con autor y fecha. Se permite comentar durante el trámite; al cerrarlo quedan como historial de lectura. No se implementará un chat independiente.
- El usuario que gestiona el hallazgo revisa y acepta el reclamo. El solicitante confirma que recibió físicamente el objeto, después de la aceptación. Cambiar el resguardo no acepta ni cierra el reclamo.
- Resguardo y gestión son conceptos separados: conmigo, con un docente o en oficina. El docente, por ahora, es un acuerdo de palabra; no necesita cuenta especial, no recibe un aviso automático y no se le asignan decisiones del trámite. Se registra el resguardo indicado por el usuario. La oficina tampoco debe crear una dependencia de aprobación de personal para completar el flujo entre usuarios.
- Se conserva la regla acordada de no devolver el resguardo de oficina a «lo tengo yo». La entrega final cierra el trámite y bloquea los cambios correspondientes. Se revisarán las restricciones actuales que bloquean toda edición cuando existe un reclamo activo.

## Notificaciones internas y del dispositivo

- Cada aviso tiene destinatario, evento de origen, fecha y estado pendiente/leído. Los participantes autorizados solo consultan sus propios avisos y expedientes.
- Abrir la campana no marca notificaciones como leídas. Pulsar un aviso concreto marca ese aviso como leído y abre su destino.
- Limpiar retira de la lista los avisos ya leídos; no marca pendientes como leídos ni elimina comentarios, pruebas o historial del expediente.
- Campana y navegación de Reclamos mantienen un indicador mientras existan avisos pendientes. El indicador es adicional al resaltado de la pantalla actual.
- Los avisos se publican en la estación interna y, si el usuario autorizó el dispositivo, se envían también mediante Web Push.
- Después de instalar e iniciar sesión se muestra una invitación con el botón Activar notificaciones. Pulsarlo solicita el permiso del sistema. Rechazarlo no bloquea las funciones de la app ni los avisos internos.
- Cada suscripción push se vincula al usuario autenticado y al dispositivo. El cierre de sesión desactiva la recepción de avisos de esa cuenta en ese dispositivo.
- Mostrar o descartar una notificación del sistema no marca como leídos los avisos internos. Un recordatorio agrupado tampoco debe consumir todos los pendientes.
- El contenido visible en la pantalla bloqueada será un resumen; la información privada y las imágenes se consultan dentro de la app con la sesión correspondiente.

## Recordatorios

- Se programan en el backend, no con temporizadores de la página ni dependiendo de una pestaña abierta.
- Preferencia configurable: desactivados, una vez al día o dos veces al día. Valor inicial: una vez al día, con horarios de referencia 10:00/18:00 y zona America/Chihuahua. Los resúmenes nativos no agregan avisos a la bandeja interna.
- Resumen de notificaciones pendientes, reclamos en curso y reportes de extravío activos que inviten a revisar hallazgos.
- Se agrupan para evitar un recordatorio por cada aviso o publicación. Se deduplican por usuario y ventana de envío; los reintentos no deben duplicar envíos.
- No se afirma que apareció una coincidencia si no existe un hallazgo vinculado. Los recordatorios se retiran cuando el motivo deja de estar vigente.
- El backend debe permanecer operativo para generar y enviar los recordatorios. La entrega final depende del dispositivo, navegador y conectividad.

## «Yo lo encontré» desde un extravío

1. Otra persona abre un reporte de extravío activo y pulsa Yo lo encontré.
2. Se abre el formulario de hallazgo conservando la referencia al reporte original; se pueden precargar datos públicos útiles, como título y categoría.
3. Quien encontró el objeto completa su propia información, fotografía y ubicación real de resguardo.
4. Cuando termina correctamente la publicación, se guarda el vínculo y se emite un aviso al propietario del reporte original, con un enlace directo al hallazgo.
5. El dueño compara la publicación y decide si inicia el reclamo. Se facilita la selección del extravío vinculado al reclamar.
6. Este vínculo representa una posible coincidencia; no crea automáticamente un reclamo ni declara recuperado el objeto.
7. No se notifica por borradores o publicaciones fallidas. Los reintentos de la publicación no duplican el aviso. Si el reporte original se cerró durante el proceso, se aplica una regla consistente de publicación/vinculación sin emitir avisos engañosos.

## Correo

El correo queda para confirmación de publicación de hallazgo, publicación de extravío, inicio de reclamo y finalización de reclamo. El usuario confirmó que «inicio» significa inicio de reclamo, no cada inicio de sesión. Se mantienen los correos de verificación y recuperación de cuenta. Los comentarios y recordatorios no generan correos individuales.

## Módulos de esta etapa

| Número | Módulo | Resultado |
| --- | --- | --- |
| 1 | Expediente, estados e historial | Reglas, participantes, resguardo, eventos y modelo persistente; compatibilidad con los registros existentes. |
| 2 | Evidencias privadas | Imágenes durante la solicitud y la revisión, vistas previas, validación y acceso privado. |
| 3 | Comentarios del reclamo | Foro del expediente con autor/fecha, conservación del historial y cierre de escritura al finalizar. |
| 4 | Motor de notificaciones | Avisos persistentes por usuario, eventos, destinatarios, contadores y envío sin duplicados. |
| 5 | Estación de notificaciones | Campana, pendientes, apertura del destino, limpieza de leídos e indicador en Reclamos. |
| 6 | Web Push y permisos | Service worker, suscripciones, permiso voluntario, recepción y apertura segura desde el dispositivo. |
| 7 | Recordatorios programados | Preferencias, horarios, resúmenes diarios y cancelación/deduplicación según el estado vigente. |
| 8 | Hallazgo vinculado a extravío | Flujo Yo lo encontré con referencia persistente, notificación al dueño y posible reclamo posterior. |
| 9 | Flujo integrado y correo por hitos | Una pantalla para revisión, comentarios, pruebas, aceptación y entrega; textos y acciones coherentes y correos limitados. |
| 10 | Validación completa | Pruebas entre cuentas, permisos, cierre, reintentos, pendientes, cambio de usuario/dispositivo y push real con HTTPS. |

## Grupos de trabajo

| Grupo | Módulos | Estado |
| --- | --- | --- |
| 1. Reclamo dentro de la app | 1, 2 y 3 | Implementado: estados, resguardo manual, expediente, imágenes privadas y comentarios. [Contrato y pruebas](claim-dossier.md). |
| 2. Notificaciones internas | 4 y 5 | Implementado: motor transaccional, campana, estación, limpieza de leídos y contadores. [Contrato y pruebas](notifications.md). |
| 3. Notificaciones del dispositivo | 6 y 7 | Implementado: permiso voluntario, suscripciones persistentes, envío cifrado, cierre de sesión y preferencias 0/1/2 con recordatorios del servidor. [Contrato y pruebas](web-push.md). |
| 4. Vinculación y cierre de integración | 8, 9 y 10 | Implementados Yo lo encontré y correo por hitos; validación local entre cuentas y SMTP completada. [Contrato](found-matches.md) y [matriz de pruebas](validation-group4.md). La recepción push real en Chrome y el cambio de cuenta están verificados; queda la prueba física en celular con HTTPS. |

## Requisitos de Web Push y pruebas

La PWA usa un único service worker para caché y recepción push. El grupo 3 incorpora permisos voluntarios, vinculación segura por cuenta/dispositivo y recordatorios persistentes del servidor. Se ejecutaron 154 pruebas Java y se recibió un Web Push real en Chrome con todas las ventanas de la app cerradas; se verificaron reapertura, reconexión y cierre de sesión. No se declara validación en un celular físico. [Detalle](web-push.md).

Para verificar notificaciones reales en un celular hará falta una dirección HTTPS accesible desde ese dispositivo y un backend operativo. En iOS/iPadOS la PWA debe estar agregada a la pantalla de inicio y el permiso se pide desde una interacción del usuario. Las pruebas simuladas no sustituyen la comprobación en un dispositivo real.

Referencias técnicas:

- [WebKit: Web Push en apps de la pantalla de inicio](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/).
- [MDN: Push API](https://developer.mozilla.org/en-US/docs/Web/API/Push_API).
- [MDN: permiso y presentación de notificaciones](https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API).
- [MDN: limitaciones de periodicidad en segundo plano](https://developer.mozilla.org/en-US/docs/Web/API/Web_Periodic_Background_Synchronization_API).

Todos los cambios de desarrollo permanecen locales. No subir a GitHub hasta indicación explícita del usuario.
