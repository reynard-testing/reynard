package nl.dflipse.fit;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;

import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.strategy.Faultload;

public class DummyTest implements InstrumentedTest {
    public static InstrumentedApp app;

    @Override
    public InstrumentedApp getApp() {
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

    @FiTest
    public void testApp(Faultload faultload) throws IOException {
        assertTrue(true);
    }
}
