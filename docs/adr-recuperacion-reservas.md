# ADR: reconciliación de operaciones de reserva pendientes

**Estado:** adoptado para TUR-14.

## Contexto

La respuesta de un POST puede perderse después de producir su efecto. Cátedra no ofrece consulta por ID de hold/proceso ni clave de idempotencia para crear holds. El listado REST permite buscar reservas de un paciente técnico por `reservationProcessId`; las ocupaciones solo informan horario, estado y (si `HELD`) vencimiento, sin identidad del dueño.

## Decisiones

- Persistir intentos, fase, próximo intento y último error en `reservation_recovery` (V8). Ejecutar reconciliación al arrancar y periódicamente, incluso sin Kafka; desactivable para tests con `TURNOS_RECOVERY_ENABLED=false`. El claim transaccional `FOR UPDATE SKIP LOCKED` usa un lease de diez minutos; al morir una instancia otra puede reclamarlo. Presupuesto de cinco intentos/fase, hasta tres procesos/ciclo, backoff sin bucles infinitos, estado `REVIEW` si no existe prueba suficiente. Un cambio de fase reinicia el presupuesto; un estado final marca `DONE`.
- Consultar `GET /api/appointments` con el paciente derivado del propietario local, paginado y con JWT técnico. No confiar en `externalPatientId` solo: buscar exactamente el ID externo persistido y cotejar slot e ID de reserva. Aplicar un resultado bajo bloqueo de fila, sin mantener una transacción abierta durante HTTP. Si cambió el estado o se perdió la lease se descarta el resultado atrasado.
- `PHONE_PENDING` después de confirmación incierta significa que cátedra aceptó la confirmación inicial. No crear `requestEventId` desde REST: solo el evento Kafka lo contiene. Una reserva `CONFIRMED`, `CANCELLED` o `FAILED` es prueba suficiente para converger, preservando el propietario y sin reabrir terminales. Un listado sin coincidencia no prueba que la operación nunca sucedió; se posterga o se expone `REVIEW`.
- Si una cancelación incierta sigue `CONFIRMED` en cátedra, no repetir automáticamente el POST: podría estar aún en proceso. Exponer `CANCEL_OUTCOME_UNKNOWN` con `recoveryState=REVIEW`, no `CANCELLED` ni `CONFIRMED` local. Cátedra puede aún enviar el evento Kafka y converger. Una actuación manual informada o una garantía de consistencia del listado deberá acordarse antes de habilitar reintentos de este efecto.
- Sin IDs de hold se consume el puerto público `GetOccupanciesUseCase` del slice `availability` mediante un adaptador del slice `reservation`; este conserva `expiresAt` de la respuesta REST. Un `HELD` o `CONFIRMED` no prueba titularidad. Un hold sin IDs espera el vencimiento observado; con ocupación confirmada queda `REVIEW`. Si está libre, se espera el TTL habitual más un margen, se realizan dos comprobaciones separadas, se vuelve a validar agenda y ocupación y recién entonces puede crearse otro hold. Cátedra no garantiza formalmente que un POST original no llegue después de estas comprobaciones: se documenta la incertidumbre residual en vez de inventar propiedad.
- Para evitar carreras, la transición local y el cambio de progreso a `DONE` se escriben juntos, usando la fila del proceso como bloqueo y `@Version`. El servicio se mantiene operativo si catálogo no está disponible: consulta/cancelación propia y consumo Kafka no dependen de él; solo el nuevo intento de hold requiere agenda fresca.

## Límites

`REVIEW` no es un estado funcional de la reserva sino un diagnóstico de recuperación. Mantiene el estado original y no libera bloqueos por ausencia de evidencia. No hay endpoint público para forzar la repetición de operaciones inciertas: la intervención y reconciliación manual segura se definirán según las garantías adicionales de cátedra. El TTL de diez minutos para un hold se describe como predeterminado, no como garantía de causalidad si la solicitud REST original se procesa tardíamente.

## Verificación

Testcontainers PostgreSQL para claim/lease/budget y migración V8, pruebas de estados remotos `PHONE_PENDING`/`CONFIRMED`/`CANCELLED`, ocupaciones `HELD`/`CONFIRMED`/libres, carrera frente a cambio de estado y aislamiento de diagnóstico por JWT. Tests de contrato para `expiresAt` de la ocupación y ausencia de transiciones finales basadas solo en el reloj.
