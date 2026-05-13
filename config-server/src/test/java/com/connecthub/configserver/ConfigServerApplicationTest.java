package com.connecthub.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the config-server Spring context loads successfully.
 * Uses @ActiveProfiles("test") to disable Eureka registration and Git clone
 * during test runs (test profile overrides git.uri with a local stub).
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigServerApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the application context starts without error
    }
}
