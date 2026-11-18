# CazaObjetos UTCH — backend

Java 21 · Spring Boot 4.1.2 · Spring Data JPA · PostgreSQL 18 · Maven · Flyway.

Primera implementación del MVP para el edificio I de Tecnologías. Las publicaciones aparecen inmediatamente para usuarios autenticados. El catálogo incluye hallazgos y extravíos. No contiene frontend ni es todavía un despliegue listo para usuarios reales.

## Launcher y Postman

```bash
cd ~/Documentos/cazaobjetos-backend
[ -f .env ] || cp .env.example .env
chmod 600 .env
./run.sh
```

Configura `.env` con tus claves existentes si ya inicializaste la base. El launcher inicia PostgreSQL y Mailpit con Docker Compose y ejecuta Java con perfil dev. Usa `./run.sh --no-db` para PostgreSQL existente y `./run.sh --build` para forzar compilación.

Importa `CazaObjetos.postman_collection.json` y `Local.postman_environment.json` de `postman/` y sigue la [guía de pruebas Postman](postman/README.md). Configura `devPassword` en el entorno importado. Verificado el 5 de noviembre de 2026: 40 peticiones y 47 assertions pasaron con Newman contra una base temporal. Se comprobó el arranque real con `--no-db`; el arranque Docker no se ejecutó en esa verificación.

## Ejecutar localmente

Requisitos: Java 21, Maven 3.6.3+ y PostgreSQL (o Docker Compose).

```bash
cd ~/Documentos/cazaobjetos-backend
export DB_PASSWORD='elige-una-clave-local'
export DEV_PASSWORD='elige-otra-clave-de-12-caracteres'
docker compose up -d
LOCAL_MAIL_ENABLED=true SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

La API escucha en `http://127.0.0.1:8080`. Para PostgreSQL existente, configura `DB_URL`, `DB_USER` y `DB_PASSWORD`. Flyway crea el esquema al iniciar. `DEV_PASSWORD` solo es necesario con el perfil `dev`.

El perfil `dev` crea cinco cuentas ficticias con esa contraseña:

- `alumno.demo@utch.edu.mx`
- `reclamante.demo@utch.edu.mx`
- `tercero.demo@utch.edu.mx`
- `docente.demo@utch.edu.mx`
- `secretaria.demo@utch.edu.mx`

No se envían mensajes a esas direcciones. Las cuentas existentes no cambian de contraseña al reiniciar. Nunca activar `dev` en un despliegue real. Sin ese perfil no se crean cuentas automáticamente, y la cookie requiere HTTPS.

## Autenticación para integrar el frontend

Sesión en cookie HttpOnly y protección CSRF habilitada. Servir frontend/API bajo el mismo origen; no hay CORS abierto. No se aceptan IDs de usuario enviados por el cliente como identidad.

1. `GET /api/v1/auth/csrf`, conservar cookies y leer `headerName` y `token`.
2. `POST /api/v1/auth/login`, formulario URL-encoded con `username` (correo) y `password`; enviar el token en el header indicado.
3. Si devuelve 204, volver a solicitar `/auth/csrf`: el inicio de sesión renueva el token.
4. Enviar cookie y token CSRF en cada POST. `GET /auth/me` devuelve la identidad y rol.
5. `POST /auth/logout` cierra la sesión; requiere CSRF. Los tokens o contraseñas no van en URLs.

Registro y verificación implementados: POST `/auth/register`, `/auth/verify-email` y `/auth/resend-verification`. La cuenta se habilita tras verificar un código de un solo uso. Hay límite local de 30 intentos por IP/minuto. El correo se restringe al dominio exacto `utch.edu.mx`; no se impone el prefijo `a`. El envío se prueba con Mailpit local; la recuperación de contraseña está implementada y el SMTP institucional requiere configuración externa. Consulta [cuentas y verificación](docs/accounts.md).

## API v1

Los catálogos de hallazgos y extravíos devuelven `{items, total, page, pageSize, totalPages, hasNext, hasPrevious}`. Admiten búsqueda, filtros y ordenamiento; `page` comienza en 0 y `pageSize` es configurable (20 por defecto, máximo 100). Consulta [el contrato del catálogo](docs/catalog.md). El listado de reclamos conserva su respuesta de array. Los IDs son UUID. DTOs de entrada y salida en camelCase. Las rutas se conservan; respecto al primer corte, `responsible_id` pasa a `responsibleId`, `found_location` a `foundLocation`, y así sucesivamente. Los errores de negocio usan `{ "status": 409, "message": "..." }`. Los datos privados del reclamo solo se entregan a sus participantes.

