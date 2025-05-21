package io.github.delanoflipse.fit.suite.instrument.services;

import java.util.List;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;

import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;
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
        boolean useHosted = Env.getEnvBool(Env.Keys.USE_REMOTE);
        if (useHosted) {
            IMAGE = Env.getEnv(Env.Keys.PROXY_IMAGE);
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
                .withEnv("LOG_LEVEL", Env.getEnv(Env.Keys.LOG_LEVEL))
                .withNetwork(app.network)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-proxy-" + randomId))
                .withNetworkAliases(hostname);

        service.setNetwork(app.network);
        service.setNetworkAliases(List.of(this.serviceHostname));
        service.withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-original-" + randomId));
    }

    public FaultInjectionPoint getPoint(String signature, String payload, Map<String, Integer> callStack, int count) {
        return new FaultInjectionPoint(hostname, signature, payload, callStack, count);
    }

    public FaultInjectionPoint getPoint() {
        return new FaultInjectionPoint(hostname, "*", "*", Map.of(), 0);
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