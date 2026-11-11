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

Registro y verificación implementados: POST `/auth/register`, `/auth/verify-email` y `/auth/resend-verification`. La cuenta se habilita tras verificar un código de un solo uso. Hay límite local de 30 intentos por IP/minuto. El correo se restringe al dominio exacto `utch.edu.mx`; no se impone el prefijo `a`. El envío se prueba con Mailpit local: SMTP institucional y recuperación de contraseña siguen pendientes. Consulta [cuentas y verificación](docs/accounts.md).

## API v1

Los catálogos de hallazgos y extravíos devuelven `{items, total, page, pageSize, totalPages, hasNext, hasPrevious}`. Admiten búsqueda, filtros y ordenamiento; `page` comienza en 0 y `pageSize` es configurable (20 por defecto, máximo 100). Consulta [el contrato del catálogo](docs/catalog.md). El listado de reclamos conserva su respuesta de array. Los IDs son UUID. DTOs de entrada y salida en camelCase. Las rutas se conservan; respecto al primer corte, `responsible_id` pasa a `responsibleId`, `found_location` a `foundLocation`, y así sucesivamente. Los errores de negocio usan `{ "status": 409, "message": "..." }`. Los datos privados del reclamo solo se entregan a sus participantes.

| Método | Ruta bajo `/api/v1` | Operación |
|---|---|---|
| GET / POST | `/found-items` | Listar / publicar hallazgos |
| GET | `/found-items/{id}` | Detalle de hallazgo |
| POST | `/found-items/{id}/office` | Reportante o responsable registra que lo dejó en oficina |
| POST | `/found-items/{id}/delegate` | Responsable delega a docente o secretaria habilitados |
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

Delegación: `{"responsibleId":"UUID-del-docente-o-secretaria"}`. Solo el responsable actual puede delegar; la transferencia es inmediata y auditada. No se permite delegar al reclamante activo. Un directorio de personal elegible y aceptación de delegaciones quedan pendientes de diseño.

## Reglas implementadas

- Publicar no implica dejar el objeto en la mesa.
- Marcar oficina registra actor/fecha; no requiere confirmación de la secretaria ni transfiere responsabilidad.
- La oficina no es un rol administrativo con acceso global. Un docente o secretaria solo decide sobre hallazgos que le fueron delegados.
- Un reclamo activo por usuario/hallazgo y un único reclamo aceptado o completado por hallazgo, respaldados por índices únicos.
- Todas las decisiones bloquean primero el hallazgo dentro de una transacción. Dos aceptaciones concurrentes no pueden ganar.
- Solo el solicitante aceptado confirma recuperación. Confirmar es una declaración del receptor, no prueba independiente de entrega física.
- Al recuperar, se completa el reclamo, se cierra el hallazgo y los demás reclamos pendientes; se cierra también el extravío vinculado y se inserta un aviso en outbox, todo atómicamente.
- Repetir oficina, aceptación vigente o recuperación completada no duplica esos efectos.
- Cancelar un reclamo aceptado libera la reserva.
- Un extravío con reclamo activo vinculado se cierra a través de ese reclamo; para recuperación externa se debe cancelar primero.
- El contacto entre participantes es por correo institucional, accesible a las dos partes desde el reclamo. No hay chat.

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

## Pendiente para completar el MVP

- Restablecimiento de contraseña, administración de roles y límites distribuidos para producción.
- Conexión SMTP institucional. El trabajador de outbox con reintentos ya funciona en el perfil dev contra Mailpit local.
- Fotos y almacenamiento de archivos.
- Búsqueda/filtros, edición/retiro de reportes, directorio para delegar y contrato OpenAPI.
- Idempotencia de publicación/creación ante reintentos de la PWA (las transiciones críticas ya toleran algunos reintentos).
- Integración frontend, despliegue HTTPS, backups y política de conservación de datos.

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

Disponibles edición con PUT, retiro con POST `/{id}/withdraw` y listados personales `/found-items/mine` y `/lost-reports/mine`. El retiro conserva el historial como WITHDRAWN y lo oculta del catálogo general. Reclamos activos bloquean edición y retiro. [Reglas, permisos y contrato](docs/publications.md). Importa `postman/Publicaciones.postman_collection.json` para probarlo.

## Fotografías (módulo 3)

JPEG/PNG: hasta tres fotos de 5 MiB por publicación. Carga multipart, consulta autenticada y eliminación con los permisos de edición. Las imágenes se validan, se vuelven a codificar sin metadatos y se almacenan en PostgreSQL. [API y límites](docs/photos.md). Pruebas manuales: `postman/Fotos.postman_collection.json`.

## Directorio y reclamos (módulo 4)

`GET /responsibles` ofrece docentes/secretaria habilitados para delegar. `GET /claims/search` agrega filtros, paginación y total; `GET /claims/{id}` reúne el reclamo, hallazgo y contactos autorizados. El listado original de reclamos conserva su contrato. [Guía y permisos](docs/claims-directory.md). Colección: `postman/Reclamos.postman_collection.json`.

## Cuentas y acceso (módulo 5)

Recuperación y cambio de contraseña, revocación de sesiones, asignación local de roles y perfil SMTP externo configurable. Las pruebas usan Mailpit; la entrega institucional real requiere credenciales y validación con el proveedor. [Guía y endpoints](docs/passwords-and-roles.md). Colección `Cuentas.postman_collection.json` con entorno `Cuentas-local.postman_environment.json`.

## Integración y despliegue (módulo 6)

OpenAPI en `/api/v1/openapi.json`, salud en `/api/v1/health`, CORS por orígenes explícitos e Idempotency-Key para crear publicaciones y reclamos. Incluye Dockerfile y Compose de producción separado. [Guía de integración y despliegue](docs/integration.md). No se publicó el sistema en Internet.
