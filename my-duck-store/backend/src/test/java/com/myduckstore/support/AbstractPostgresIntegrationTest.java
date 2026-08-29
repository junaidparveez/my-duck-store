package com.myduckstore.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the real database.
 *
 * <p>Several of the warehouse's guarantees are properties of PostgreSQL rather than of Java - the
 * partial unique index, {@code INSERT … ON CONFLICT DO UPDATE}, and the row locking that makes
 * concurrent adds serialise. An in-memory database would not exercise any of them, so these tests
 * run against the same PostgreSQL version as production and let Flyway build the schema.
 *
 * <p>The container is started once for the whole JVM and deliberately never stopped - the
 * "singleton container" pattern. The obvious alternative, JUnit's {@code @Testcontainers} with a
 * static {@code @Container} field, stops the container when the <em>first</em> test class using it
 * finishes, so the second class inherits a dead container and every test fails with
 * "Connection refused". Testcontainers' own reaper removes this one when the JVM exits.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
