<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-35%2F100-orange) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-no%20compila-red) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-0.0%25-green) ![%POO](https://img.shields.io/badge/%25POO-56%25-orange) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-58-orange) ![D2](https://img.shields.io/badge/D2-36-red) ![D4](https://img.shields.io/badge/D4-0-red) ![D5](https://img.shields.io/badge/D5-95-green) ![D6](https://img.shields.io/badge/D6-2-red) ![D7](https://img.shields.io/badge/D7-31-red)

**Calidad (heurístico, 7 dimensiones):** índice **35/100** · build **no compila** · aprobar **no (6/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# backend-turnos

## Registro e inicio de sesión (TUR-01 y TUR-02)

El servicio usa PostgreSQL y aplica la migración Flyway al iniciarse. Configurar `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` para su base de datos propia (valores locales predeterminados: `jdbc:postgresql://localhost:5433/turnos`, `turnos`, `turnos`). Configurar obligatoriamente `TURNOS_USER_JWT_SECRET` con **al menos 32 bytes UTF-8**; ambos backends deben recibir la **misma** clave (`CATALOG_USER_JWT_SECRET` en catálogo). No publicar la clave en Git ni entregarla a la app Android.

`POST /api/register` recibe `login`, `password`, `firstName`, `lastName`, `email`, `imageUrl` opcional y `langKey` opcional. Registra al usuario activo con `ROLE_USER` y almacena únicamente su hash Argon2id; la respuesta `201` no contiene contraseña ni hash. `POST /api/authenticate` recibe `username` (login), `password` y `rememberMe` (opcional, por defecto `false`). Si las credenciales son válidas responde `200` con `{ "id_token": "<jwt>" }` y la cabecera `Authorization: Bearer <jwt>`; las credenciales inválidas responden `401` sin distinguir usuario de contraseña. El registro y el login son públicos; los demás endpoints permanecen cerrados hasta habilitar sus casos de uso.

El JWT propio de usuario se firma con HS256, `iss=backend-turnos`, `sub=user-{id}`, audiencias `backend-turnos` y `backend-catalogo`, y el claim `authorities` con `ROLE_USER`. La duración normal es **1 hora** y con `rememberMe` es **7 días**; se pueden cambiar mediante `TURNOS_USER_JWT_TTL` (por defecto `PT1H`) y `TURNOS_USER_JWT_REMEMBER_ME_TTL` (por defecto `P7D`). `TURNOS_USER_JWT_ISSUER` permite cambiar el emisor (por defecto `backend-turnos`), siempre coordinado con catálogo. No se emiten tokens de renovación: al vencer la sesión se requiere un nuevo login. El JWT técnico de cátedra es distinto y nunca se envía al usuario.

Las pruebas requieren Docker para iniciar PostgreSQL con Testcontainers: `./mvnw --batch-mode --no-transfer-progress verify`. Esta fase genera `target/site/jacoco/index.html` y `target/site/jacoco/jacoco.xml` con la cobertura JaCoCo. El workflow `.github/workflows/backend-ci.yml` ejecuta `verify` en Java 25, comprueba el XML y lo importa en SonarCloud; requiere el secreto de GitHub Actions `SONAR_TOKEN` y las variables `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`. En SonarCloud se desactiva Automatic Analysis para usar el análisis desde CI. Los informes Surefire y JaCoCo también quedan disponibles como artefactos del workflow.
