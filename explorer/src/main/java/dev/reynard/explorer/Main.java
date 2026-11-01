package dev.reynard.explorer;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;

/** Command line entrypoint of the explorer. */
public class Main {
    private static final String USAGE = """
            Usage:
              reynard serve   --controller <url> [--port <port>]
                  Serve the explorer HTTP API (default port 7070).

              reynard explore --controller <url> [--config <json>] -- <command> [args...]
                  Run <command> once per faultload. The command must send the headers in
                  REYNARD_TRACEPARENT and REYNARD_TRACESTATE with the request under test,
                  and exit with 0 if the invariant holds.

              --config takes the @FiTest options as JSON, e.g. '{"name": "checkout", "maxTestCases": 100}'.
            """;

    public static void main(String[] args) throws Exception {
        int separator = Arrays.asList(args).indexOf("--");
        List<String> options = Arrays.asList(args).subList(0, separator < 0 ? args.length : separator);
        List<String> command = separator < 0 ? List.of() : Arrays.asList(args).subList(separator + 1, args.length);

        String controller = option(options, "--controller", null);
        if (options.isEmpty() || controller == null) {
            exit(USAGE);
        }

        switch (options.get(0)) {
            case "serve" -> {
                int port = Integer.parseInt(option(options, "--port", "7070"));
                new ExplorerServer(port, config -> new Explorer(config, new RemoteController(controller))).start();
            }
            case "explore" -> {
                if (command.isEmpty()) {
                    exit(USAGE);
                }
                ExplorerConfig config = new ObjectMapper().readValue(option(options, "--config", "{}"),
                        ExplorerConfig.class);
                if (config.name.isEmpty()) {
                    config.name = String.join(" ", command);
                }
                System.exit(explore(new Explorer(config, new RemoteController(controller)), command));
            }
            default -> exit(USAGE);
        }
    }

    /** Runs the command for every faultload, returns 1 if an invariant was violated */
    static int explore(Explorer explorer, List<String> command) throws Exception {
        while (true) {
            TrackedFaultload faultload = explorer.next();
            if (faultload == null) {
                break;
            }

            explorer.start(faultload);
            ProcessBuilder process = new ProcessBuilder(command).inheritIO();
            process.environment().put("REYNARD_TRACEPARENT", faultload.getTraceParent().toString());
            process.environment().put("REYNARD_TRACESTATE", faultload.getTraceState().toString());
            int exitCode = process.start().waitFor();
            explorer.finish(faultload, exitCode == 0);
        }
        explorer.close();

        var statistics = explorer.getStrategy().statistics;
        System.out.printf("Explored %d faultloads, %d violated the invariant%n", statistics.getTotalRun(),
                statistics.getFailures().size());
        for (var failure : statistics.getFailures()) {
            System.out.println("  " + failure.trackedFaultload.getFaultload().readableString());
        }
        return statistics.getFailures().isEmpty() ? 0 : 1;
    }

    private static String option(List<String> options, String name, String fallback) {
        int index = options.indexOf(name);
        return index >= 0 && index + 1 < options.size() ? options.get(index + 1) : fallback;
    }

    private static void exit(String message) {
        System.err.print(message);
        System.exit(2);
    }
}
