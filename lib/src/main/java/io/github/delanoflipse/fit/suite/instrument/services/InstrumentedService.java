package io.github.delanoflipse.fit.suite.instrument.services;

import java.util.List;

import org.testcontainers.containers.GenericContainer;

import io.github.delanoflipse.fit.suite.instrument.InstrumentedApp;
import io.github.delanoflipse.fit.suite.strategy.util.Env;

public class InstrumentedService extends GenericContainer<InstrumentedService> {
    public static final String IMAGE;
    private final GenericContainer<?> service;
    private final String hostname;
    private final String serviceHostname;
    private final int port;
    private final int controlPort;
    private final int randomId;

    static {
        boolean useHosted = Env.getEnv("USE_REMOTE", "0").equals("1");
        if (useHosted) {
            IMAGE = Env.getEnv("PROXY_MAGE", "dflipse/ds-fit-proxy:latest");
        } else {
            IMAGE = "fit-proxy:latest";
        }
    }

    public InstrumentedService(GenericContainer<?> service, String hostname, int port, InstrumentedApp app) {
        super(IMAGE);

        this.randomId = InstrumentedApp.getRandomId();
        this.hostname = hostname;
        this.serviceHostname = hostname + "-instrumented";
        this.port = port;
        this.controlPort = port + 1;
        this.service = service;

        this.dependsOn(service)
                .dependsOn(service)
                .withEnv("PROXY_HOST", "0.0.0.0:" + port)
                .withEnv("PROXY_TARGET", "http://" + this.serviceHostname + ":" + port)
                .withEnv("CONTROLLER_HOST", app.controllerHost + ":" + app.controllerPort)
                .withEnv("SERVICE_NAME", hostname)
                .withEnv("CONTROL_PORT", "" + controlPort)
                .withNetwork(app.network)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-proxy-" + randomId))
                .withNetworkAliases(hostname);

        service.setNetwork(app.network);
        service.setNetworkAliases(List.of(this.serviceHostname));
        service.withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-original-" + randomId));
    }

    public InstrumentedService withHttp2() {
        withEnv("USE_HTTP2", "true");
        return this;
    }

    public String getControlHost() {
        // controller port is original port + 1
        return hostname + ":" + this.controlPort;
    }

    public GenericContainer<?> getService() {
        return service;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void start() {
        service.start();
        super.start();
    }

    @Override
    public void stop() {
        service.stop();
        super.stop();
    }

}