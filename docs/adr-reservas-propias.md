# ADR: listar y cancelar reservas propias

**Estado:** adoptado para TUR-11/TUR-12/TUR-13.

## Contexto

La API de cátedra usa un JWT de cuenta técnica compartida y puede devolver reservas de muchos usuarios. El único vínculo confiable entre una reserva y un usuario final está en `reservation_process.owner_user_id`. La cancelación REST tiene efectos y su respuesta puede perderse; cátedra también emite `AppointmentCancelled` por Kafka.

## Decisión

- Exponer `GET /api/reservations?page=&size=` y `POST /api/reservations/{id}/cancel`, siempre con JWT propio `ROLE_USER`. El ID de la ruta es local, y `externalPatientId=user-{id}` se deriva exclusivamente del JWT.
- El listado usa el filtro remoto por `externalPatientId` como optimización, **no como autorización**. Antes de devolver cada registro verifica el proceso local, propietario, profesional, fecha, hora e ID de reserva si está persistido. No expone el teléfono ni datos de otros usuarios. Calcula `totalElements` y paginación después de este filtro. Recorre hasta 100 páginas remotas de 100 elementos, límite 10 000; si no puede completarlas falla cerrado, sin dar totales engañosos. Esta decisión privilegia exactitud y aislamiento frente al costo del recorrido; paginación escalable con un índice local queda como evolución si el volumen crece.
- Validar propiedad y estado `CONFIRMED` bajo bloqueo de fila, persistir `CANCEL_REQUESTING` y el motivo en una transacción, y recién entonces llamar al REST externo sin mantener la transacción abierta. El 200 válido mueve a `CANCELLED`; un resultado incierto se guarda como `CANCEL_OUTCOME_UNKNOWN` y no se reenvía automáticamente. Un rechazo por JWT técnico (401/403) devuelve indisponibilidad de integración y restablece el estado confirmado.
- Una nueva llamada sobre `CANCELLED` es idempotente; sobre un intento incierto no dispara otro POST. `AppointmentCancelled` valida versión, grupo, clave, proceso, hold, solicitud, ID de reserva y slot; marca `eventId` y transición en la misma transacción, con commit de offset después de persistir. Si llega antes que la respuesta REST, esta última lee el estado ya cancelado y no lo pisa. Si se perdió la notificación previa de confirmación, el evento de cancelación autoritativo puede guardar el `reservationId` faltante y converger directamente desde un estado no terminal. Eventos incompatibles o tardíos no retroceden `CANCELLED`.
- La migración V7 agrega `cancelled_at` y `cancellation_reason`. No se crea una réplica del catálogo ni se comparte una base de datos con el otro backend. `CANCEL_OUTCOME_UNKNOWN` queda identificado para reconciliación por identificadores persistidos en TUR-14.

## Verificación

Pruebas HTTP contra cátedra ficticia y PostgreSQL Testcontainers para aislamiento incluso si cátedra mezcla usuarios, paginación, 401/403, cancelación idempotente, resultado incierto y carrera REST/Kafka. Fixtures del contrato y Kafka embebido para cancelación repetida, desconocida o inconsistente. Sin dependencia de credenciales reales de cátedra.
