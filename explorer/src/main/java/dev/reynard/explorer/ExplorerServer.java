package dev.reynard.explorer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.reynard.junit.strategy.StrategyStatistics;
import dev.reynard.junit.strategy.TrackedFaultload;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;

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

    private final int port;
    private final Javalin app;
    private final Function<ExplorerConfig, Explorer> explorers;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

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

    public ExplorerServer(int port, Function<ExplorerConfig, Explorer> explorers) {
        this.port = port;
        this.explorers = explorers;
        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // A plain mapper rejects unknown config options
            config.jsonMapper(new JavalinJackson(new ObjectMapper(), false));
        });

        app.post("/v1/sessions", this::create);
        app.post("/v1/sessions/{id}/next", this::next);
        app.post("/v1/sessions/{id}/runs/{run_id}", this::finish);
        app.get("/v1/sessions/{id}/report", this::report);

        app.exception(JsonProcessingException.class,
                (e, ctx) -> ctx.status(400).json(Map.of("error", e.getOriginalMessage())));
    }

    public void start() {
        app.start(port);
        logger.info("Explorer listening on port {}", getPort());
    }

    public void stop() {
        app.stop();
    }

    public int getPort() {
        return app.port();
    }

    private Session session(Context ctx) {
        Session session = sessions.get(ctx.pathParam("id"));
        if (session == null) {
            throw new NotFoundResponse("Unknown session");
        }
        return session;
    }

    private void create(Context ctx) {
        ExplorerConfig config = ctx.body().isBlank() ? new ExplorerConfig() : ctx.bodyAsClass(ExplorerConfig.class);
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(config, explorers.apply(config)));
        logger.info("Started session {} for {}", id, config.name);
        ctx.json(Map.of("session_id", id));
    }

    private void next(Context ctx) {
        Session session = session(ctx);
        synchronized (session) {
            if (session.done) {
                ctx.status(204);
                return;
            }

            TrackedFaultload faultload = session.explorer.next();
            if (faultload == null) {
                session.done = true;
                session.explorer.close();
                ctx.status(204);
                return;
            }

            session.explorer.start(faultload);
            session.runs.put(faultload.getTraceId(), faultload);

            ctx.json(Map.of(
                    "run_id", faultload.getTraceId(),
                    "headers", Map.of(
                            "traceparent", faultload.getTraceParent().toString(),
                            "tracestate", faultload.getTraceState().toString())));
        }
    }

    private void finish(Context ctx) {
        Session session = session(ctx);
        Verdict verdict = ctx.bodyAsClass(Verdict.class);
        String runId = ctx.pathParam("run_id");

        synchronized (session) {
            TrackedFaultload faultload = session.runs.remove(runId);
            if (faultload == null) {
                throw new NotFoundResponse("Unknown run");
            }

            if (verdict.message() != null) {
                logger.info("Run {}: {}", runId, verdict.message());
            }
            session.explorer.finish(faultload, verdict.passed());
            ctx.json(Map.of());
        }
    }

    private void report(Context ctx) {
        Session session = session(ctx);
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
            ctx.json(report);
        }
    }
}
