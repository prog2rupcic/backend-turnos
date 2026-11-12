# ADR: intercambio de teléfono con outbox transaccional

**Estado:** adoptado para TUR-07/TUR-08/TUR-10 (issue #19).

**Extensión:** TUR-09 agrega estados terminales `EXPIRED`/`INVALID`, `terminalReason` y abandono atómico de los envíos aún pendientes. La política de reconciliación de operaciones inciertas continúa en TUR-14; ver [ADR: vencimiento e invalidez](adr-vencimiento-invalidez.md).

## Problema

PostgreSQL y Kafka no comparten una transacción. Guardar primero un estado de envío y caer antes de publicar pierde la respuesta a cátedra; publicar primero y caer antes de persistir pierde la identidad del envío. El acuse del broker tampoco equivale a confirmación funcional.

## Decisión

- Mantener todo en el slice `reservation`: puertos de consulta, envío, publicación de pendientes y recepción de resultados, con dominio Java puro. No se introduce otra base ni otro microservicio.
- Guardar el proceso y el evento saliente en una transacción. La outbox congela el grupo técnico, IDs, teléfono normalizado y `occurredAt`; la clave Kafka es `reservationProcessId`.
- Persistir `PHONE_SUBMISSION_PENDING` antes de publicar, `PHONE_SUBMITTED` tras acuse del broker y `CONFIRMED` únicamente ante evento válido de cátedra.
- Publicar fuera de la transacción de negocio. Un claim SQL atómico usa `FOR UPDATE SKIP LOCKED`, incrementa intentos y fija una lease de 60 segundos. Una caída se recupera al vencer la lease; el evento conserva identidad y contenido. El claim se confirma antes del I/O Kafka.
- Usar un productor idempotente con `acks=all`, espera acotada y hasta cinco claims por envío. Backoff de 2/4/8/16 segundos; el quinto fallo deja `RETRY_EXHAUSTED`. Un POST explícito con el mismo teléfono puede reiniciar ese presupuesto antes del plazo, manteniendo el evento. No se reintenta automáticamente para siempre.
- No publicar pendientes cuyo plazo ya pasó. Marcar `ABANDONED` es un resultado de entrega local, no el estado funcional `EXPIRED`. Aún puede llegar una confirmación autoritativa tardía.
- Bloquear la fila del proceso al enviar, registrar eventos y aplicar callbacks. Se conserva `@Version` para las carreras con REST. El callback cambia el proceso solo si sigue pendiente y el intento continúa en vuelo; nunca pisa un rechazo o confirmación que se adelantó.
- Usar un solo listener del topic de acciones con despacho por tipo. Un segundo listener competidor del mismo grupo podría recibir eventos que no sabe procesar.
- Registrar solicitudes, confirmaciones y rechazos en `reservation_processed_event`, con marca y efecto atómicos. V5 siembra los `requestEventId` vigentes; no reconstruye un historial anterior inexistente.
- Validar grupo, identificadores y correlación con la solicitud; confirmar además profesional/fecha/hora. Procesos desconocidos, correlaciones inválidas y estados finales no se alteran.
- Permitir consultar/envíar solo al propietario del JWT. El teléfono no se devuelve ni se registra; el listener de errores del productor omite valores y excepciones con posibles payloads.

## Rechazos y orden

Una corrección tras un rechazo usa un evento nuevo; un reenvío técnico reutiliza el original. Los duplicados se reconocen durablemente y un rechazo anterior al instante del último envío no afecta a la corrección. El contrato de rechazo no informa el `eventId` del envío rechazado, solo `requestEventId`: no permite una correlación perfecta de resultados contradictorios con IDs distintos entre intentos. Se mantiene un único intento funcional activo y se documenta esa limitación, sin inventar campos de cátedra.

## Consecuencias

La entrega es al menos una vez. La idempotencia del productor no evita por sí sola duplicados tras reinicios: la identidad persistida y la deduplicación funcional siguen siendo necesarias. Se usa JDBC exclusivamente en infraestructura para el claim PostgreSQL y las actualizaciones condicionadas, compartiendo la transacción Spring/JPA.

Los resultados agotados/abandonados se exponen por `deliveryStatus`; la reconciliación con REST, cancelación y vencimiento autoritativo se incorporan en sus issues. Los teléfonos quedan almacenados en la outbox; no hay purga automática en esta entrega.

## Verificación

Tests con PostgreSQL real y Kafka embebido: contrato saliente y clave, entrada inválida seguida de válida, confirmación/rechazo, corrección y duplicados, rollback atómico, solicitudes concurrentes, recuperación de lease, agotamiento/reactivación y confirmación antes del acuse. Las pruebas HTTP usan JWT firmado y verifican `401`/`403`/`404`, validación y ausencia del teléfono en respuestas.
