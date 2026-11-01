package dev.reynard.explorer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.reynard.junit.strategy.StrategyStatistics;
import dev.reynard.junit.strategy.TrackedFaultload;

/**
 * Exposes explorers over HTTP, so tests in any language can drive an
 * exploration:
 *
 * <pre>
 * POST /v1/sessions                    {config}           -> {session_id}
 * POST /v1/sessions/{id}/next                             -> {run_id, headers}, or 204 when done
 * POST /v1/sessions/{id}/runs/{run_id} {passed, message}  -> 200
 * GET  /v1/sessions/{id}/report                           -> {name, done, runs, lost, failures}
 * </pre>
 */
public class ExplorerServer {
    private static final Logger logger = LoggerFactory.getLogger(ExplorerServer.class);

    private final HttpServer server;
    private final Function<ExplorerConfig, Explorer> explorers;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private static class Session {
        final ExplorerConfig config;
        final Explorer explorer;
        final Map<String, TrackedFaultload> runs = new HashMap<>();
        boolean done = false;

        Session(ExplorerConfig config, Explorer explorer) {
            this.config = config;
            this.explorer = explorer;
        }
    }

    public record Verdict(boolean passed, String message) {
    }

    private record Response(int status, Object body) {
    }

    public ExplorerServer(int port, Function<ExplorerConfig, Explorer> explorers) throws IOException {
        this.explorers = explorers;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/v1/sessions", this::handle);
    }

    public void start() {
        server.start();
        logger.info("Explorer listening on port {}", getPort());
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Response response;
        try {
            response = route(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestBody());
        } catch (JsonProcessingException e) {
            response = new Response(400, Map.of("error", e.getOriginalMessage()));
        } catch (Exception e) {
            logger.error("Failed to handle {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
            response = new Response(500, Map.of("error", String.valueOf(e.getMessage())));
        }

        if (response.body() == null) {
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            byte[] body = mapper.writeValueAsBytes(response.body());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private Response route(String method, String path, InputStream body) throws IOException {
        // ["", "v1", "sessions", {id}, ...]
        String[] parts = path.split("/");

        if (method.equals("POST") && parts.length == 3) {
            return create(body);
        }

        Session session = parts.length > 3 ? sessions.get(parts[3]) : null;
        if (session == null) {
            return new Response(404, Map.of("error", "Unknown session"));
        }

        if (method.equals("POST") && parts.length == 5 && parts[4].equals("next")) {
            return next(session);
        }

        if (method.equals("POST") && parts.length == 6 && parts[4].equals("runs")) {
            return finish(session, parts[5], mapper.readValue(body, Verdict.class));
        }

        if (method.equals("GET") && parts.length == 5 && parts[4].equals("report")) {
            return report(session);
        }

        return new Response(404, Map.of("error", "Unknown endpoint"));
    }

    private Response create(InputStream body) throws IOException {
        byte[] json = body.readAllBytes();
        ExplorerConfig config = json.length == 0 ? new ExplorerConfig() : mapper.readValue(json, ExplorerConfig.class);
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(config, explorers.apply(config)));
        logger.info("Started session {} for {}", id, config.name);
        return new Response(200, Map.of("session_id", id));
    }

    private Response next(Session session) {
        synchronized (session) {
            if (session.done) {
                return new Response(204, null);
            }

            TrackedFaultload faultload = session.explorer.next();
            if (faultload == null) {
                session.done = true;
                session.explorer.close();
                return new Response(204, null);
            }

            session.explorer.start(faultload);
            session.runs.put(faultload.getTraceId(), faultload);

            return new Response(200, Map.of(
                    "run_id", faultload.getTraceId(),
                    "headers", Map.of(
                            "traceparent", faultload.getTraceParent().toString(),
                            "tracestate", faultload.getTraceState().toString())));
        }
    }

    private Response finish(Session session, String runId, Verdict verdict) {
        synchronized (session) {
            TrackedFaultload faultload = session.runs.remove(runId);
            if (faultload == null) {
                return new Response(404, Map.of("error", "Unknown run"));
            }

            if (verdict.message() != null) {
                logger.info("Run {}: {}", runId, verdict.message());
            }
            session.explorer.finish(faultload, verdict.passed());
            return new Response(200, Map.of());
        }
    }

    private Response report(Session session) {
        synchronized (session) {
            StrategyStatistics statistics = session.explorer.getStrategy().statistics;
            List<String> failures = statistics.getFailures().stream()
                    .map(f -> f.trackedFaultload.getFaultload().readableString())
                    .toList();

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("name", session.config.name);
            report.put("done", session.done);
            report.put("runs", statistics.getTotalRun());
            report.put("lost", statistics.getTotalLost());
            report.put("failures", failures);
            return new Response(200, report);
        }
    }
}
