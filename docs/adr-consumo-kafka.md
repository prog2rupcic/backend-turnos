# ADR: consumo Kafka de la solicitud de teléfono

**Estado:** adoptado para TUR-06.

## Contexto

Después de la confirmación REST inicial, cátedra publica `AdditionalInformationRequested` en el topic de acciones. El evento puede adelantarse a la respuesta REST de TUR-05 y Kafka garantiza entrega al menos una vez, por lo que puede repetirse. El contrato exige conservar el `eventId` como `requestEventId` para responder luego con el teléfono y confirmar el offset después de persistir.

## Decisión

- El listener se registra solo con `TURNOS_KAFKA_ENABLED=true`; así los tests y un entorno sin broker no intentan conectarse. Los brokers, el topic y el consumer group vienen de `integration` y se externalizan.
- El mensaje se valida antes de tocar la base: tipo, `schemaVersion=1`, `requiredField=PHONE_NUMBER`, identificadores e instantes, y clave Kafka igual a `reservationProcessId`. Los campos desconocidos se ignoran. Un mensaje inválido se registra y se descarta sin bloquear la partición.
- `requestEventId` se guarda en el propio proceso con restricción única, junto con `requiredField`, `message` y `expiresAt`. La marca de idempotencia y la transición a `WAITING_FOR_PHONE` se escriben en la misma transacción, de modo que un evento repetido no tiene efecto.
- Se acepta el evento desde `HELD`, `CONFIRM_REQUESTING`, `CONFIRM_OUTCOME_UNKNOWN` y `WAITING_FOR_PHONE`. Un estado terminal (`EXPIRED`, `FAILED`) no se reabre; un `holdId` distinto o un proceso inexistente se descartan con registro.
- El offset se confirma después del commit de la transacción mediante `ack-mode=record` con autocommit desactivado. Los fallos inesperados se reintentan tres veces con 2 s de espera; agotados los intentos se registran y se descartan para no bloquear el resto de la partición.
- La confirmación REST tolera la carrera con el consumidor: si al guardar `WAITING_FOR_PHONE` la versión optimista falla porque Kafka ya aplicó la solicitud, se relee el proceso y se devuelve su estado sin pisar `requestEventId`.

## Consecuencias

El proceso conserva todo lo necesario para que TUR-07 publique `AdditionalInformationSubmitted`. La restricción única de `requestEventId` impide aplicar el mismo evento a dos procesos, pero una nueva solicitud para el mismo proceso (por ejemplo, tras un rechazo) reemplaza el identificador anterior; el histórico de solicitudes no se conserva. La tabla genérica de eventos procesados se puede introducir cuando existan más consumidores y se necesite deduplicar por evento entre agregados.
