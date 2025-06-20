package dev.reynard.junit.instrumentation.testcontainers.services;

import org.testcontainers.containers.GenericContainer;

import dev.reynard.junit.strategy.util.Env;

public class ControllerService extends GenericContainer<ControllerService> {
    public static final String IMAGE;

    static {
        boolean useHosted = Env.getEnvBool(Env.Keys.USE_REMOTE);
        if (useHosted) {
            IMAGE = Env.getEnv(Env.Keys.CONTROLLER_IMAGE);
        } else {
            IMAGE = "fit-controller:latest";
        }
    }

    public ControllerService() {
        super(IMAGE);
        withEnv("LOG_LEVEL", Env.getEnv(Env.Keys.LOG_LEVEL));

        // .withNetwork(network)
        // .withNetworkAliases(name);
    }
}
