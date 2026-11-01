package dev.reynard.explorer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.reynard.junit.benchmark.StrategyBenchmark;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.store.ImplicationsStore;
import dev.reynard.junit.strategy.util.TraceAnalysis;

public class ExplorerServerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    /** Answers traces from a simulated system, and records which faultloads were run */
    static class SimulatedController implements FaultController {
        final ImplicationsStore system = StrategyBenchmark.system(3, 3);
        final List<String> registered = new ArrayList<>();

        @Override
        public TraceAnalysis getTrace(TrackedFaultload faultload) {
            return StrategyBenchmark.run(faultload.getFaultload(), system);
        }

        @Override
        public void registerFaultload(TrackedFaultload faultload) {
            registered.add(new TreeSet<>(faultload.getFaultload().faultSet().stream()
                    .map(Object::toString).toList()).toString());
        }

        @Override
        public void unregisterFaultload(TrackedFaultload faultload) {
        }

        @Override
        public void withFaultload(TrackedFaultload faultload, Callable<Void> runnable) throws Exception {
            registerFaultload(faultload);
            runnable.call();
        }
    }

    private HttpResponse<String> post(String url, Object body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testHttpExplorationMatchesInProcess() throws Exception {
        ExplorerConfig config = new ExplorerConfig();
        config.maxTestCases = 200;

        // In-process, as @FiTest does
        SimulatedController inProcess = new SimulatedController();
        Explorer explorer = new Explorer(config, inProcess);
        for (TrackedFaultload f = explorer.next(); f != null; f = explorer.next()) {
            explorer.start(f);
            explorer.finish(f, true);
        }

        // Over HTTP, as a client in another language would
        SimulatedController overHttp = new SimulatedController();
        ExplorerServer server = new ExplorerServer(0, c -> new Explorer(c, overHttp));
        server.start();
        String base = "http://localhost:" + server.getPort() + "/v1/sessions";
        try {
            String session = mapper.readTree(post(base, Map.of("name", "simulated", "maxTestCases", 200)).body())
                    .get("session_id").asText();

            while (true) {
                var next = post(base + "/" + session + "/next", Map.of());
                if (next.statusCode() == 204) {
                    break;
                }
                assertEquals(200, next.statusCode());
                var run = mapper.readTree(next.body());
                assertTrue(run.get("headers").get("tracestate").asText().contains("fit=1"));
                var verdict = post(base + "/" + session + "/runs/" + run.get("run_id").asText(),
                        Map.of("passed", true));
                assertEquals(200, verdict.statusCode());
            }

            var report = mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/" + session + "/report"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            assertTrue(report.get("done").asBoolean());
            assertEquals(overHttp.registered.size(), report.get("runs").asInt());
        } finally {
            server.stop();
        }

        assertTrue(inProcess.registered.size() > 1, "the simulated system should be explored");
        assertEquals(inProcess.registered, overHttp.registered);
    }

    @Test
    public void testErrors() throws Exception {
        ExplorerServer server = new ExplorerServer(0, c -> new Explorer(c, new SimulatedController()));
        server.start();
        String base = "http://localhost:" + server.getPort() + "/v1/sessions";
        try {
            assertEquals(400, post(base, Map.of("unknownOption", true)).statusCode());
            assertEquals(404, post(base + "/missing/next", Map.of()).statusCode());

            String session = mapper.readTree(post(base, Map.of()).body()).get("session_id").asText();
            assertEquals(404, post(base + "/" + session + "/runs/missing", Map.of("passed", true)).statusCode());
        } finally {
            server.stop();
        }
    }
}
