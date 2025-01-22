package nl.dflipse.fit.instrument;

import org.testcontainers.containers.GenericContainer;

public class ProxyService implements InstrumentedService {
    public GenericContainer<?> service;
    public GenericContainer<?> proxy;
    private String name;
    private String serviceHost;

    private static String image = "fit-proxy:latest";

    public ProxyService(String name, GenericContainer<?> service, int port, InstrumentedApp app) {
        this.name = name;
        this.service = service;

        this.serviceHost = name + "-instrumented";
        this.proxy = new GenericContainer<>(image)
                .dependsOn(service)
                .withEnv("PROXY_HOST", "0.0.0.0:" + port)
                .withEnv("PROXY_TARGET", "http://" + this.serviceHost + ":" + port)
                .withEnv("COLLECTOR_HOST", app.collectorHost + ":" + app.collectorPort)
                .withEnv("SERVICE_NAME", name)
                .withNetwork(app.network)
                .withNetworkAliases(name);

        service.withNetwork(app.network);
        service.withNetworkAliases(this.serviceHost);
    }

    public ProxyService withHttp2() {
        proxy.withEnv("USE_HTTP2", "true");
        return this;
    }

    public boolean isRunning() {
        return service.isRunning();
    }

    public GenericContainer<?> getContainer() {
        return proxy;
    }

    public String getName() {
        return name;
    }

    public void start() {
        service.start();
        proxy.start();
    }

    public void stop() {
        if (proxy != null && proxy.isRunning()) {
            proxy.stop();
        }

        if (service != null && service.isRunning()) {
            service.stop();
        }
    }
}
