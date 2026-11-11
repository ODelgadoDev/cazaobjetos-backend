# Módulo 1: catálogo, búsqueda y filtros

Implementado en GET `/api/v1/found-items` y GET `/api/v1/lost-reports`. Requieren sesión. Al ser GET no necesitan token CSRF. Ambos devuelven ahora un objeto paginado; ya no devuelven un array directo.

## Parámetros

| Parámetro | Significado | Valor por defecto / límites |
|---|---|---|
| q | Subcadena en título, descripción pública o lugar | Vacío; máximo 200 caracteres |
| category | ELECTRONICS, DOCUMENTS, KEYS, CLOTHING, OTHER | Todas |
| location | Subcadena en lugar del hallazgo/extravío | Todos; máximo 200 caracteres |
| status | Hallazgos: AVAILABLE, RETURNED. Extravíos: ACTIVE, RECOVERED | Todos |
| custody | Solo hallazgos: WITH_FINDER, OFFICE | Ambos |
| dateFrom | Fecha mínima del hallazgo/extravío, inclusiva | Sin mínimo; YYYY-MM-DD |
| dateTo | Fecha máxima del hallazgo/extravío, inclusiva | Sin máximo; YYYY-MM-DD |
| sort | RECENT, OLDEST, TITLE_ASC, TITLE_DESC | RECENT |
| page | Número de página, empezando por 0 | 0; máximo 10000 |
| pageSize | Registros por página | 20; entre 1 y 100 |

Los filtros se combinan con AND. `q` busca en cualquiera de los tres campos públicos (OR). La búsqueda ignora mayúsculas/minúsculas y espacios en los extremos, pero conserva acentos; no es búsqueda difusa. `%`, `_` y `!` se buscan literalmente, no como comodines. No busca en pruebas privadas, correos, matrículas ni nombres de usuarios.

RECENT y OLDEST ordenan por la fecha del hallazgo/extravío, no por la fecha de publicación. TITLE_ASC/DESC ordenan por título ignorando mayúsculas. Los empates se resuelven por UUID ascendente para mantener orden estable con un conjunto de datos fijo. Los resultados y el conteo se obtienen dentro de una transacción de lectura REPEATABLE_READ; si entre dos peticiones otros usuarios publican o cierran reportes, las páginas pueden cambiar.

`dateFrom > dateTo`, estados/categorías/órdenes desconocidos, fechas inválidas, valores numéricos inválidos y límites excedidos devuelven 400. Enviar custody al catálogo de extravíos devuelve 400, porque no aplica. Una página válida fuera del último resultado devuelve `items: []` conservando el total.

## Ejemplo

```http
GET /api/v1/found-items?q=mochila&category=CLOTHING&location=Edificio&status=AVAILABLE&custody=OFFICE&sort=RECENT&page=0&pageSize=5
```

Estructura de respuesta (items contiene los DTOs existentes, sin cambiar sus campos):

```json
{
  "items": [],
  "total": 0,
  "page": 0,
  "pageSize": 5,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

`total` es el total después de aplicar todos los filtros, antes de paginar. Si se omite status se incluyen reportes cerrados. La pantalla Explorar debe enviar `status=AVAILABLE` para mostrar solo hallazgos disponibles; Mis extravíos activos debe enviar `status=ACTIVE`.

## Adaptación del frontend

- GET /hallazgos → /api/v1/found-items.
- `categoria` → `category`, traduciendo etiquetas a enums (Electrónica → ELECTRONICS; Documentos → DOCUMENTS; Llaves → KEYS; Ropa/Mochilas → CLOTHING; Otro → OTHER).
- `orden=recientes` → `sort=RECENT`; antiguos → OLDEST.
- Página visual 1 → `page=0` (`page - 1`). `pageSize` se conserva.
- Leer `response.items` y `response.total`; el total nunca es la longitud de una página.
- `zona` no es un catálogo estructurado en la base actual. `location` busca texto en el lugar; no debe presentarse como equivalencia exacta a zonas. Un catálogo de ubicaciones puede añadirse después.
- Los cambios de filtro deben reiniciar page a 0.
- Se mantienen UUID como IDs y los nombres camelCase de cada objeto.

Cambio de contrato: las respuestas de GET found-items/lost-reports pasan de array a PageResponseDto. GET claims sigue siendo array. Se actualizó la colección anterior de Postman para leer `items` al comprobar un extravío. No se modificó el repositorio del frontend.

## Arquitectura

Controller → CatalogService → CatalogDataService → JpaRepository + JpaSpecificationExecutor.

Las combinaciones de filtros se expresan con Criteria/Specification y parámetros, sin construir SQL con texto del usuario. La paginación y el conteo ocurren en PostgreSQL, no cargando todas las filas en Java. No cambia el esquema de la base. La búsqueda con subcadena puede requerir índices especializados si el volumen crece; para este MVP no se agregó una extensión de PostgreSQL.

## Pruebas en Postman

Importa `postman/Catalogo.postman_collection.json`. Usa el entorno existente **CazaObjetos · Local**, con `devPassword` configurado. Run collection ejecuta 23 peticiones: crea dos hallazgos y un extravío ficticios con una etiqueta única, verifica filtros, paginación, orden y errores de validación. No elimina datos.

La colección original `CazaObjetos.postman_collection.json` también se actualizó: impórtala de nuevo/reemplázala antes de volver a ejecutar el flujo completo.

## Verificación del 9 de noviembre de 2026

`mvn verify`: 32 pruebas, sin fallos. PostgreSQL 18 temporal independiente de los datos locales. Newman: catálogo 23 peticiones/33 assertions; flujo original 40 peticiones/47 assertions, sin fallos. Incluye filtros combinados, extremos de fechas, caracteres literales, empates de orden, páginas vacías, validación y acceso sin sesión.

Desde el módulo 2, los catálogos generales excluyen WITHDRAWN. Las rutas `/mine` permiten consultar las publicaciones propias, incluidas las retiradas; consulta [gestión de publicaciones](publications.md).
