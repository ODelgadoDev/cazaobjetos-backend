# Avisos del dispositivo y recordatorios — grupo 3

Implementado localmente el 18 de noviembre de 2026: módulos 6 y 7. Usa Web Push estándar, VAPID y el service worker de la PWA. No requiere contratar una plataforma adicional; la entrega utiliza el servicio push del navegador y necesita conectividad del servidor y del dispositivo.

## Activación y uso

1. Inicia sesión y abre la campana → **Avisos en este dispositivo**.
2. Pulsa **Activar notificaciones** y acepta el permiso del navegador. El permiso solo se pide al pulsar ese botón; no aparece automáticamente al entrar.
3. Usa **Probar notificación** para poner un envío de prueba en la cola. La aceptación del servidor no garantiza la entrega del sistema.
4. Elige recordatorios desactivados, una vez al día o dos veces al día. La elección se guarda en tu cuenta; activar o desactivar el dispositivo es independiente de esa preferencia.
5. **Desactivar en este dispositivo** silencia el navegador y da de baja su suscripción. Cerrar sesión también elimina la vinculación local, cierra sus avisos visibles y desactiva la suscripción del servidor para esa sesión/dispositivo.

Al instalar e iniciar sesión aparece una invitación discreta si el navegador es compatible y el dispositivo no está activado. Ahora no la oculta durante ese acceso. Rechazar el permiso mantiene disponibles las publicaciones, reclamos y notificaciones internas. Para cambiar un permiso bloqueado se utilizan los ajustes del sitio en el navegador.

Una cuenta que ya autorizó el mismo navegador recupera su vinculación al volver a entrar, aunque haya vencido la sesión. El cambio de cuenta exige una nueva activación para la nueva cuenta; las respuestas tardías de una sesión anterior no deben asociar el navegador a otra identidad.

## Qué recibe el dispositivo

Todos los eventos que crean un aviso interno nuevo pueden generar un push para los dispositivos activos del destinatario, incluida una posible coincidencia desde Yo lo encontré. No se reproducen avisos anteriores al activar ni al migrar.

El envío contiene únicamente una vinculación opaca e identificadores de entrega/tipo/aviso. El service worker presenta texto genérico y fijo: no expone títulos de objetos, correos, comentarios, descripciones privadas ni imágenes en la pantalla bloqueada. Al pulsar un aviso de evento se abre una ruta del mismo sitio y se marca **solo ese aviso**, después de comprobar sesión y permisos en la API. Si hace falta entrar, el destino permanece en la URL hasta completar el acceso.

Mostrar o descartar un aviso del sistema no lo lee en la estación. Pulsar un recordatorio o una prueba abre la estación sin consumir sus pendientes. El cambio de cuenta y el cierre de sesión invalidan las vinculaciones antiguas; el worker descarta mensajes con una vinculación distinta y nunca sigue URLs recibidas en el payload.

## Recordatorios del servidor

| Preferencia | Horario de referencia, America/Chihuahua |
| --- | --- |
| 0: desactivados | Sin recordatorios; continúan los avisos de novedades. |
| 1: una vez al día, valor inicial | 10:00 |
| 2: dos veces al día | 10:00 y 18:00 |

El trabajador revisa cada minuto. Para tolerar reinicios admite ventanas 10:00–11:59 y 18:00–19:59; fuera de ellas no genera recordatorios atrasados. La entrega no es una alarma de precisión.

Solo se envía un resumen por cuenta, fecha y ventana a sus dispositivos activos, cuando existe al menos un motivo: avisos internos pendientes visibles, un reclamo activo como solicitante/responsable o un extravío activo propio. No afirma que haya aparecido un objeto si no existe una coincidencia. No crea otra notificación interna ni aumenta el contador: así se evita un recordatorio de otro recordatorio. Las ejecuciones y entregas quedan registradas en sus tablas.

Antes de enviar se revisan otra vez la preferencia, el motivo vigente, el permiso de acceso al aviso, la vigencia de credenciales y la vinculación del dispositivo. Bajar de dos recordatorios a uno cancela la segunda ventana aún pendiente; resolver el motivo cancela su envío pendiente. El backend debe permanecer operativo; un temporizador del navegador no interviene.

## API

Prefijo `/api/v1`. Todas las rutas requieren sesión; las escrituras además requieren CSRF. La identidad siempre procede de la sesión.

| Método | Ruta | Solicitud o respuesta |
| --- | --- | --- |
| GET | `/push/config` | `{enabled,applicationServerKey}`; solo clave pública. |
| POST | `/push/subscriptions` | `{expectedUserId,endpoint,keys:{p256dh,auth}}` → `{id,userId,bindingToken}`. `expectedUserId` solo comprueba que la cuenta no cambió mientras se activaba. |
| DELETE | `/push/subscriptions/{id}` | 204; solo su propietario. |
| POST | `/push/test` | `{subscriptionId}` → 202 `{id}`; suscripción activa de la sesión actual, máximo una prueba por minuto. |
| GET | `/push/preferences` | `{dailyReminders,timeZone,firstHour,secondHour}`. |
| PUT | `/push/preferences` | `{dailyReminders:0\|1\|2}` → preferencia guardada. |

