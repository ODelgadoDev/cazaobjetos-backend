# Adaptación a los ejemplos de Alfonso Barroso

Repositorios consultados el 31 de octubre de 2026:

- [order-service](https://github.com/alfonsojbarroso/order-service), commit `a8372344f49ae1aa7622334e7887d2bc1c6f46a1`.
- [billing-service](https://github.com/alfonsojbarroso/billing-service), commit `9cefdb8bf5c130b8be9d37280eaf01df99ae8eb2`.

## Patrones adoptados

| Referencia | Aplicación en CazaObjetos |
|---|---|
| Paquetes controller, dto, model, repository, service | Separación explícita por capas |
| OrderController y ResponseEntity | FoundItemController, LostReportController, ClaimController y respuestas tipadas |
| OrderRequestDto / OrderResponseDto como record | ReportRequestDto, ClaimRequestDto, DTOs de respuesta por recurso |
| OrderModel / BillingModel con @Entity | Seis entidades JPA; conversión model → response DTO |
| JpaRepository | Seis interfaces de persistencia, queries JPQL y bloqueo pesimista |
| OrderService / OrderDataService / OrderEventService | ItemService / ItemDataService / ItemEventService |
| BusinessException y handler central | Excepción de negocio y ApiExceptionHandler |
| enums, constants, util | Estados tipados, constantes y ValidateUtil sin estado mutable |
| Constructor injection | Dependencias finales e inyección por constructor |
| application.yaml y rutas configurables | YAML, url.api y variables de entorno |
| Pruebas por capas | ItemServiceTest en paquete service, más prueba HTTP de integración |

## Decisiones deliberadas

Se mantiene Java 21 y Spring Boot 4.1.2 (los ejemplos usan 4.1.1). Se conserva Spring MVC con sesión y CSRF; los ejemplos incluyen WebFlux, pero el acceso JPA es bloqueante y el MVP actual no requiere un flujo reactivo. Kafka y Redis no se agregan: los ejemplos usan eventos entre servicios e idempotencia distribuida; este MVP tiene un único backend y outbox PostgreSQL.

Flyway conserva el esquema y JPA usa `validate`, en lugar de `ddl-auto: update`. Los índices parciales y bloqueos siguen protegiendo concurrencia. No se copian transformaciones de DTOs que sobrescriban el estado o la fecha leídos desde la base. Los DTOs de salida reflejan lo persistido.

BusinessException es unchecked para asegurar rollback de transacciones. Validación de entrada usa `@Valid` y Jakarta Validation; ValidateUtil concentra las precondiciones de negocio sin campos mutables compartidos.

## Compatibilidad

No cambia el esquema V1 ni las rutas HTTP. Las respuestas antes basadas en mapas SQL ahora son records camelCase; el cliente debe usar, por ejemplo, `responsibleId` y `officeMarkedAt`. Los errores de negocio usan `status` y `message`. El contrato queda documentado en README. No existe frontend en este repositorio que haya requerido migración.
