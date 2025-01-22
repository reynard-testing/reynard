package nl.dflipse.fit.instrument;

import org.testcontainers.containers.GenericContainer;

public interface InstrumentedService {
    public GenericContainer<?> getContainer();

    public String getName();

    public boolean isRunning();

    public void start();

    public void stop();
}
