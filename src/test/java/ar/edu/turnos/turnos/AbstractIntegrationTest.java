package ar.edu.turnos.turnos;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@TestPropertySource(properties = {
        "turnos.catalog.base-url=http://localhost:1",
        "turnos.catedra.base-url=http://localhost:1",
        "turnos.catedra.technical-jwt=test-only-technical-jwt",
        "turnos.recovery.enabled=false"
})
abstract class AbstractIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnos")
            .withUsername("turnos")
            .withPassword("turnos");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("turnos.security.jwt-secret", () -> "test-only-signing-key-for-integration-tests-0123456789");
    }
}
