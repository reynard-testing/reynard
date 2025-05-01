package io.github.delanoflipse.fit.suite.instrument;

import java.util.ArrayList;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import io.github.delanoflipse.fit.suite.instrument.controller.RemoteController;
import io.github.delanoflipse.fit.suite.instrument.services.InstrumentedService;
import io.github.delanoflipse.fit.suite.instrument.services.Jaeger;
import io.github.delanoflipse.fit.suite.instrument.services.OrchestratorService;

public class InstrumentedApp extends RemoteController {
    public Network network;
    private final List<InstrumentedService> proxies = new ArrayList<>();
    private final List<GenericContainer<?>> services = new ArrayList<>();

    public String orchestratorHost = "orchestrator";
    public int orchestratorPort = 5000;
    public OrchestratorService orchestrator;
    public String orchestratorInspectUrl;

    public Jaeger jaeger = null;
    public String jaegerHost = "jaeger";
    public int jaegerPort = 16686;

    @SuppressWarnings("resource")
    public InstrumentedApp() {
        this.network = Network.newNetwork();

        this.orchestrator = new OrchestratorService()
                .withNetwork(network)
                .withNetworkAliases(orchestratorHost)
                .withExposedPorts(orchestratorPort);
        this.services.add(orchestrator);
    }

    public InstrumentedApp withJaeger() {
        jaeger = new Jaeger()
                .withNetwork(network)
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
        orchestrator.withEnv("PROXY_LIST", String.join(",", getProxyList()));
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

        int localOrchestratorPort = orchestrator.getMappedPort(5000);
        orchestratorInspectUrl = "http://localhost:" + localOrchestratorPort;
        this.apiHost = orchestratorInspectUrl;
    }

    public void stop() {
        for (var service : services) {
            service.stop();
        }
    }
}
