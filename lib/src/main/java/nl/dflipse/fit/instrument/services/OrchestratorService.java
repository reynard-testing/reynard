package nl.dflipse.fit.instrument.services;

import org.testcontainers.containers.GenericContainer;

public class OrchestratorService extends GenericContainer<OrchestratorService> {
    private static final String IMAGE = "fit-otel-orchestrator:latest";

    public OrchestratorService() {
        super(IMAGE);

        this
                .withCommand("flask --app orchestrator.py run --host=0.0.0.0");
        // .withNetwork(network)
        // .withNetworkAliases(name);
    }
}
