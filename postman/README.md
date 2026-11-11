# Pruebas manuales en Postman

## 1. Arrancar el backend

```bash
cd ~/Documentos/cazaobjetos-backend
# Solo la primera vez, si todavía no existe .env:
[ -f .env ] || cp .env.example .env
chmod 600 .env
./run.sh
```

Edita `.env` si tus claves son distintas. La configuración de ejemplo utiliza `CazaObjetos-local-2026` para PostgreSQL y `CazaObjetos-demo-2026` para las cuentas demo. Si ya había una base o cuentas creadas, usa sus claves originales: cambiarlas en `.env` no las cambia en la base. `.env` es configuración Bash local; solo carga contenido de confianza y está excluido de Git.

Espera el mensaje `Started Application`. El launcher usa el perfil dev y Java queda en primer plano. Ctrl+C detiene Java, PostgreSQL continúa activo. `docker compose stop db` detiene la base sin borrar los datos. No hace falta iniciar Java con sudo.

Variantes:

```bash
./run.sh --build   # Fuerza compilación, omitiendo las pruebas de integración
./run.sh --no-db   # Usa PostgreSQL existente según las variables de .env
./run.sh --help
```

La compilación es automática si falta el JAR o hay fuentes más recientes. El modo normal necesita Docker y Compose; `--no-db` necesita una base previamente creada y accesible. Java debe ser 21 o posterior. Si el puerto 8080 está ocupado, detén tu otra instancia o cambia `SERVER_PORT=8081` en `.env` y también la URL en Postman.

## 2. Importar y configurar

En Postman de escritorio, pulsa **Import** y selecciona:

- `postman/CazaObjetos.postman_collection.json`
- `postman/Local.postman_environment.json`

Selecciona el entorno **CazaObjetos · Local**. Configura `devPassword` con el valor de `DEV_PASSWORD` de tu `.env`. `baseUrl` debe ser `http://localhost:8080/api/v1` (sin barra final). Usa siempre el mismo host; localhost y 127.0.0.1 tienen cookies distintas.

Mantén habilitado el cookie jar. No agregues Bearer Token ni Basic Auth: esta API usa cookie de sesión y CSRF. Postman guarda automáticamente la cookie enviada por el servidor.

## 3. Ejecutar manualmente o con Runner

La colección contiene 40 peticiones numeradas. Empieza por la 01 y pulsa **Send** en orden, o ejecuta la colección completa con **Run collection**, una iteración, todas las peticiones seleccionadas. El inicio limpia la sesión anterior mediante logout, usando primero un token CSRF válido.

| Paso | Acción | Resultado esperado |
|---|---|---|
| Login | GET csrf → POST login → GET csrf → GET me | 200 → 204 → 200 → 200 |
| Publicar hallazgo | POST found-items | 201; guarda foundId |
| Dejar en oficina | POST found-items/{{foundId}}/office | 204 |
| Publicar extravío | POST lost-reports como reclamante | 201; guarda lostId |
| Reclamar | POST found-items/{{foundId}}/claims | 201; guarda claimId |
| Aceptar | POST claims/{{claimId}}/accept como reportante | 204 |
| Recuperar | POST claims/{{claimId}}/complete como reclamante | 204 |
| Consultar hallazgo | GET found-items/{{foundId}} | status = RETURNED |

La colección cambia entre `alumno.demo@utch.edu.mx` y `reclamante.demo@utch.edu.mx`, haciendo logout/login en cada cambio. Ambas cuentas usan DEV_PASSWORD cuando se crean. En cada petición revisa **Test Results**: los tests deben pasar, incluso en los casos negativos.

Errores deliberados: 401 sin sesión, 403 sin CSRF o sin permisos, 400 con título vacío y 409 al confirmar recuperación antes de la aceptación. No son fallos de la colección si coincide el código esperado.

Cada ejecución crea un hallazgo, un extravío y un reclamo ficticios nuevos; no borra tus datos. Con el launcher actualizado, los avisos se entregan al buzón Mailpit local en http://localhost:8025. Si desactivas el correo local, quedan pendientes en outbox.

## 4. Construir peticiones por tu cuenta

