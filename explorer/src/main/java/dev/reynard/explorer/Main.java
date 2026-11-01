package dev.reynard.explorer;

import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.reynard.junit.instrumentation.RemoteController;
import dev.reynard.junit.strategy.TrackedFaultload;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Command line entrypoint of the explorer. */
@Command(name = "reynard", mixinStandardHelpOptions = true, subcommands = { Main.Serve.class, Main.Explore.class })
public class Main {
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    static class Controller {
        @Option(names = "--controller", required = true, paramLabel = "<url>")
        String url;
    }

    @Command(name = "serve", mixinStandardHelpOptions = true, description = "Serve the explorer HTTP API.")
    static class Serve implements Callable<Integer> {
        @Mixin
        Controller controller;

        @Option(names = "--port", defaultValue = "7070", paramLabel = "<port>")
        int port;

        public Integer call() throws InterruptedException {
            new ExplorerServer(port, config -> new Explorer(config, new RemoteController(controller.url))).start();
            // block so main does not exit while Javalin serves
            Thread.currentThread().join();
            return 0;
        }
    }

    @Command(name = "explore", mixinStandardHelpOptions = true, header = "Run <command> once per faultload.", description = {
            "Run <command> once per faultload. The command must send the headers in",
            "REYNARD_TRACEPARENT and REYNARD_TRACESTATE with the request under test,",
            "and exit with 0 if the invariant holds." })
    static class Explore implements Callable<Integer> {
        @Mixin
        Controller controller;

        @Option(names = "--config", defaultValue = "{}", paramLabel = "<json>", description = "The @FiTest options as JSON, e.g. '{\"name\": \"checkout\", \"maxTestCases\": 100}'.")
        String config;

        @Parameters(arity = "1..*", paramLabel = "<command>")
        List<String> command;

        public Integer call() throws Exception {
            ExplorerConfig explorerConfig = new ObjectMapper().readValue(config, ExplorerConfig.class);
            if (explorerConfig.name.isEmpty()) {
                explorerConfig.name = String.join(" ", command);
            }
            return explore(new Explorer(explorerConfig, new RemoteController(controller.url)), command);
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
}
