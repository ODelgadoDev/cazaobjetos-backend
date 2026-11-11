# Verificación del incremento de cuentas — 2026-11-05

- `mvn verify`: 22 pruebas, cero fallos/errores, ninguna omitida.
- Registro: contraseña cifrada, cuenta deshabilitada y rol STUDENT; dominio exacto institucional.
- Verificación: expiración, uso único, carrera entre verificaciones, reenvío y anulación del código anterior.
- HTTP: protección CSRF, login rechazado antes de verificar, login exitoso después y sin elevación de rol.
- Correo: marcado SENT, ausencia de reenvío en operación normal, reintentos con espera y FAILED tras cinco intentos.
- Limitación local de intentos: IP independiente, límite y recuperación al pasar un minuto.
- Colección nueva ejecutada con Newman y Mailpit real local: 16 peticiones incluyendo consultas repetidas del buzón, 20 comprobaciones, cero fallos.
- Colección anterior de hallazgos/reclamos: 40 peticiones y 47 comprobaciones, cero fallos.

Pruebas sobre PostgreSQL temporal, sin vaciar la base del proyecto y sin mensajes enviados a Internet. En esa verificación aún no estaban implementados SMTP institucional real, recuperación de contraseña ni operación distribuida. Para el estado actual consulta [módulo 5](passwords-and-roles.md).
