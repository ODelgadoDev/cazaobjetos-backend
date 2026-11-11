# Verificación de la adaptación — 2026-10-31

Ubicación final: `/home/odelgado/Documentos/cazaobjetos-backend`.

Entorno: Java 21, Spring Boot 4.1.2, Spring Data JPA/Hibernate y PostgreSQL 18.6 temporal.

- `mvn clean verify`: 9 pruebas, 0 fallos, 0 errores, 0 omitidas; JAR generado.
- Flyway validó V1 existente sin modificar la migración ni perder compatibilidad del esquema.
- Hibernate validó las seis entidades contra PostgreSQL (`ddl-auto: validate`).
- `scripts/smoke.py` ejecutado contra el JAR desde Documentos: correcto.

Cobertura: permisos, privacidad, resguardo sin transferencia de responsabilidad, delegación, recuperación solo tras aceptación, cancelación, cierre de extravío vinculado, restricción de reclamos duplicados, un solo ganador entre aceptaciones concurrentes y un solo aviso entre recuperaciones simultáneas.

HTTP: login/logout, CSRF, validación, DTOs camelCase, ausencia de contraseña en perfil, permisos, privacidad, recuperación y reintentos.

La API y PostgreSQL temporales se detuvieron al terminar. La prueba HTTP creó datos ficticios solo en la base temporal. No se modificaron archivos sincronizados en sources/.

Sin verificar/implementar todavía: SMTP real, registro institucional, fotos, frontend y despliegue. Compose está incluido, pero estas comprobaciones usaron PostgreSQL local.
