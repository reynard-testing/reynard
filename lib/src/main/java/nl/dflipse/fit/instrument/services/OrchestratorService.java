package nl.dflipse.fit.instrument.services;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class OrchestratorService implements InstrumentedService {
    private GenericContainer<?> container;
    private String name;

    private static String image = "fit-otel-orchestrator:latest";

    public OrchestratorService(String name, Network network) {
        this.name = name;

        this.container = new GenericContainer<>(image)
                .withCommand("flask --app orchestrator.py run --host=0.0.0.0")
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
