package nl.dflipse.fit.instrument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.dflipse.fit.instrument.services.OrchestratorService;
import nl.dflipse.fit.instrument.services.InstrumentedService;
import nl.dflipse.fit.instrument.services.PlainService;
import nl.dflipse.fit.instrument.services.ProxyService;
import nl.dflipse.fit.trace.data.TraceData;

public class InstrumentedApp {
    public Network network;
    private List<InstrumentedService> services;
    
    public String orchestratorHost = "collector";
    public int orchestratorPort = 5000;
    public OrchestratorService orchestrator;
    public String orchestratorInspectUrl;

    public InstrumentedApp() {
        this.network = Network.newNetwork();
        this.services = new ArrayList<InstrumentedService>();

        this.orchestrator = new OrchestratorService(orchestratorHost, network);

        this.services.add(orchestrator);
    }

    public void addService(String serviceName, GenericContainer<?> service) {
        PlainService plainService = new PlainService(serviceName, service, network);
        this.services.add(plainService);
    }

    public void addService(InstrumentedService service) {
        this.services.add(service);
    }

    public ProxyService addInstrumentedService(String serviceName, GenericContainer<?> service, int port) {
        ProxyService proxyService = new ProxyService(serviceName, service, port, this);
        this.services.add(proxyService);
        return proxyService;
    }

    public InstrumentedService getServiceByName(String serviceName) {
        for (InstrumentedService service : this.services) {
            if (service.getName().equals(serviceName)) {
                return service;
            }
        }

        return null;
    }

    public GenericContainer<?> getContainerByName(String serviceName) {
        for (InstrumentedService service : this.services) {
            if (service.getName().equals(serviceName)) {
                return service.getContainer();
            }
        }

        return null;
    }

    public boolean allRunning() {
        for (InstrumentedService service : this.services) {
            if (!service.isRunning()) {
                return false;
            }
        }

        return true;
    }

    public TraceData getTrace(String traceId) {
        String queryUrl = orchestratorInspectUrl + "/v1/get/" + traceId;
        try {
            Response res = Request.get(queryUrl).execute();
            String body = res.returnContent().asString();
            TraceData collectorResponse = new ObjectMapper().readValue(body, TraceData.class);
            return collectorResponse;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void start() {
        for (InstrumentedService service : this.services) {
            try {
                service.start();
            } catch (Exception e) {
                System.err.println("Failed to start container: " + service.getName());
                e.printStackTrace();
            }
        }

        int collectorPort = orchestrator.getContainer().getMappedPort(5000);
        orchestratorInspectUrl = "http://localhost:" + collectorPort;
    }

    public void stop() {
        for (InstrumentedService service : this.services) {
            service.stop();
        }
    }
}
