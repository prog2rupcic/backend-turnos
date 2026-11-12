# ADR: resultados terminales de vencimiento e invalidez

**Estado:** adoptado para TUR-09 (issue #20).

## Contexto

El topic de acciones incluye `AppointmentProcessExpired` y `AppointmentProcessInvalid`, con entrega at-least-once y clave `reservationProcessId`. El proceso necesita comunicar estos resultados al usuario y no reabrirse por eventos tardíos. Un plazo local no demuestra que cátedra haya expirado el proceso; la reconciliación REST completa pertenece a TUR-14.

## Decisión

- Parsear ambos eventos según v1, validando `eventId`, `eventType`, `schemaVersion=1`, integración técnica, IDs de proceso/hold/solicitud, `reason`, `occurredAt` y message key igual a `reservationProcessId`; tolerar campos desconocidos.
- Deduplicar con `reservation_processed_event` y persistir la marca y transición en la misma transacción. El listener confirma el offset después del commit. Mensajes malformados o con correlación incorrecta se descartan; los fallos inesperados se propagan para reintento.
- Usar un estado explícito `INVALID` en vez de reducir invalidez a `FAILED`; `EXPIRED` e `INVALID` almacenan `reason` en `terminal_reason`. El GET propio devuelve `status` y `terminalReason` sin exponer datos de otro usuario.
- Con proceso y hold coincidentes, aceptar `AppointmentProcessInvalid` con `reason=REQUEST_EVENT_MISMATCH` aunque su `requestEventId` difiera del local: el evento informa precisamente de que el valor enviado era incorrecto. Para los demás resultados, exigir que coincida el `requestEventId` persistido.
- Los resultados `CONFIRMED`, `EXPIRED`, `INVALID` y `FAILED` son terminales para este flujo. Los eventos posteriores/contradictorios se deduplican o ignoran y no mueven el proceso hacia atrás.
- Al aplicar vencimiento/invalidez, marcar `ABANDONED` las entradas de outbox aún `PENDING`, `IN_FLIGHT` o `RETRY_EXHAUSTED`. Las entradas ya publicadas conservan ese dato de entrega; la transición terminal no afirma que un envío previamente publicado no haya llegado.
- `expiresAt` local solo impide nuevos envíos y sirve como dato para reconciliar. No se persiste `EXPIRED` por el paso del reloj ni se inicia una segunda reconciliación antes de TUR-14.

## Consecuencias y límites

La migración V6 agrega `terminal_reason`; `INVALID` es un valor nuevo de estado JPA almacenado como texto. La invalidez por `REQUEST_EVENT_MISMATCH` se correlaciona con la clave, proceso y hold porque el contrato no ofrece un segundo identificador de intento que permita verificar un `requestEventId` que precisamente está reportado como incorrecto. Un productor que ya obtuvo una lease puede estar ejecutando I/O al mismo tiempo que llega el resultado terminal; la outbox impide reintentos futuros, aunque no puede cancelar una llamada Kafka ya iniciada.

La política de reconciliación con `GET /api/appointments`, los reintentos REST y el vencimiento de procesos después de reinicios quedan para TUR-14.

## Verificación

Fixtures de contrato para ambos eventos; tests de campos requeridos, tipos y message key; integración con Kafka embebido y PostgreSQL Testcontainers para expiración, invalidez, deduplicación, outbox pendiente, resultado tardío, motivo visible para el propietario y rechazo de acceso cruzado.
