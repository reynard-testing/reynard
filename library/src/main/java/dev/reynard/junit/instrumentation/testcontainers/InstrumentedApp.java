package dev.reynard.junit.instrumentation.testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.instrumentation.testcontainers.services.ControllerService;
import dev.reynard.junit.instrumentation.testcontainers.services.InstrumentedService;
import dev.reynard.junit.instrumentation.testcontainers.services.Jaeger;
import dev.reynard.junit.strategy.util.Env;

public class InstrumentedApp extends RemoteController {
    public Network network;
    private final List<InstrumentedService> proxies = new ArrayList<>();
    private final List<GenericContainer<?>> services = new ArrayList<>();

    public String controllerHost = "fit-controller";
    public int controllerPort = 5000;
    public ControllerService controller;
    public String controllerInspectUrl;

    public Jaeger jaeger = null;
    public String jaegerHost = "fit-jaeger";
    public int jaegerPort = 16686;
    public String jaegerInspectUrl;

    @SuppressWarnings("resource")
    public InstrumentedApp() {
        this.network = Network.newNetwork();

        this.controller = new ControllerService()
                .withNetwork(network)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(controllerHost + "-" + getRandomId()))
                .withEnv("OTEL_SDK_DISABLED", "true")
                .withNetworkAliases(controllerHost)
                .withExposedPorts(controllerPort);
        this.services.add(controller);
    }

    private static final Random random = new Random();

    public static int getRandomId() {
        // 4 digit random number
        // 0-9999
        return random.nextInt() % (9999 + 1);
    }

    @SuppressWarnings("resource")
    public InstrumentedApp withJaeger() {
        jaeger = new Jaeger()
                .withNetwork(network)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(jaegerHost + "-" + getRandomId()))
                .withNetworkAliases(jaegerHost)
                .withExposedPorts(jaegerPort);
        this.services.add(jaeger);
        return this;
    }

    public void addService(String serviceName, GenericContainer<?> service) {
        service.withNetwork(network)
                .withNetworkAliases(serviceName);
        this.services.add(service);
    }

    public InstrumentedService instrument(String hostname, int port, GenericContainer<?> service) {
        var newProxy = new InstrumentedService(service, hostname, port, this);
        this.proxies.add(newProxy);
        controller.withEnv("PROXY_LIST", String.join(",", getProxyList()));

        if (jaeger != null) {
            // Add OTEL environment variables to the instrumented service
            service.withEnv("OTEL_TRACES_EXPORTER", "otlp");
            service.withEnv("OTEL_LOGS_EXPORTER", "none");
            service.withEnv("OTEL_METRICS_EXPORTER", "none");
            service.withEnv("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc");
            service.withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://" + jaegerHost + ":4317");
            service.withEnv("OTEL_SERVICE_NAME", hostname);
        }

        return newProxy;
    }

    public boolean allRunning() {
        for (var proxy : proxies) {
            if (!proxy.getService().isRunning()) {
                return false;
            }
        }

        return true;
    }

    private List<String> getProxyList() {
        List<String> proxyList = new ArrayList<>();

        for (var proxy : proxies) {
            proxyList.add(proxy.getControlHost());
        }

        return proxyList;
    }

    public void start() {
        for (var service : services) {
            service.start();
        }

        int localControllerPort = controller.getMappedPort(5000);
        String networkHost = Env.getEnv(Env.Keys.LOCAL_HOST);
        controllerInspectUrl = "http://" + networkHost + ":" + localControllerPort;

        if (jaeger != null) {
            int localJaegerPort = jaeger.getMappedPort(16686);
            jaegerInspectUrl = "http://" + networkHost + ":" + localJaegerPort;
        }

        this.apiHost = controllerInspectUrl;
    }

    public void stop() {
        for (var service : services) {
            service.stop();
        }
    }
}
