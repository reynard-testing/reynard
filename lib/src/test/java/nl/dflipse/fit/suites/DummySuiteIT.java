package io.github.delanoflipse.fit.suites;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import io.github.delanoflipse.fit.instrument.FaultController;
import io.github.delanoflipse.fit.instrument.InstrumentedApp;

public class DummySuiteIT {
    public static InstrumentedApp app;

    public static FaultController getController() {
        return app;
    }

    @BeforeAll
    static public void setupServices() {
        app = new InstrumentedApp();
        app.start();
    }

    @AfterAll
    static public void teardownServices() {
        app.stop();
    }

    // @FiTest
    // public void testApp(Faultload faultload) throws IOException {
    // assertTrue(true);
    // }
    @Test
    public void testNothing() {
        assertTrue(true);
    }
}
