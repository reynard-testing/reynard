package io.github.delanoflipse.fit.suite.instrument.services;

import org.testcontainers.containers.GenericContainer;

public class ControllerService extends GenericContainer<ControllerService> {
    private static final String IMAGE = "dflipse/ds-fit-controller:latest";

    public ControllerService() {
        super(IMAGE);

        // .withNetwork(network)
        // .withNetworkAliases(name);
    }
}