Hasta diez dispositivos activos por cuenta. Se validan claves P-256/auth reales y endpoints HTTPS de proveedores admitidos: FCM, Mozilla, Apple y los subdominios push de Windows. No se aceptan destinos arbitrarios, IP privadas, redirecciones ni puertos alternativos. No se registran endpoints, claves ni payloads en los logs de transporte.

## Persistencia y configuración

Flyway **V13** añade `push_subscription`, `push_preference`, `push_delivery` y `push_reminder_run`. Conserva las tablas anteriores, no activa dispositivos existentes ni genera envíos de avisos históricos. La cola se guarda en la misma transacción del aviso interno nuevo y deduplica origen/dispositivo/vinculación.

El despachador procesa lotes de cinco cada cinco segundos. Reintenta errores transitorios hasta cinco intentos, con espera exponencial y Retry-After acotado. La entrega caduca tras una hora; 404/410 desactivan la suscripción. El proveedor puede aceptar un mensaje sin que el sistema llegue a mostrarlo. Los reintentos conservan el identificador/tag; no se promete entrega exactamente una vez a nivel de red.

| Variable | Valor inicial / uso |
| --- | --- |
| `PUSH_ENABLED` | `true`; habilita la función. |
| `PUSH_WORKER_ENABLED` | `true`; permite detener envío y programación para pruebas. |
| `PUSH_KEY_FILE` | `.local/vapid.properties` desde el directorio del backend. |
| `PUSH_SUBJECT` | `https://utch.edu.mx`; contacto VAPID configurable mediante URL HTTPS o mailto del operador. |
| `FRONTEND_URL` | URL de la aplicación; también usada en correos por hitos. |

La pareja VAPID se genera una sola vez y se reutiliza después de reiniciar. En Linux el archivo y su lock tienen permisos 600; el directorio nuevo tiene 700 y `.local/` está ignorado por Git. **No borrar ni regenerar esas claves en cada arranque**: conservarlas con los respaldos privados para que sigan funcionando las suscripciones. La clave privada nunca forma parte del frontend, OpenAPI ni respuestas HTTP.

Docker de producción monta `production_push_keys` en `/app/.local`, con propietario del usuario no root de la API. Conservar ese volumen junto con el respaldo de PostgreSQL. Se verificó la sintaxis de Compose; no se desplegó esta configuración.

## PWA, HTTPS y prueba de teléfono

El mismo service worker generado por VitePWA importa `/push-worker.js`; no se registra un segundo worker competidor. El modo dev también lo habilita para probar localmente. Las llamadas a la API no se guardan en la caché PWA.

En esta computadora, `http://localhost` y `http://127.0.0.1` permiten las pruebas de contexto local seguro. Para abrirlo desde un teléfono hace falta una dirección **HTTPS accesible desde ese teléfono**, con frontend y API configurados de forma coherente. El localhost del celular no apunta a la computadora. En iPhone/iPad hay que agregar la PWA a la pantalla de inicio, abrirla desde su icono y pedir el permiso mediante el botón. Instalar por sí solo no autoriza notificaciones.

Referencias: [MDN: suscripción push y permiso del usuario](https://developer.mozilla.org/en-US/docs/Web/API/PushManager/subscribe), [WebKit: Web Push en aplicaciones de pantalla de inicio](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/), [RFC 8291: cifrado de Web Push](https://www.rfc-editor.org/rfc/rfc8291.html).

## Verificación y límites comprobados

- **154 pruebas Java, sin fallos**, sobre PostgreSQL 18 desechable. Incluyen migración V12→V13, transacciones, permisos/CSRF, cambio de contraseña/sesión/cuenta, cuotas, reintentos, vencimiento, cancelaciones y deduplicación concurrente de recordatorios. Un reloj controlado permite comprobar los horarios sin cambiar la hora del equipo.
- Prueba criptográfica independiente: descifrado AES-GCM del mensaje y validación de firma/audiencia/expiración VAPID.
- **Web Push real recibido en Chrome**, mediante su proveedor, con payload cifrado y cero ventanas de la app abiertas. Se comprobó la reapertura, reconexión de la misma cuenta, persistencia de preferencias y limpieza de avisos/suscripción al salir. No fue una simulación de recepción.
- Rechazo del permiso sin solicitudes automáticas, bandeja interna operativa, apertura que lee un solo aviso y aislamiento al cambiar de cuenta. Revisión responsive a 320, 390, 635, 820 y 1440 px, en claro/oscuro, y compilación de producción PWA.
- **Pendiente físico:** recepción en Android/iPhone, toque del aviso desde la pantalla bloqueada y políticas de ahorro de batería mediante una dirección HTTPS del entorno elegido. Chrome de escritorio no demuestra esos comportamientos en un celular.

Recorrido manual: activar → probar → cerrar ventanas → provocar una novedad desde otra cuenta → abrir el aviso → comprobar que solo ese aviso quedó leído → cambiar frecuencia → cerrar sesión → comprobar que ya no llegan avisos de esa cuenta. Repetir rechazando el permiso: el flujo interno debe seguir funcionando. Para recordatorios, dejar un motivo activo y el backend encendido hasta la siguiente ventana; resolverlo y comprobar que no vuelve a recordarse.
