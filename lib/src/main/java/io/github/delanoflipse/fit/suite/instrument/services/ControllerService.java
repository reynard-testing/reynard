package io.github.delanoflipse.fit.suite.instrument.services;

import org.testcontainers.containers.GenericContainer;

import io.github.delanoflipse.fit.suite.strategy.util.Env;

public class ControllerService extends GenericContainer<ControllerService> {
    public static final String IMAGE;

    static {
        boolean useHosted = Env.getEnv("USE_REMOTE", "0").equals("1");
        if (useHosted) {
            IMAGE = Env.getEnv("CONTROLLER_IMAGE", "dflipse/ds-fit-controller:latest");
        } else {
            IMAGE = "fit-controller:latest";
        }
    }

    public ControllerService() {
        super(IMAGE);

        // .withNetwork(network)
        // .withNetworkAliases(name);
    }
}