| Método | Ruta bajo `/api/v1` | Operación |
|---|---|---|
| GET / POST | `/found-items` | Listar / publicar hallazgos |
| GET | `/found-items/{id}` | Detalle de hallazgo |
| POST | `/found-items/{id}/office` | Reportante o responsable registra que lo dejó en oficina |
| PUT | `/found-items/{id}/custody` | Resguardo manual: conmigo, docente u oficina |
| POST | `/found-items/{id}/delegate` | Compatibilidad con delegaciones anteriores |
| POST | `/found-items/{id}/claims` | Crear reclamo |
| GET | `/claims` | Reclamos propios o gestionados por el usuario; detalles privados y correos de contacto |
| POST | `/claims/{id}/accept` | Responsable acepta |
| POST | `/claims/{id}/reject` | Responsable rechaza pendiente |
| POST | `/claims/{id}/cancel` | Solicitante cancela pendiente o aceptado |
| POST | `/claims/{id}/complete` | Solicitante aceptado confirma recuperación |
| GET / POST | `/lost-reports` | Listar / publicar extravíos |
| POST | `/lost-reports/{id}/recover` | Dueño cierra extravío recuperado fuera de la app |

Publicar hallazgo o extravío:

```json
{
  "title": "Mochila negra",
  "description": "Mochila negra con cierre gris",
  "category": "OTHER",
  "location": "Edificio I, salón 3",
  "occurredOn": "2026-10-30"
}
```

Categorías: `ELECTRONICS`, `DOCUMENTS`, `KEYS`, `CLOTHING`, `OTHER`. Fecha no futura; no incluir detalles secretos de propiedad en la descripción pública.

Reclamo:

```json
{"privateDescription": "Dentro tiene un cuaderno con mi nombre", "lostReportId": null}
```

La API de delegación anterior y el directorio `/responsibles` se conservan por compatibilidad. El nuevo formulario usa resguardo manual con docente por nombre y ubicación; no transfiere la gestión ni necesita una cuenta del docente.

## Reglas implementadas

- Publicar no implica dejar el objeto en la mesa.
- Marcar oficina registra actor/fecha; no requiere confirmación de la secretaria ni transfiere responsabilidad.
- Un rol docente o secretaría no concede acceso global. Se conserva la gestión de delegaciones anteriores; registrar resguardo manual con docente no asigna revisión ni genera avisos a ese docente.
- Un reclamo activo por usuario/hallazgo y un único reclamo aceptado o completado por hallazgo, respaldados por índices únicos.
- Todas las decisiones bloquean primero el hallazgo dentro de una transacción. Dos aceptaciones concurrentes no pueden ganar.
- Solo el solicitante aceptado confirma recuperación. Confirmar es una declaración del receptor, no prueba independiente de entrega física.
- Al recuperar, se completa el reclamo, se cierra el hallazgo y los demás reclamos pendientes; se cierra también el extravío vinculado y se inserta un aviso en outbox, todo atómicamente.
- Repetir oficina, aceptación vigente o recuperación completada no duplica esos efectos.
- Cancelar un reclamo aceptado libera la reserva.
- Un extravío con reclamo activo vinculado se cierra a través de ese reclamo; para recuperación externa se debe cancelar primero.
- La coordinación se realiza en los comentarios privados del expediente, con evidencias e historial. El correo se limita a publicaciones e inicio/finalización del reclamo, además de la seguridad de cuenta.

## Pruebas

