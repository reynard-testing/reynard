package nl.dflipse.fit.instrument;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class PlainService implements InstrumentedService {
    public GenericContainer<?> container;
    public String name;

    public PlainService(String name, GenericContainer<?> container, Network network) {
        this.name = name;
        this.container = container;

        container.withNetwork(network);
        container.withNetworkAliases(name);
    }

    public GenericContainer<?> getContainer() {
        return container;
    }

    public String getName() {
        return name;
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    public void start() {
        container.start();
    }

    public void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }
}
