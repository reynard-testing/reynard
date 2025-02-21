package nl.dflipse.fit.instrument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.instrument.services.InstrumentedService;
import nl.dflipse.fit.instrument.services.Jaeger;
import nl.dflipse.fit.instrument.services.OTELCollectorService;
import nl.dflipse.fit.instrument.services.OrchestratorService;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class InstrumentedApp {
    public Network network;
    private final List<InstrumentedService> proxies = new ArrayList<>();
    private final List<GenericContainer<?>> services = new ArrayList<>();

    public String collectorHost = "otel-collector";
    public OTELCollectorService collector;

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

        this.collector = new OTELCollectorService()
                .withNetwork(network)
                .withNetworkAliases(collectorHost)
                .dependsOn(orchestrator);
        this.services.add(collector);
    }

    public InstrumentedApp withJaeger() {
        jaeger = new Jaeger()
                .withNetwork(network)
                .withNetworkAliases(jaegerHost)
                .withExposedPorts(jaegerPort);
        this.collector.dependsOn(jaeger)
                .withJeager();
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

    public TraceTreeSpan getTrace(String traceId) throws IOException {
        String queryUrl = orchestratorInspectUrl + "/v1/get-trees/" + traceId;
        Response res = Request.get(queryUrl).execute();
        String body = res.returnContent().asString();
        List<TraceTreeSpan> orchestratorResponse = new ObjectMapper().readValue(body,
                new TypeReference<List<TraceTreeSpan>>() {
                });

        if (orchestratorResponse.size() == 0) {
            throw new IOException("No trace found for traceId: " + traceId);
        }

        if (orchestratorResponse.size() > 1) {
            System.out.println("Expected 1 trace, got " + orchestratorResponse.size());
        }

        var trace = orchestratorResponse.get(0);

        if (trace.isIncomplete()) {
            throw new IOException("Trace is incomplete");
        }

        return trace;
    }

    public void registerFaultload(Faultload faultload) {
        String queryUrl = orchestratorInspectUrl + "/v1/register_faultload";

        try {
            String jsonBody = faultload.serializeJson();

            Response res = Request.post(queryUrl)
                    .bodyString(jsonBody, ContentType.APPLICATION_JSON)
                    .execute();
            res.returnContent().asString(); // Ensure the request is executed
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        for (var service : services) {
            service.start();
        }

        int localOrchestratorPort = orchestrator.getMappedPort(5000);
        orchestratorInspectUrl = "http://localhost:" + localOrchestratorPort;
    }

    public void stop() {
        for (var service : services) {
            service.stop();
        }
    }
}
