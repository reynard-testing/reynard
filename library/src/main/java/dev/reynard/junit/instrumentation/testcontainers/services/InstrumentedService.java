package dev.reynard.junit.instrumentation.testcontainers.services;

import java.util.List;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;

import dev.reynard.junit.faultload.FaultInjectionPoint;
import dev.reynard.junit.instrumentation.testcontainers.InstrumentedApp;
import dev.reynard.junit.strategy.util.Env;

public class InstrumentedService extends GenericContainer<InstrumentedService> {
    public static final String IMAGE;
    private final GenericContainer<?> service;
    private final String hostname;
    private final String serviceHostname;
    private final int port;
    private final int randomId;

    static {
        IMAGE = Env.getEnv(Env.Keys.PROXY_IMAGE);
    }

    public InstrumentedService(GenericContainer<?> service, String hostname, int port, InstrumentedApp app) {
        super(IMAGE);

        this.randomId = InstrumentedApp.getRandomId();
        this.hostname = hostname;
        this.serviceHostname = hostname + "-instrumented";
        this.port = port;
        this.service = service;

        this.dependsOn(service)
                .withEnv("PROXY_HOST", "0.0.0.0:" + port)
                .withEnv("PROXY_TARGET", "http://" + this.serviceHostname + ":" + port)
                .withEnv("CONTROLLER_HOST", app.controllerHost + ":" + app.controllerPort)
                .withEnv("SERVICE_NAME", hostname)
                .withEnv("LOG_LEVEL", Env.getEnv(Env.Keys.LOG_LEVEL))
                .withNetwork(app.network)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-proxy-" + randomId))
                .withNetworkAliases(hostname);

        service.setNetwork(app.network);
        service.setNetworkAliases(List.of(this.serviceHostname));
        service.withCreateContainerCmdModifier(cmd -> cmd.withName(hostname + "-original-" + randomId));
    }

    public FaultInjectionPoint getPoint(String signature, String payload, Map<String, Integer> predecessors,
            int count) {
        return new FaultInjectionPoint(hostname, signature, payload, predecessors, count);
    }

    public FaultInjectionPoint getPoint() {
        return FaultInjectionPoint.Any()
                .withDestination(hostname);
    }

    public String getHostname() {
        return hostname;
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