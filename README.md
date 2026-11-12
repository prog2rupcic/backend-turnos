<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-35%2F100-orange) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-no%20compila-red) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-0.0%25-green) ![%POO](https://img.shields.io/badge/%25POO-56%25-orange) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-58-orange) ![D2](https://img.shields.io/badge/D2-36-red) ![D4](https://img.shields.io/badge/D4-0-red) ![D5](https://img.shields.io/badge/D5-95-green) ![D6](https://img.shields.io/badge/D6-2-red) ![D7](https://img.shields.io/badge/D7-31-red)

**Calidad (heurístico, 7 dimensiones):** índice **35/100** · build **no compila** · aprobar **no (6/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# backend-turnos

## Registro e inicio de sesión (TUR-01 y TUR-02)

El servicio usa PostgreSQL y aplica la migración Flyway al iniciarse. Configurar `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` para su base de datos propia (valores locales predeterminados: `jdbc:postgresql://localhost:5433/turnos`, `turnos`, `turnos`). Configurar obligatoriamente `TURNOS_USER_JWT_SECRET` con **al menos 32 bytes UTF-8**; ambos backends deben recibir la **misma** clave (`CATALOG_USER_JWT_SECRET` en catálogo). No publicar la clave en Git ni entregarla a la app Android.

`POST /api/register` recibe `login`, `password`, `firstName`, `lastName`, `email`, `imageUrl` opcional y `langKey` opcional. Registra al usuario activo con `ROLE_USER` y almacena únicamente su hash Argon2id; la respuesta `201` no contiene contraseña ni hash. `POST /api/authenticate` recibe `username` (login), `password` y `rememberMe` (opcional, por defecto `false`). Si las credenciales son válidas responde `200` con `{ "id_token": "<jwt>" }` y la cabecera `Authorization: Bearer <jwt>`; las credenciales inválidas responden `401` sin distinguir usuario de contraseña. El registro y el login son públicos; las operaciones de turnos disponibles, holds y confirmación requieren `ROLE_USER`.

El JWT propio de usuario se firma con HS256, `iss=backend-turnos`, `sub=user-{id}`, audiencias `backend-turnos` y `backend-catalogo`, y el claim `authorities` con `ROLE_USER`. La duración normal es **1 hora** y con `rememberMe` es **7 días**; se pueden cambiar mediante `TURNOS_USER_JWT_TTL` (por defecto `PT1H`) y `TURNOS_USER_JWT_REMEMBER_ME_TTL` (por defecto `P7D`). `TURNOS_USER_JWT_ISSUER` permite cambiar el emisor (por defecto `backend-turnos`), siempre coordinado con catálogo. No se emiten tokens de renovación: al vencer la sesión se requiere un nuevo login. El JWT técnico de cátedra es distinto y nunca se envía al usuario.

La API es **stateless**: no utiliza cookies de sesión para autenticar y los clientes envían explícitamente el JWT en `Authorization: Bearer`. Por eso `POST /api/register`, `POST /api/authenticate`, `POST /api/reservation-processes` y `POST /api/reservation-processes/{id}/confirm` están excluidos de la verificación CSRF; el resto de rutas conserva la protección de Spring Security por defecto. Una futura autenticación por cookies requeriría protección CSRF.

## Consultar turnos disponibles (TUR-03)

`GET /api/professionals/{id}/availability?date=YYYY-MM-DD` requiere un JWT propio con `ROLE_USER`. Devuelve `professionalId`, `date` y `availableStartTimes` (horas locales, por ejemplo `"09:30:00"`). Para construir los slots consulta `GET /api/professionals/{id}` en `backend-catalogo` **propagando el JWT del usuario** y `GET /api/appointment-occupancies?professionalId=...&from=...&to=...` en cátedra **con el JWT técnico**. Se eliminan ocupaciones `HELD` y `CONFIRMED`, se evitan horarios duplicados y no se ofrecen horarios ya pasados. Profesionales o categorías deshabilitados devuelven una lista vacía; el profesional inexistente devuelve `404`. Fechas pasadas o ID no positivo devuelven `400`; ante fuentes indisponibles devuelve `503`, y ante datos externos inválidos `502`, sin afirmar que los horarios están libres. El resultado es orientativo: TUR-04 volverá a validar el turno antes del hold.

Configurar `TURNOS_CATALOG_BASE_URL` (URL HTTP del servicio de catálogo), `CATEDRA_REST_URL` y `CATEDRA_TECHNICAL_JWT` exclusivamente en el backend, fuera de Git. El JWT técnico nunca llega a Android ni a catálogo. `TURNOS_AVAILABILITY_ZONE_ID` controla la interpretación de «hoy», por defecto `America/Argentina/Buenos_Aires`; la zona de cátedra aún debe confirmarse y puede cambiarse sin migrar datos de TUR-03. Los horarios semanales son horas locales, mientras que los vencimientos externos son instantes UTC. Las llamadas HTTP tienen timeout de conexión de 3 s y lectura de 5 s. Se usan clientes síncronos `RestClient` incluidos en Spring WebMVC, sin agregar dependencias reactivas.

El reporte de compañeros sobre `500` con `from`/`to` corresponde a `GET /api/appointments` (listado de reservas), distinto del endpoint de ocupaciones requerido en TUR-03. La consulta real de ocupaciones debe verificarse por separado; si falla, se devuelve indisponibilidad y no se reemplaza por el listado de reservas.

## Bloquear temporalmente un turno (TUR-04)

`POST /api/reservation-processes` requiere JWT de usuario con `ROLE_USER` y el cuerpo `{ "professionalId": 15, "date": "2026-11-09", "startTime": "09:30" }`. El ID del usuario **no** se recibe del cliente: se obtiene del `sub=user-{id}` validado del JWT y se envía a cátedra como `externalPatientId`. El horario tiene precisión de minutos. Se vuelven a consultar la agenda vigente de catálogo (con JWT de usuario) y las ocupaciones de cátedra (con JWT técnico) antes de pedir `POST /api/appointment-holds` (también con JWT técnico). La validación previa no bloquea el slot: cátedra resuelve definitivamente las carreras entre usuarios con `409`.

Si cátedra responde `201` con `status=HELD`, el backend responde `201` con `id` local, `status`, `holdId`, `reservationProcessId`, `expiresAt` y los datos del horario, **sin exponer la identidad de otros usuarios ni el JWT técnico**. El vencimiento es el instante UTC informado por cátedra; TUR-05 iniciará la confirmación, no se confirma en esta operación. Si el slot no está libre o cátedra responde `SLOT_ALREADY_HELD`, `SLOT_ALREADY_RESERVED`, `INVALID_SLOT` o `PROFESSIONAL_DISABLED`, se devuelve `409`. Un profesional inexistente devuelve `404`; una fuente indisponible, `503`; datos inválidos de integración, `502`. Los errores incluyen una propiedad `code` estable.

Antes del POST externo se persiste el intento `HOLD_REQUESTING` en PostgreSQL con propietario y horario. Se guarda `HELD` cuando llega una respuesta válida; los rechazos concluyentes se guardan como `FAILED`. Si el POST pudo haber creado el hold pero la respuesta se perdió, el servidor devuelve `503` con `code=OUTCOME_UNKNOWN` y `processId` local, persiste `HOLD_OUTCOME_UNKNOWN` y vuelve a consultar ocupaciones sin asumir que el hold es propio ni repetir el POST. El mismo usuario no puede abrir otro intento no resuelto para el mismo horario (`409 ALREADY_IN_PROGRESS` con `processId`); un índice único parcial protege también ante solicitudes concurrentes. La recuperación definitiva y liberación de intentos inciertos o vencidos corresponde a TUR-14. No existe en cátedra una clave de idempotencia ni GET de hold por ID, y un hold local no extiende el TTL externo. Ver [decisión de slices y recuperación](docs/adr-reservas-disponibilidad.md).

## Iniciar confirmación del hold (TUR-05)

`POST /api/reservation-processes/{id}/confirm` requiere el JWT propio del usuario y solo confirma un proceso local que pertenezca a ese usuario y esté en `HELD`. No recibe datos de identidad: deriva `externalPatientId=user-{id}` del JWT y obtiene `firstName`/`lastName` mediante un puerto del slice `reservation` conectado al caso de uso público de perfil del slice `user`. El adaptador envía esos datos a `POST /api/appointment-holds/{holdId}/confirm` con el JWT técnico de cátedra.

Una respuesta `202 WAITING_FOR_PHONE` persiste ese estado y devuelve `202` con el ID local y el vencimiento informado por cátedra; no representa una reserva confirmada. Si el proceso ya está esperando el teléfono, la petición local es idempotente y no repite el POST externo. Antes de llamar a cátedra se persiste `CONFIRM_REQUESTING`; un timeout o `5xx` produce `503 OUTCOME_UNKNOWN` y `CONFIRM_OUTCOME_UNKNOWN`, sin reintento automático. Un reintento explícito en ese estado puede converger cuando cátedra responda `RESERVATION_PROCESS_ALREADY_CONFIRMED`. `HOLD_EXPIRED` persiste el estado terminal `EXPIRED`. Los estados no resueltos permanecen protegidos por el índice único parcial por usuario y horario. La solicitud de teléfono y su `requestEventId` corresponden a TUR-06.

## Recibir la solicitud de teléfono (TUR-06)

El consumidor Kafka lee `AdditionalInformationRequested` del topic de acciones configurado (`TURNOS_KAFKA_ACTIONS_TOPIC`, por defecto `catedra.turnos.acciones`) usando `TURNOS_KAFKA_BOOTSTRAP_SERVERS` y `TURNOS_KAFKA_CONSUMER_GROUP_ID`. Se habilita explícitamente con `TURNOS_KAFKA_ENABLED=true`; sin esa variable el listener no se registra y el resto del backend funciona igual. No hay credenciales ni direcciones reales en el repositorio: todas se reciben desde `integration` y se configuran fuera de Git.

Cada mensaje se valida contra el contrato v1: `eventType=AdditionalInformationRequested`, `schemaVersion=1`, `requiredField=PHONE_NUMBER`, identificadores no vacíos, instantes ISO-8601 y clave Kafka igual a `reservationProcessId`. Los campos desconocidos se ignoran. Un mensaje inválido, un proceso inexistente o un `holdId` distinto del persistido se registran con datos de diagnóstico y se descartan sin bloquear la partición.

El evento se aplica aunque llegue antes de que se guarde la respuesta REST de TUR-05: se acepta desde `HELD`, `CONFIRM_REQUESTING`, `CONFIRM_OUTCOME_UNKNOWN` y `WAITING_FOR_PHONE`. Se guardan `eventId` como `requestEventId`, `requiredField`, `message` y el `expiresAt` informado, y el proceso pasa a `WAITING_FOR_PHONE`. El mismo `eventId` es idempotente y un estado terminal (`EXPIRED`/`FAILED`) no se reabre. La escritura y la marca de idempotencia se persisten en una sola transacción y el offset se confirma después del commit (`ack-mode=record`, autocommit desactivado). Los fallos transitorios se reintentan de forma acotada (3 reintentos con 2 s de espera) y luego se registran y se descartan, sin bloquear el resto de los mensajes.

La respuesta tardía de TUR-05 detecta la carrera con `@Version`: si el consumidor ya dejó el proceso en `WAITING_FOR_PHONE`, la confirmación REST devuelve ese estado sin pisar `requestEventId`. La decisión completa está en [ADR: consumo Kafka de la solicitud de teléfono](docs/adr-consumo-kafka.md).

## Intercambio de teléfono y consulta de proceso (TUR-07, TUR-08 y TUR-10)

`GET /api/reservation-processes/{id}` requiere `ROLE_USER` y propiedad local. Devuelve `id`, `status`, profesional/fecha/hora, `expiresAt`, `canSubmitPhone`, `message`, `rejectionReason`, `reservationId`, `confirmedAt`, `deliveryStatus` y `terminalReason`. El ID del path es el **local**, no el identificador externo de cátedra. Un JWT ausente recibe `401`, un proceso ajeno `403` y uno inexistente `404`. Android puede consultar periódicamente este endpoint. No se devuelve el teléfono ni el JWT técnico.

`POST /api/reservation-processes/{id}/phone` recibe `{ "phoneNumber": "+54 (261) 555-1234" }`. Elimina espacios, guiones, paréntesis y puntos y exige `^\+?[0-9]{7,15}$`. Solo permite el envío con una solicitud Kafka recibida (`requestEventId` y `requiredField=PHONE_NUMBER`) y antes de `expiresAt`: un `202 WAITING_FOR_PHONE` REST sin solicitud Kafka **no basta**. Un teléfono inválido devuelve `400`; falta de solicitud, estado incompatible, otro envío en curso o plazo transcurrido devuelven `409` con un `code` estable. El POST Bearer stateless está excluido específicamente de CSRF, igual que los otros POST de la API.

La respuesta `202` significa **persistido para envío**, no publicación ni reserva confirmada. La migración V5 incorpora la outbox `reservation_phone_submission`, el registro `reservation_processed_event` y los datos de confirmación/rechazo del proceso. El proceso y su evento pendiente se guardan en la misma transacción PostgreSQL. Un publicador recupera envíos pendientes, confirma Kafka con `acks=all` y productor idempotente, y reutiliza el mismo `eventId`, número normalizado e instante original ante un resultado incierto. Las consultas SQL de leasing y `SKIP LOCKED` están encapsuladas en el adaptador de persistencia; el dominio y los casos de uso no dependen de JDBC.

Estados: `WAITING_FOR_PHONE → PHONE_SUBMISSION_PENDING → PHONE_SUBMITTED → CONFIRMED`; un rechazo vuelve a `WAITING_FOR_PHONE`. Una confirmación adelantada al acuse de publicación no retrocede. Cada ciclo toma hasta diez mensajes, con lease de 60 segundos y hasta cinco intentos con backoff de 2/4/8/16 segundos. Agotados los intentos, `deliveryStatus=RETRY_EXHAUSTED`; el propietario puede repetir el POST con el mismo teléfono para reactivar **el mismo evento**, siempre dentro del plazo. Repetir el POST mientras está pendiente/publicado no crea otro envío; intentar cambiar el teléfono antes de un rechazo devuelve `409`. Después de un rechazo, la corrección crea un evento nuevo.

Los pendientes que superan su plazo quedan `ABANDONED`, sin afirmar que cátedra haya expirado el proceso. La expiración/invalidez autoritativa y la reconciliación funcional corresponden a TUR-09/TUR-14. Los registros de outbox conservan el teléfono para el reenvío durable; no se imprimen en logs ni se exponen por HTTP. Esta versión no implementa una política automática de purga de esos registros.

Un único listener despacha el topic de acciones según `eventType`: solicitud, confirmación, rechazo, vencimiento e invalidez. Valida grupo técnico, versión, clave y correlación con proceso/hold/solicitud; para confirmar también profesional/fecha/hora. La deduplicación se persiste junto al efecto en la misma transacción, con offset posterior al commit. V5 inicializa las marcas de las solicitudes vigentes de TUR-06. `AppointmentProcessExpired` cierra como `EXPIRED`; `AppointmentProcessInvalid` cierra como `INVALID`. El motivo autoritativo se guarda como `terminalReason` y se expone en el GET propio. Para `REQUEST_EVENT_MISMATCH`, el `requestEventId` del evento inválido puede diferir deliberadamente del guardado; se correlaciona con proceso, clave y hold. Los resultados terminales abandonan envíos aún no publicados de la outbox, pero no convierten un reloj local en un vencimiento autoritativo. No se reabren procesos `CONFIRMED`, `EXPIRED`, `INVALID` o `FAILED`. Eventos desconocidos o inválidos se descartan sin bloquear la partición. V6 agrega `terminal_reason`. Ver [ADR: outbox de teléfono](docs/adr-outbox-telefono.md) y [ADR: vencimiento e invalidez](docs/adr-vencimiento-invalidez.md).

Configurar en `.env`:

| Variable | Valor de integración |
| --- | --- |
| `TURNOS_KAFKA_PHONE_TOPIC` | `integration.kafkaAppointmentPhoneTopic` |
| `TURNOS_INTEGRATION_GROUP_ID` | `integration.groupId`, **no** el consumer group |
| `TURNOS_KAFKA_ACTIONS_TOPIC` | `integration.kafkaAppointmentActionsTopic` |
| `TURNOS_KAFKA_ENABLED` | `true` para consumir/publicar |
| `TURNOS_OUTBOX_INTERVAL` | Opcional, intervalo entre ciclos en milisegundos; por defecto `2000` |

Sin grupo o topic de salida configurados no se aceptan nuevos envíos (`503 INTEGRATION_UNAVAILABLE`). Los tests usan PostgreSQL Testcontainers y Kafka embebido, sin credenciales reales; cubren el contrato saliente, confirmación/rechazo, aislamiento HTTP, concurrencia, rollback, duplicados, backoff agotado y recuperación de lease tras caída.

## Docker Compose

El backend y su PostgreSQL se ejecutan con `compose.yaml`. La aplicación participa de la red compartida `turnos-integracion` y resuelve el catálogo como `catalogo-api`; su base queda solo en la red privada `turnos-private`.

1. Crear una sola vez la red compartida:

```bash
docker network create --driver bridge --subnet 172.29.0.0/16 turnos-integracion
```

2. Copiar `.env.example` a `.env` y completar, como mínimo:
   - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: base propia de turnos.
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/turnos`.
   - `TURNOS_CATALOG_BASE_URL=http://catalogo-api:8080`.
   - `TURNOS_KAFKA_*`, `CATEDRA_*` y `TURNOS_USER_JWT_SECRET` con los valores vigentes de integración.

3. Levantar el servicio:

```bash
docker compose up -d --build
```

4. Verificar:

```bash
curl http://localhost:8080/actuator/health
docker compose logs -f turnos
```

La aplicación queda en `127.0.0.1:8080` y la base en `127.0.0.1:5434` (este último solo para inspección). Kafka, Redis y cátedra son externos y deben ser alcanzables desde el contenedor, no solo desde el host. Para detener sin borrar datos: `docker compose down`; `docker compose down -v` también elimina el volumen `turnos_db`.

Las pruebas requieren Docker para iniciar PostgreSQL con Testcontainers: `./mvnw --batch-mode --no-transfer-progress verify`. Esta fase genera `target/site/jacoco/index.html` y `target/site/jacoco/jacoco.xml` con la cobertura JaCoCo. El workflow `.github/workflows/backend-ci.yml` ejecuta `verify` en Java 25, comprueba el XML y lo importa en SonarCloud; requiere el secreto de GitHub Actions `SONAR_TOKEN` y las variables `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`. En SonarCloud se desactiva Automatic Analysis para usar el análisis desde CI. Los informes Surefire y JaCoCo también quedan disponibles como artefactos del workflow.
