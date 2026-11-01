package dev.reynard.explorer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.faultload.modes.HttpError;
import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.StrategyRunner;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.components.analyzers.BehaviorAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.ConcurrencyDetector;
import dev.reynard.junit.strategy.components.analyzers.ConditionalPointDetector;
import dev.reynard.junit.strategy.components.analyzers.ErrorPropagationDetector;
import dev.reynard.junit.strategy.components.analyzers.FaultsWithoutCauseOracle;
import dev.reynard.junit.strategy.components.analyzers.HappensBeforeNeighbourDetector;
import dev.reynard.junit.strategy.components.analyzers.HappyPathDetector;
import dev.reynard.junit.strategy.components.analyzers.ParentChildDetector;
import dev.reynard.junit.strategy.components.analyzers.RedundancyAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.StatusAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.StatusPropagationOracle;
import dev.reynard.junit.strategy.components.analyzers.TimingAnalyzer;
import dev.reynard.junit.strategy.components.generators.DynamicExplorationGenerator;
import dev.reynard.junit.strategy.components.pruners.DynamicReductionPruner;
import dev.reynard.junit.strategy.components.pruners.FaultloadSizePruner;
import dev.reynard.junit.strategy.components.pruners.NoImpactPruner;
import dev.reynard.junit.strategy.components.pruners.UnreachabilityPruner;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import dev.reynard.junit.util.TaggedTimer;

/**
 * Explores the fault space of one scenario. Each run is: next() for the
 * faultload, start() before the request under test, finish() with the verdict.
 * Used in-process by @FiTest, and over HTTP or the CLI by other languages.
 */
public class Explorer {
    private static final Logger logger = LoggerFactory.getLogger(Explorer.class);

    private final StrategyRunner strategy;
    private final FaultController controller;
    private final TaggedTimer totalTimer = new TaggedTimer();

    public Explorer(ExplorerConfig config, FaultController controller) {
        this.controller = controller;
        this.strategy = createStrategy(config);
        totalTimer.start("Total test time");
    }

    private static StrategyRunner createStrategy(ExplorerConfig config) {
        List<FailureMode> modes = List.of(
                ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR),
                ErrorFault.fromError(HttpError.BAD_GATEWAY),
                ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE),
                ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT));

        // List<FailureMode> modes =
        // List.of(ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE));

        StrategyRunner strategy = new StrategyRunner(modes);
        strategy
                .withComponent(new DynamicExplorationGenerator(strategy.getStore(), strategy::prune, config.pointOrder,
                        !config.depthFirstSearchOrder))
                // These components detect the necessairy info
                // for the implications store
                .withComponent(new HappyPathDetector())
                .withComponent(new ParentChildDetector())
                .withComponent(new HappensBeforeNeighbourDetector())
                .withComponent(new ErrorPropagationDetector())
                // Note: ConditionalPointDetector needs to be the last detector in the chain, as
                // it relies on the implications
                .withComponent(new ConditionalPointDetector(config.optimizeForRetries))
                .withComponent(new RedundancyAnalyzer())
                .withComponent(new StatusAnalyzer())
                .withComponent(new BehaviorAnalyzer())
                .withComponent(new TimingAnalyzer())
                .withComponent(new StatusPropagationOracle())
                .withComponent(new FaultsWithoutCauseOracle())
                .withComponent(new ConcurrencyDetector())
                .withComponent(new UnreachabilityPruner())
                .withComponent(new NoImpactPruner(config.optimizeForImpactless))
                .withComponent(new DynamicReductionPruner());

        if (config.maxTestCases > 0) {
            strategy.withMaxTestCases(config.maxTestCases);
        }

        if (config.maxTimeS > 0) {
            strategy.withMaxTimeS(config.maxTimeS);
        }

        if (config.maxFaultloadSize > 0) {
            strategy.withComponent(new FaultloadSizePruner(config.maxFaultloadSize));
        }

        if (config.failStop) {
            strategy.withStopOnError();
        }

        if (config.maskPayload) {
            strategy.withPayloadMasking();
        }

        if (config.hashBody) {
            strategy.withBodyHashing();
        }

        if (config.logHeaders) {
            strategy.withLogHeader();
        }

        if (config.checkAllPruners) {
            strategy.withCheckAllPruners();
        }

        if (config.withPredecessors) {
            strategy.withPredecessors();
        }

        if (config.initialGetTraceDelay > 0) {
            strategy.withGetDelay(config.initialGetTraceDelay);
        }

        if (config.outputDir != null) {
            Path outputDir = Path.of(config.outputDir).toAbsolutePath();
            logger.info("Exporting reports to: " + outputDir);
            strategy.setOutputDir(outputDir);
        }

        strategy.setContextName(config.name);
        return strategy;
    }

    public StrategyRunner getStrategy() {
        return strategy;
    }

    /** The next faultload to run, or null when the exploration is done */
    public TrackedFaultload next() {
        TaggedTimer strategyTimer = new TaggedTimer();
        strategyTimer.start("nextFaultload");
        TrackedFaultload faultload = strategy.nextFaultload();
        strategyTimer.stop("nextFaultload");
        strategy.registerTime(strategyTimer);
        return faultload;
    }

    /** Register the faultload with the proxies, before the request under test */
    public void start(TrackedFaultload faultload) {
        faultload.timer.start("Per test");
        faultload.timer.start("registerFaultload");
        try {
            controller.registerFaultload(faultload);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register faultload", e);
        }
        faultload.timer.stop("registerFaultload");
        faultload.timer.start("testMethod");
    }

    /** Handle the result of the run and update the strategy */
    public void finish(TrackedFaultload faultload, boolean passed) {
        faultload.timer.stop("testMethod");

        logger.info("Invariant: " + (passed ? "HOLDS" : "VIOLATED"));

        strategy.statistics.registerRun();

        try {
            TraceAnalysis trace = controller.getTrace(faultload);
            int status = trace.getRootReport().response.status;
            logger.info("Client responded with HTTP status: {}", status);
            faultload.timer.start("handleResult");
            FaultloadResult result = new FaultloadResult(faultload, trace, passed);
            strategy.handleResult(result);
            faultload.timer.stop("handleResult");
        } catch (IOException e) {
            // The fault combinations this run would have expanded into
            // are never explored, so this must stay visible rather than
            // being silently dropped as a stack trace on stdout.
            logger.error("Failed to fetch or process trace for faultload, this run's result is lost: {}",
                    faultload.getTraceId(), e);
            strategy.statistics.registerLost();
        }

        faultload.timer.start("unregisterFautload");
        try {
            controller.unregisterFaultload(faultload);
        } catch (Exception e) {
            logger.warn("Failed to unregister faultload: {}", faultload.getTraceId(), e);
        }
        faultload.timer.stop("unregisterFautload");

        faultload.timer.stop("Per test");
        strategy.registerTime(faultload);
    }

    /** Report the statistics of the exploration */
    public void close() {
        totalTimer.stop("Total test time");
        strategy.registerTime(totalTimer);
        strategy.statistics.setSize(strategy.getGenerator().spaceSize());
        strategy.statistics.report();
    }
}
