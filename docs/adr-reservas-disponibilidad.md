# ADR: disponibilidad y procesos de reserva en slices separados

**Estado:** adoptado para TUR-04.

## Contexto

La consulta TUR-03 calcula horarios a partir de fuentes actuales sin persistir catálogo. TUR-04 y los CU posteriores deben guardar procesos por usuario, ejecutar efectos en cátedra y converger con eventos Kafka. El contrato externo no ofrece idempotency key ni consulta por ID de un hold; perder la respuesta de un POST no demuestra que haya fallado.

## Decisión

- El slice `availability` conserva el cálculo. Expone un puerto público `IsSlotAvailableUseCase` que entrega un resultado acotado, sin exportar los modelos de agenda y ocupaciones al slice `reservation`.
- `reservation` declara su propio puerto de salida `SlotAvailability` y un adaptador dentro del mismo proceso invoca ese puerto público. No se llama por HTTP al propio backend ni se copia el cálculo. La validación es orientativa: la respuesta de cátedra al POST es autoritativa ante carreras por el slot.
- `reservation` persiste cada intento antes del POST con `ownerUserId` derivado del JWT, un ID local, estado y versión optimista. Se registra el resultado del POST en otra operación de persistencia: no se mantiene una transacción de base de datos abierta durante la llamada externa.
- Ante timeout, error 5xx o respuesta de éxito inválida se conserva un estado de resultado desconocido, se reconsultan ocupaciones sin asumir propiedad y no se repite el POST. Un índice único parcial impide dos intentos simultáneos sin resolver del mismo usuario y slot. TUR-14 es responsable de reconciliar y desbloquear intentos pendientes tras un reinicio o un vencimiento.

## Consecuencias

La separación introduce un puerto y un adaptador, pero evita que el flujo de reserva, persistencia y Kafka crezcan dentro de la consulta sin estado. Puede existir `HOLD_REQUESTING` tras un fallo de proceso y `HOLD_OUTCOME_UNKNOWN` sin `holdId`: son estados recuperables, no pruebas de éxito o fracaso. No se ofrece lectura del proceso ni confirmación del hold hasta implementar sus CU con autorización de propietario.