1. `GET {{baseUrl}}/auth/csrf`. Guarda el campo `token` como `csrfToken` y `headerName` como `csrfHeader`.
2. `POST {{baseUrl}}/auth/login` con Body **x-www-form-urlencoded**:
   - username: alumno.demo@utch.edu.mx
   - password: tu DEV_PASSWORD
   - Header: `{{csrfHeader}}: {{csrfToken}}`.
3. Repite GET csrf después del login, porque el token cambia. Conserva la cookie JSESSIONID.
4. En todos los POST siguientes agrega ese header. Para publicaciones usa Body **raw → JSON**:

```json
{
  "title": "Mochila negra",
  "description": "Mochila negra con cierre gris",
  "category": "OTHER",
  "location": "Edificio I, salón 3",
  "occurredOn": "2026-10-30"
}
```

5. Para reclamar, inicia sesión como el otro usuario y manda a `/found-items/{{foundId}}/claims`:

```json
{
  "privateDescription": "Dentro hay un cuaderno con mi nombre",
  "lostReportId": "{{lostId}}"
}
```

Puedes usar `null` en lostReportId si no quieres vincular un extravío. No envíes ID del actor: se obtiene de la sesión. Para enviar acciones sin cuerpo (office, accept, complete, logout), elige Body **none** y conserva el header CSRF.

## Problemas frecuentes

- Connection refused: la API no está iniciada o estás usando otro puerto.
- Login 401: revisa correo/contraseña; las cuentas existentes conservan su clave original.
- POST 403: solicita otra vez el token y comprueba la sesión; también puede ser falta de permisos.
- 409 al aceptar: ya hay otro reclamo aceptado o el estado ya no permite la operación.
- Docker sin permisos: comprueba `docker info`; puedes usar PostgreSQL local con --no-db.
- Contraseña de base rechazada: utiliza la clave con que se inicializó el volumen. No lo borres para probar.
- La raíz `/` no tiene pantalla: prueba `/api/v1/auth/csrf` o la colección.

Referencias: [variables de Postman](https://learning.postman.com/docs/use/send-requests/variables/variables/) y [cookies](https://learning.postman.com/docs/use/send-requests/response-data/cookies/).

PostgreSQL de Docker usa el puerto local `5433` para evitar conflictos con PostgreSQL instalado en el sistema. `DB_PORT` y el puerto en `DB_URL` deben coincidir. La API sigue en `8080`.

Para registro y verificación de cuentas nuevas, importa la colección Registro y su entorno Registro-local; sigue [la guía de cuentas](../docs/accounts.md).

## Catálogo: búsqueda, filtros y paginación

Importa también `Catalogo.postman_collection.json` y usa el mismo entorno Local. Ejecuta sus 23 peticiones en orden con Runner. Crea dos hallazgos y un extravío de prueba y comprueba filtros, órdenes, páginas y errores 400. Consulta [parámetros y ejemplos](../docs/catalog.md). Reimporta la colección original actualizada: los listados ahora se leen desde `response.items`.

## Gestión de publicaciones

Importa `Publicaciones.postman_collection.json` y utiliza el entorno Local. Sus 26 peticiones prueban edición, listados personales y retiro con historial. Consulta [las reglas y rutas del módulo 2](../docs/publications.md).

## Fotografías

Importa `Fotos.postman_collection.json` y usa el entorno Local. En Body → form-data de cada petición “Subir PNG”, selecciona `fixtures/photo.png` en el campo `file`. Ejecuta las 20 peticiones en orden. Consulta [permisos, límites y API](../docs/photos.md).

## Directorio y reclamos

Importa `Reclamos.postman_collection.json` y usa el entorno Local. Ejecuta sus 49 peticiones en orden para probar el directorio, los filtros y el detalle junto con el flujo completo de entrega. Consulta [la guía del módulo 4](../docs/claims-directory.md).

## Recuperación y cambio de contraseña

Importa `Cuentas.postman_collection.json` y `Cuentas-local.postman_environment.json`; usa el entorno Cuentas local y Runner. El flujo crea una cuenta ficticia propia y usa Mailpit. No modifica contraseñas de tus cuentas demo. [Guía de acceso](../docs/passwords-and-roles.md).

## Integración y reintentos

Importa `Integracion.postman_collection.json` y usa Local. Sus 45 peticiones incluyen reintentos con el mismo ID y el flujo de entrega. También puedes importar `http://localhost:8080/api/v1/openapi.json` como contrato.
