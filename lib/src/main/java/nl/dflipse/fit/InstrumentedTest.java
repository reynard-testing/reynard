package nl.dflipse.fit;

import nl.dflipse.fit.instrument.FaultController;

public interface InstrumentedTest {
    FaultController getController();
}
