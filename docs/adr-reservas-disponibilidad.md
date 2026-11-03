# ADR: disponibilidad y procesos de reserva en slices separados

**Estado:** adoptado para TUR-04 y TUR-05.

## Contexto

La consulta TUR-03 calcula horarios a partir de fuentes actuales sin persistir catálogo. TUR-04 y los CU posteriores deben guardar procesos por usuario, ejecutar efectos en cátedra y converger con eventos Kafka. El contrato externo no ofrece idempotency key ni consulta por ID de un hold; perder la respuesta de un POST no demuestra que haya fallado.

## Decisión

- El slice `availability` conserva el cálculo. Expone un puerto público `IsSlotAvailableUseCase` que entrega un resultado acotado, sin exportar los modelos de agenda y ocupaciones al slice `reservation`.
- `reservation` declara su propio puerto de salida `SlotAvailability` y un adaptador dentro del mismo proceso invoca ese puerto público. No se llama por HTTP al propio backend ni se copia el cálculo. La validación es orientativa: la respuesta de cátedra al POST es autoritativa ante carreras por el slot.
- `reservation` persiste cada intento antes del POST con `ownerUserId` derivado del JWT, un ID local, estado y versión optimista. Se registra el resultado del POST en otra operación de persistencia: no se mantiene una transacción de base de datos abierta durante la llamada externa.
- Ante timeout, error 5xx o respuesta de éxito inválida se conserva un estado de resultado desconocido, se reconsultan ocupaciones sin asumir propiedad y no se repite el POST. Un índice único parcial impide dos intentos simultáneos sin resolver del mismo usuario y slot. TUR-14 es responsable de reconciliar y desbloquear intentos pendientes tras un reinicio o un vencimiento.
- La confirmación inicial se persiste como `CONFIRM_REQUESTING` antes del POST y como `WAITING_FOR_PHONE` al aceptar la respuesta `202`. Timeout, `5xx` o respuesta inválida producen `CONFIRM_OUTCOME_UNKNOWN`; no se reintenta automáticamente. Una repetición explícita permite converger usando el código `RESERVATION_PROCESS_ALREADY_CONFIRMED`.
- Los nombres enviados a cátedra se leen del perfil local. `user` expone únicamente un `UserProfile` por su puerto de entrada público; `reservation` mantiene un puerto de salida y un adaptador que traduce ese perfil a su propio modelo `PatientProfile`. El slice de reserva no depende del modelo completo del usuario ni accede a su entidad o repositorio.
- Los estados de confirmación pendientes y `WAITING_FOR_PHONE` siguen dentro del índice único parcial para impedir otro proceso activo del mismo usuario y horario.

## Consecuencias

La separación introduce puertos y adaptadores, pero evita que el flujo de reserva, persistencia y Kafka crezcan dentro de la consulta sin estado. Puede existir `HOLD_REQUESTING` tras un fallo de proceso y `HOLD_OUTCOME_UNKNOWN` sin `holdId`; los estados `CONFIRM_REQUESTING` y `CONFIRM_OUTCOME_UNKNOWN` también requieren recuperación, y ninguno prueba por sí solo el resultado externo. TUR-05 ya confirma holds propios; la lectura del proceso y la reconciliación automática quedan para TUR-10 y TUR-14.
