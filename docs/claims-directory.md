# Módulo 4: directorio y consultas de reclamos

Todas las rutas requieren sesión. **Actualización etapa 2:** el directorio y la delegación se conservan para compatibilidad con registros anteriores; la interfaz nueva registra al docente como resguardo manual, sin asignarle la revisión. [Expediente y comentarios](claim-dossier.md). El directorio sirve para seleccionar responsables al delegar; no crea cuentas ni concede roles. La secretaria del director se representa con SECRETARY. Un alumno no puede registrarse con ese rol por su cuenta.

## Directorio

`GET /api/v1/responsibles?q=docente&role=TEACHER&page=0&pageSize=20`

Respuesta paginada `{items,total,page,pageSize,totalPages,hasNext,hasPrevious}`. Cada elemento contiene `{id,displayName,email,role}`. Solo incluye cuentas habilitadas TEACHER/SECRETARY. No expone contraseñas, tokens ni alumnos. `role` es opcional; STUDENT u otro valor devuelve 400.

`q` busca una subcadena en nombre o correo, sin distinguir mayúsculas; sí distingue acentos. Máximo 200 caracteres, espacios exteriores ignorados y comodines SQL tratados literalmente. Orden estable por nombre e ID. Página desde 0 hasta 10000 y tamaño de 1 a 100 (20 por defecto).

Después de elegir una persona, envía su `id` en el endpoint existente:

```http
POST /api/v1/found-items/{foundId}/delegate
Content-Type: application/json
```

```json
{"responsibleId":"UUID del directorio"}
```

Incluye el header CSRF. El servidor vuelve a validar que el destino esté habilitado, sea docente/secretaria y cumpla las reglas de delegación; aparecer en el directorio no garantiza que pueda recibir cualquier hallazgo. Se conserva la responsabilidad hasta que la delegación se confirma. No se agrega un flujo de aceptación de delegación en este módulo.

## Listado paginado de reclamos

`GET /api/v1/claims/search?view=RECEIVED&status=PENDING&foundItemId={UUID}&page=0&pageSize=20`

| Parámetro | Valores |
|---|---|
| view | ALL (defecto), SENT (solicitados por mí), RECEIVED (de hallazgos bajo mi responsabilidad actual) |
| status | PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED, CLOSED; opcional |
| foundItemId | UUID de un hallazgo; opcional |
| page / pageSize | 0..10000 / 1..100; defecto 0 / 20 |

Los filtros se combinan. El orden es creación descendente e ID ascendente; el total se calcula después de aplicar filtros y permisos. Una página fuera de los resultados tiene items vacío y conserva el total. Una búsqueda sobre un hallazgo ajeno devuelve cero resultados, sin revelar sus reclamos.

Los elementos conservan ClaimResponseDto: IDs, descripción privada, estado, fechas de aceptación/completado y correos de responsable/solicitante. La descripción privada solo es visible al solicitante de ese reclamo y al responsable actual del hallazgo. Un solicitante no ve los reclamos de los demás sobre el mismo objeto.

El endpoint anterior `GET /api/v1/claims?page=0` conserva su array de 20 registros para compatibilidad. Para la nueva interfaz usa `/claims/search`; no intentes leer `items` en el endpoint anterior.

## Detalle del reclamo

`GET /api/v1/claims/{claimId}` devuelve:

```text
{
  claim: ClaimResponseDto,
  foundItem: FoundItemResponseDto,
  responsible: { id, displayName, email, role },
  claimant: { id, displayName, email, role },
  canContribute: boolean
}
```

No devuelve información del resto de reclamos ni un historial de datos personales anteriores. La coordinación se realiza en comentarios privados; los correos siguen en el DTO por compatibilidad. Para fotos, usa los endpoints del módulo 3: las reglas de visibilidad de publicaciones retiradas siguen aplicando.

Solo el solicitante o el responsable actual tiene acceso; reclamo inexistente o ajeno devuelve 404. El reportante que delegó deja de ver el reclamo salvo que sea participante por otra razón. El nuevo responsable lo ve en RECEIVED. Los reclamos cerrados permanecen consultables por esas partes, aunque el hallazgo se retire después. El DTO de hallazgo dentro del detalle aporta contexto a ese historial; no convierte la publicación retirada en pública.

Se conservan las acciones POST `/claims/{id}/accept`, `/reject`, `/cancel`, `/complete`, sus permisos y CSRF. Consultar el detalle no modifica estados ni envía correos. La recuperación sigue requiriendo aceptación del responsable y confirmación del solicitante.

## Arquitectura y pruebas

Controller → Service → DataService → Repository. Consultas paginadas en PostgreSQL con filtros de permisos antes de contar. Las lecturas usan REPEATABLE_READ para que el detalle y sus participantes correspondan al mismo estado durante la petición. No se necesita migración ni modificar registros existentes.

Importa `postman/Reclamos.postman_collection.json`, selecciona CazaObjetos · Local y configura devPassword. Sus 49 peticiones ejecutan el flujo original de hallazgo/reclamo/entrega y comprueban directorio, búsqueda, detalle y validación. Crea datos ficticios nuevos; no elimina publicaciones. Guarda `responsibleId` como ejemplo para una delegación manual, pero la colección no delega el hallazgo. Los permisos tras delegar se verifican en las pruebas de integración Java.

Verificado el 10 de noviembre de 2026: `mvn verify` completó 49 pruebas sin fallos contra PostgreSQL temporal. Se comprobaron exclusión de alumnos/cuentas deshabilitadas, búsqueda literal, filtros y totales, privacidad, transferencia de acceso al delegar, historial y validación HTTP. Newman completó 49 peticiones y 63 assertions sin fallos.
