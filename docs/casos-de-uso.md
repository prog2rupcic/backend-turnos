# Casos de uso — backend-turnos

## Alcance y vocabulario

Este servicio administra los usuarios finales, su autenticación, los procesos locales de reserva y las reservas asociadas a cada usuario. Consulta la **agenda vigente** a `backend-catalogo/` mediante un contrato protegido con JWT; no mantiene una réplica del catálogo para búsquedas o nuevas reservas.

**«Turnos disponibles»** son horarios libres en una **fecha concreta**, obtenidos al combinar horarios semanales vigentes con ocupaciones `HELD` y `CONFIRMED` consultadas a la cátedra. Es distinto del filtro **«atiende el día»** de catálogo, que solo indica si existe agenda semanal para ese día de la semana.

## Casos de uso

| ID | Caso de uso | Actor / disparador | Resultado esperado |
| --- | --- | --- | --- |
| TUR-01 | Registrar usuario final | Usuario desde Android | Valida `login`, `password`, `firstName`, `lastName`, `email`, `imageUrl` opcional y `langKey`; crea un usuario activo con contraseña hasheada con Argon2id, sin permitir elegir ID, autoridades ni datos de auditoría. |
| TUR-02 | Iniciar sesión | Usuario desde Android | Verifica la contraseña contra su hash Argon2id y comprueba que la cuenta esté activa. Emite un JWT propio de la aplicación válido por una hora, o por siete días con `rememberMe`; el JWT técnico de cátedra nunca se entrega al cliente. |
| TUR-03 | Consultar turnos disponibles | Usuario autenticado | Obtiene agenda vigente desde catálogo y ocupaciones por REST desde cátedra; calcula horarios libres para profesional y fecha. |
| TUR-04 | Bloquear temporalmente un turno | Usuario autenticado | Valida los datos vigentes, solicita el hold REST y conserva localmente `holdId`, `reservationProcessId`, `expiresAt` y el usuario propietario. Si se pierde la respuesta `201`, no asume que el hold pertenece al usuario ni repite inmediatamente el POST: consulta las ocupaciones del profesional y la fecha. |
| TUR-05 | Iniciar confirmación del hold | Usuario propietario | Envía identificador estable del usuario, nombre y apellido al endpoint REST de confirmación; registra `WAITING_FOR_PHONE`. La respuesta `202` no equivale a una reserva confirmada. |
| TUR-06 | Recibir solicitud de teléfono | Kafka `AdditionalInformationRequested` | Asocia la solicitud al proceso y persiste su `eventId` como `requestEventId` para responder. |
| TUR-07 | Enviar teléfono solicitado | Usuario propietario desde Android | Valida el número y publica `AdditionalInformationSubmitted` con los identificadores requeridos y message key `reservationProcessId`. |
| TUR-08 | Procesar confirmación o rechazo del teléfono | Kafka `AppointmentConfirmed` / `AdditionalInformationRejected` | Guarda la reserva confirmada o permite corregir y reenviar un teléfono rechazado antes de `expiresAt`. |
| TUR-09 | Procesar vencimiento o invalidez | Kafka `AppointmentProcessExpired` / `AppointmentProcessInvalid`, o control local | Cierra o reconcilia el proceso y comunica el resultado; un evento tardío no reabre estados finales. |
| TUR-10 | Consultar estado de un proceso propio | Usuario autenticado | Devuelve su avance y resultado, incluidos estados pendientes del intercambio asincrónico; impide consultar procesos ajenos. |
| TUR-11 | Consultar mis reservas | Usuario autenticado | Devuelve únicamente reservas asociadas a su identidad local, aunque la API de cátedra devuelva reservas de toda la cuenta técnica. |
| TUR-12 | Cancelar mi reserva confirmada | Usuario propietario | Comprueba la propiedad y el estado, solicita la cancelación REST y guarda el resultado; una cancelación ya realizada es idempotente. |
| TUR-13 | Procesar cancelación notificada | Kafka `AppointmentCancelled` | Converge el estado local de la reserva con el central sin repetir efectos. |
| TUR-14 | Recuperar operaciones pendientes | Reinicio, timeout o conexión recuperada | Reconcilia por identificadores persistidos antes de repetir acciones con efectos; limita y registra los reintentos. Si se perdió la respuesta inicial de creación del hold y no hay identificadores, consulta ocupaciones: ante `HELD` espera hasta el `expiresAt` informado y vuelve a consultar sin asumir propiedad; ante `CONFIRMED` cierra el intento; si no hay ocupación, intenta crear un nuevo hold con reintentos limitados y contemplando `409` por concurrencia. |
| TUR-15 | Impedir acceso cruzado entre usuarios | Cualquier consulta o acción protegida | Autoriza según la identidad del JWT y la propiedad local, nunca según un ID de usuario enviado libremente por el cliente. |

**Estado de TUR-15:** está implementada la base reutilizable de autorización. El `sub` del JWT válido identifica a `user-{id}` y el caso de uso compara ese ID con el propietario persistido del recurso; ausencia de credenciales devuelve `401` y una propiedad distinta devuelve `403`. Las pruebas HTTP de esta base usan un endpoint exclusivo de test. La verificación de acceso cruzado en procesos y reservas reales queda pendiente de TUR-04/TUR-10/TUR-11/TUR-12, cuando existan sus endpoints y persistencia.

## Reglas compartidas

- TUR-01 y TUR-02 **pertenecen a este backend**. El JWT de usuario se firma con HS256: `iss=backend-turnos`, `sub=user-{id}`, audiencias `backend-turnos` y `backend-catalogo`, y autoridades en el claim `authorities`. Ambos servicios comparten la clave de firma mediante configuración externa; el JWT técnico de cátedra es independiente.
- Solo se aceptan contraseñas hasheadas con Argon2id; no hay cuentas heredadas con BCrypt que migrar. Se almacena un único hash por usuario, nunca la contraseña en claro. La verificación del JWT es independiente del hash de contraseñas.
- Cada proceso y reserva local conserva su propietario. Para `externalPatientId` se usa un identificador estable de ese usuario dentro de la aplicación; `groupId` identifica solo la cuenta técnica ante cátedra.
- El hold vence en el instante `expiresAt` informado por cátedra: guardarlo localmente no amplía su vigencia.
- No existe idempotencia ni consulta por identificador para recuperar un hold cuya respuesta `201` se perdió. El TTL predeterminado es de diez minutos desde la creación en el servidor, pero esperar diez minutos desde el timeout no garantiza que el slot esté disponible.
- Los eventos Kafka pueden repetirse. Se deduplican por `eventId` y no se retrocede desde un estado final; el rechazo de teléfono permite un nuevo intento válido mientras el proceso siga vigente.
- Si catálogo está indisponible no se inician operaciones que necesiten agenda vigente; pueden seguir atendiéndose las que dependan solo de datos propios cuando sea posible.
- La cuenta técnica se registra una sola vez por Postman o equivalente. Ambos backends usan sus secretos externalizados; el JWT técnico no se expone a Android.

**Fuentes:** `PROJECT_STATEMENT-v1.md` (§§3.2, 4.2, 5, 7–9) e `INTEGRATION_REFERENCE-v2.md` (§§2, 5, 8–13, 15, 17–18).
