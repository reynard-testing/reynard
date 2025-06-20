package dev.reynard.junit;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
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
import dev.reynard.junit.strategy.util.Env;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;
import dev.reynard.junit.util.TaggedTimer;

public class FiTestExtension
        implements TestTemplateInvocationContextProvider {
    private StrategyRunner strategy;
    private static final String OUTPUT_DIR_KEY = "dsfit.output.dir";
    private final TaggedTimer totalTimer = new TaggedTimer();
    private static final Logger logger = LoggerFactory.getLogger(FiTestExtension.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        // Check if the annotation is present
        return context.getTestMethod().isPresent() &&
                context.getTestMethod().get().isAnnotationPresent(FiTest.class);
    }

    private String getOutputDir(ExtensionContext context) {
        String envOutput = Env.getEnv(Env.Keys.OUTPUT_DIR);
        if (!envOutput.equals("")) {
            return envOutput;
        }

        var outputConfig = context.getConfigurationParameter(OUTPUT_DIR_KEY);
        if (outputConfig.isPresent()) {
            return outputConfig.get();
        }

        return null;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        totalTimer.start("Total test time");
        // Retrieve the annotation and its parameters
        var annotation = context.getTestMethod()
                .orElseThrow()
                .getAnnotation(FiTest.class);

        if (annotation == null) {
            annotation = context.getRequiredTestClass().getAnnotation(FiTest.class);
        }

        List<FailureMode> modes = List.of(
                ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR),
                ErrorFault.fromError(HttpError.BAD_GATEWAY),
                ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE),
                ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT));

        // List<FailureMode> modes =
        // List.of(ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE));

        boolean onlyPersistantOrTransientRetries = annotation.optimizeForRetries();
        boolean pruneImpactless = annotation.optimizeForImpactless();
        int maxFaultloadSize = annotation.maxFaultloadSize();
        TraversalOrder traversalStrategy = annotation.pointOrder();
        boolean depthFirstSearchOrder = annotation.depthFirstSearchOrder();

        strategy = new StrategyRunner(modes);
        strategy
                .withComponent(new DynamicExplorationGenerator(strategy.getStore(), strategy::prune, traversalStrategy,
                        !depthFirstSearchOrder))
                // These components detect the necessairy info
                // for the implications store
                .withComponent(new HappyPathDetector())
                .withComponent(new ParentChildDetector())
                .withComponent(new HappensBeforeNeighbourDetector())
                .withComponent(new ErrorPropagationDetector())
                // Note: ConditionalPointDetector needs to be the last detector in the chain, as
                // it relies on the implications
                .withComponent(new ConditionalPointDetector(onlyPersistantOrTransientRetries))
                .withComponent(new RedundancyAnalyzer())
                .withComponent(new StatusAnalyzer())
                .withComponent(new BehaviorAnalyzer())
                .withComponent(new TimingAnalyzer())
                .withComponent(new StatusPropagationOracle())
                .withComponent(new ConcurrencyDetector())
                .withComponent(new UnreachabilityPruner())
                .withComponent(new NoImpactPruner(pruneImpactless))
                .withComponent(new DynamicReductionPruner());

        if (annotation.maxTestCases() > 0) {
            strategy.withMaxTestCases(annotation.maxTestCases());
        }

        if (annotation.maxTimeS() > 0) {
            strategy.withMaxTimeS(annotation.maxTimeS());
        }

        if (maxFaultloadSize > 0) {
            strategy.withComponent(new FaultloadSizePruner(maxFaultloadSize));
        }

        if (annotation.failStop()) {
            strategy.withStopOnError();
        }

        if (annotation.maskPayload()) {
            strategy.withPayloadMasking();
        }

        if (annotation.hashBody()) {
            strategy.withBodyHashing();
        }

        if (annotation.logHeaders()) {
            strategy.withLogHeader();
        }

        if (annotation.checkAllPruners()) {
            strategy.withCheckAllPruners();
        }

        if (annotation.withPredecessors()) {
            strategy.withPredecessors();
        }

        if (annotation.initialGetTraceDelay() > 0) {
            strategy.withGetDelay(annotation.initialGetTraceDelay());
        }

        String outputConfig = getOutputDir(context);
        if (outputConfig != null) {
            Path outputDir;

            if (outputConfig.startsWith("/")) {
                outputDir = Path.of(outputConfig);
            } else {
                Path projectRoot = Path.of("").toAbsolutePath().getParent();
                outputDir = projectRoot.resolve(outputConfig);
            }

            logger.info("Exporting reports to: " + outputDir);
            strategy.setOutputDir(outputDir);
        }

        String testClassName = context.getRequiredTestClass().getSimpleName();
        String testMethodName = context.getTestMethod().orElseThrow().getName();
        strategy.setContextName(testClassName + "#" + testMethodName);

        if (annotation.additionalComponents().length > 0) {
            for (Class<?> componentClass : annotation.additionalComponents()) {
                try {
                    var constructor = componentClass.getDeclaredConstructor();
                    var instance = constructor.newInstance();
                    strategy.withComponent(instance);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                        | NoSuchMethodException e) {
                    throw new RuntimeException("Failed to instantiate additional component: " + componentClass, e);
                }
            }
        }

        Class<?> testClass = context.getRequiredTestClass();
        FaultController controller;

        try {
            controller = (FaultController) testClass.getMethod("getController").invoke(null);
            if (controller == null) {
                throw new IllegalStateException(
                        "Test has no public static field getController(). Ensure getController() exists and returns a faultcontroller.");
            }
        } catch (NoSuchMethodException | NullPointerException | IllegalArgumentException | IllegalAccessException
                | InvocationTargetException | SecurityException e) {
            throw new RuntimeException("Failed to access getControleler from test class", e);
        }

        return Stream
                .generate(() -> createInvocationContext(strategy, controller))
                .takeWhile(ctx -> ctx != null)
                .onClose(() -> {
                    afterAll();
                });
    }

    private TestTemplateInvocationContext createInvocationContext(StrategyRunner strategy, FaultController controller) {
        TaggedTimer strategyTimer = new TaggedTimer();
        strategyTimer.start("nextFaultload");
        TrackedFaultload faultload = strategy.nextFaultload();
        strategyTimer.stop("nextFaultload");
        strategy.registerTime(strategyTimer);

        if (faultload == null) {
            return null;
        }

        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return "[" + invocationIndex + "] TraceId=" + faultload.getTraceId() + " Faultload="
                        + faultload.getFaultload().readableString();
            }

            @Override
            public List<Extension> getAdditionalExtensions() {
                return List.of(
                        new QueueParameterResolver(faultload),
                        new BeforeTestExtension(faultload, controller),
                        new AfterTestExtension(faultload, strategy, controller));
            }
        };
    }

    public void afterAll() {
        totalTimer.stop("Total test time");
        strategy.registerTime(totalTimer);
        strategy.statistics.setSize(strategy.getGenerator().spaceSize());
        strategy.statistics.report();
    }

    // Parameter resolver to inject the current parameter into the test
    private static class QueueParameterResolver implements ParameterResolver {
        private final TrackedFaultload faultload;

        QueueParameterResolver(TrackedFaultload faultload) {
            this.faultload = faultload;
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType().equals(TrackedFaultload.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return faultload;
        }
    }

    // Before each test, register the faultload with the proxies
    private static class BeforeTestExtension implements BeforeTestExecutionCallback {
        private final TrackedFaultload faultload;
        private final FaultController controller;

        BeforeTestExtension(TrackedFaultload faultload, FaultController controller) {
            this.faultload = faultload;
            this.controller = controller;
        }

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            if (faultload == null) {
                return;
            }

            String displayName = context.getDisplayName();
            System.out.println();
            logger.info("Test " + displayName);

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
    }

    // After each test, handle the result and update the strategy
    private static class AfterTestExtension implements AfterTestExecutionCallback {
        private final TrackedFaultload faultload;
        private final StrategyRunner strategy;
        private final FaultController controller;

        AfterTestExtension(TrackedFaultload faultload, StrategyRunner strategy, FaultController controller) {
            this.faultload = faultload;
            this.strategy = strategy;
            this.controller = controller;
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            faultload.timer.stop("testMethod");
            // Access the queue and test result
            // var testMethod = context.getTestMethod().orElseThrow();
            // var annotation = testMethod.getAnnotation(FiTest.class);

            boolean testFailed = context.getExecutionException().isPresent();
            logger.info("Invariant: " + (testFailed ? "VIOLATED" : "HOLDS"));

            strategy.statistics.registerRun();

            try {
                TraceAnalysis trace = controller.getTrace(faultload);
                int status = trace.getRootReport().response.status;
                logger.info("Client responded with HTTP status: {}", status);
                faultload.timer.start("handleResult");
                FaultloadResult result = new FaultloadResult(faultload, trace, !testFailed);
                strategy.handleResult(result);
                faultload.timer.stop("handleResult");
            } catch (IOException e) {
                e.printStackTrace();
            }

            faultload.timer.start("unregisterFautload");
            try {
                controller.unregisterFaultload(faultload);
            } catch (Exception e) {
                e.printStackTrace();
            }
            faultload.timer.stop("unregisterFautload");

            faultload.timer.stop("Per test");
            strategy.registerTime(faultload);
        }
    }

}
