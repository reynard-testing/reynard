package nl.dflipse.fit.instrument;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class CollectorService implements InstrumentedService {
    private GenericContainer<?> container;
    private String name;

    private static String image = "fit-otel-collector:latest";

    public CollectorService(String name, Network network) {
        this.name = name;

        this.container = new GenericContainer<>(image)
                .withCommand("flask --app collector.py run --host=0.0.0.0")
                .withExposedPorts(5000)
                .withNetwork(network)
                .withNetworkAliases(name);
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