Las pruebas de integración requieren una base **desechable exclusiva**: vacían las tablas del esquema. Usan `TEST_DB_URL` obligatorio, separado de `DB_URL`; no se ejecutan contra la base de desarrollo por defecto.

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5432/cazaobjetos_test \
TEST_DB_USER=caza_test TEST_DB_PASSWORD='clave-de-pruebas' mvn test
mvn -DskipTests package
```

El usuario de pruebas debe poder crear tablas. Las migraciones se ejecutan automáticamente. `scripts/smoke.py` verifica además autenticación, CSRF y flujo de recuperación por HTTP sobre una instancia con perfil dev. Solo crea registros ficticios; no borra datos:

```bash
DEV_PASSWORD='la-clave-del-perfil-dev' python3 scripts/smoke.py
```

## Pendiente para el despliegue y la prueba de teléfono

- Configurar y verificar la entrega de correo mediante SMTP externo.
- Publicar frontend/API con HTTPS accesible desde el teléfono y comprobar instalación, permiso y recepción real en Android/iPhone.
- Operación de producción: respaldos privados de base y claves VAPID, conservación de imágenes/historial y límites distribuidos si se utilizan varias instancias.

Fotos, catálogo, edición/retiro, perfil, reclamos, OpenAPI, idempotencia, integración local y notificaciones ya tienen implementación y documentación en los apartados siguientes.

## Organización por capas

Se adapta la estructura de los repositorios de Alfonso Barroso: [order-service](https://github.com/alfonsojbarroso/order-service) y [billing-service](https://github.com/alfonsojbarroso/billing-service).

```text
src/main/java/mx/utch/cazaobjetos/
├── Application.java
├── config/                # Seguridad y cuentas de desarrollo
├── constants/             # Constantes compartidas
├── controller/            # Controladores REST por recurso
├── dto/                   # Records de entrada/salida
├── enums/                 # Estados, categorías y roles
├── exception/handler/     # BusinessException y traducción HTTP
├── model/                 # Entidades JPA
├── repository/            # Interfaces JpaRepository
├── service/               # Orquestación, datos y eventos
└── util/                  # Validación común sin estado mutable
```

`ItemService` organiza el caso de uso y la transacción; `ItemDataService` accede a repositorios; `ItemEventService` registra auditoría y avisos dentro de esa misma transacción. Los controladores usan inyección por constructor y respuestas tipadas. `application.yaml` configura las rutas y JPA con `ddl-auto: validate`; Flyway mantiene el control del esquema.

Detalles y diferencias deliberadas: [patrones de referencia](docs/reference-patterns.md).

## Referencias técnicas consultadas

- https://docs.spring.io/spring-boot/system-requirements.html
- https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/basic.html (se evaluó Basic; esta implementación usa sesión y formulario con CSRF).

PostgreSQL de Docker usa el puerto local `5433` para evitar conflictos con PostgreSQL instalado en el sistema. `DB_PORT` y el puerto en `DB_URL` deben coincidir. La API sigue en `8080`.

## Registro y correo local

Al ejecutar `./run.sh`, abre el buzón de pruebas en http://localhost:8025. Para probar cuentas nuevas importa `postman/Registro.postman_collection.json` y `postman/Registro-local.postman_environment.json`. Selecciona el entorno Registro local y usa Run collection. La guía completa está en [docs/accounts.md](docs/accounts.md). Los mensajes se capturan localmente y no se envían a cuentas institucionales reales.

## Gestión de publicaciones (módulo 2)

Disponibles edición con PUT, retiro con POST `/{id}/withdraw` y listados personales `/found-items/mine` y `/lost-reports/mine`. El retiro conserva el historial como WITHDRAWN y lo oculta del catálogo general. Reclamos activos permiten corregir la ficha y cambiar el resguardo, dejando constancia en el expediente; bloquean el retiro y los cambios de fotos públicas. [Reglas, permisos y contrato](docs/publications.md). Importa `postman/Publicaciones.postman_collection.json` para probarlo.

## Fotografías (módulo 3)

JPEG/PNG: hasta tres fotos de 5 MiB por publicación. Carga multipart, consulta autenticada y eliminación con los permisos de edición. Las imágenes se validan, se vuelven a codificar sin metadatos y se almacenan en PostgreSQL. [API y límites](docs/photos.md). Pruebas manuales: `postman/Fotos.postman_collection.json`.

## Directorio y reclamos (módulo 4)

`GET /responsibles` ofrece docentes/secretaria habilitados para delegar. `GET /claims/search` agrega filtros, paginación y total; `GET /claims/{id}` reúne el reclamo, hallazgo y contactos autorizados. El listado original de reclamos conserva su contrato. [Guía y permisos](docs/claims-directory.md). Colección: `postman/Reclamos.postman_collection.json`.

## Cuentas y acceso (módulo 5)

Recuperación y cambio de contraseña, revocación de sesiones, asignación local de roles y perfil SMTP externo configurable. Las pruebas usan Mailpit; la entrega institucional real requiere credenciales y validación con el proveedor. [Guía y endpoints](docs/passwords-and-roles.md). Colección `Cuentas.postman_collection.json` con entorno `Cuentas-local.postman_environment.json`.

## Integración y despliegue (módulo 6)

OpenAPI en `/api/v1/openapi.json`, salud en `/api/v1/health`, CORS por orígenes explícitos e Idempotency-Key para crear publicaciones y reclamos. Incluye Dockerfile y Compose de producción separado. [Guía de integración y despliegue](docs/integration.md). No se publicó el sistema en Internet.

## Base del perfil

La migración V8 prepara alias e información académica opcional sin cambiar la identidad de las cuentas existentes. `GET /api/v1/auth/me` conserva su contrato y añade esos campos privados; su edición se implementará en los siguientes módulos. [Diagnóstico, estructura y alcance del módulo 1](docs/profile-foundation.md).

## Perfil completo

Nombre, alias, datos académicos y fotografía se pueden modificar desde Mi cuenta. La matrícula se muestra cuando coincide el formato admitido del correo del alumno. La migración V9 almacena un avatar por cuenta y conserva el resto de datos. [Contrato y funcionamiento del perfil](docs/profile-editing.md).

## Expediente de reclamo (etapa 2, grupo 1)

Comentarios privados, hasta cinco evidencias JPG/PNG de 5 MiB, solicitud con imágenes atómica y bitácora paginada. El solicitante y el responsable actual acceden al expediente; al rechazar, cancelar o completar queda en lectura. Resguardo con docente es una referencia manual sin cuenta ni transferencia de gestión. El resguardo en oficina no se revierte. Se preservan los registros y responsables anteriores mediante Flyway V10. [API, reglas y pruebas](docs/claim-dossier.md). La estación interna y los avisos opcionales del dispositivo están implementados en los grupos siguientes.

## Notificaciones internas (etapa 2, grupo 2)

Estación personal en `/notificaciones`, campana e indicador de pendientes en Reclamos. Las publicaciones y los eventos del expediente crean avisos transaccionales sin duplicados; los comentarios y pruebas nunca se copian al texto del aviso. Abrir la estación o el expediente no marca avisos como leídos: pulsar uno marca únicamente ese aviso y abre su destino. Limpiar retira solo leídos, sin borrar historial. Flyway V11 presenta los reclamos anteriores aún activos una sola vez. [API, destinatarios y pruebas](docs/notifications.md). El grupo 3 agrega los avisos nativos y recordatorios; [activación](docs/web-push.md).

## Hallazgo desde un extravío y correo por hitos (grupo 4)

Yo lo encontré conserva el reporte de origen y publica ficha, foto obligatoria y resguardo juntos con `POST /found-items/publish`. Al terminar se avisa al dueño como **posible coincidencia**, sin iniciar ni cerrar un reclamo automáticamente. El dueño consulta los posibles hallazgos desde su reporte y puede reclamar con su extravío preseleccionado. Los comentarios y acuerdos permanecen en la app; los correos se generan solo al publicar, iniciar o finalizar un reclamo, además de verificación/recuperación de cuenta. `FRONTEND_URL` configura sus enlaces; el perfil dev usa `http://localhost:5173` por defecto. [Contrato](docs/found-matches.md) y [validación](docs/validation-group4.md). Web Push real con la app cerrada ya fue verificado en Chrome; queda el teléfono físico con HTTPS.

## Avisos del dispositivo y recordatorios (grupo 3)

Desde la campana, abre **Avisos en este dispositivo** y pulsa **Activar notificaciones**. El permiso es voluntario. Web Push avisa aunque no haya ventanas de la app abiertas, con resumen genérico y apertura del aviso autorizado; mostrarlo no marca pendientes como leídos. Cerrar sesión silencia y desvincula ese dispositivo. Los recordatorios se configuran en 0, 1 o 2 al día, a las 10:00/18:00 de Chihuahua, solo si existe trabajo pendiente. No aumentan el contador de la bandeja.

Flyway V13 conserva los datos y añade suscripciones, preferencias y cola. Las claves VAPID se generan una vez en `.local/` (privado e ignorado por Git); conservarlas al respaldar. Docker de producción incluye volumen persistente para ellas. **154 pruebas Java sin fallos** y recepción cifrada real en Chrome con cero ventanas abiertas. La validación física de Android/iPhone requiere una dirección HTTPS accesible desde el teléfono. [API, configuración y recorrido de pruebas](docs/web-push.md).
