package dev.reynard.junit.integration;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.instrumentation.testcontainers.InstrumentedApp;

/**
 * A dummy test suite that does nothing.
 * This is used to ensure that the test infrastructure is working correctly.
 * And can be used as a template for new testcontainer-based suites.
 */
public class DummySuiteIT {
    public static InstrumentedApp app;

    public static FaultController getController() {
        return app;
    }

    @BeforeAll
    static void setupServices() {
        app = new InstrumentedApp();
        app.start();
    }

    @AfterAll
    static void teardownServices() {
        app.stop();
    }

    @Test
    public void testNothing() {
        assertTrue(true);
    }
}
