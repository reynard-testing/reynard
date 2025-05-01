package io.github.delanoflipse.fit.suite.instrument.services;

import org.testcontainers.containers.GenericContainer;

public class OrchestratorService extends GenericContainer<OrchestratorService> {
    private static final String IMAGE = "fit-orchestrator:latest";

    public OrchestratorService() {
        super(IMAGE);

        // .withNetwork(network)
        // .withNetworkAliases(name);
    }
}
