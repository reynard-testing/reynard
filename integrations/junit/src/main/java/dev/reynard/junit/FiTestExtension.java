package dev.reynard.junit;

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

import dev.reynard.explorer.Explorer;
import dev.reynard.explorer.ExplorerConfig;

import dev.reynard.junit.instrumentation.FaultController;
import dev.reynard.junit.strategy.StrategyRunner;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.util.Env;

public class FiTestExtension
        implements TestTemplateInvocationContextProvider {
    private Explorer explorer;
    private StrategyRunner strategy;
    private static final String OUTPUT_DIR_KEY = "reynard.output.dir";
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
        // Retrieve the annotation and its parameters
        var annotation = context.getTestMethod()
                .orElseThrow()
                .getAnnotation(FiTest.class);

        if (annotation == null) {
            annotation = context.getRequiredTestClass().getAnnotation(FiTest.class);
        }

        ExplorerConfig config = new ExplorerConfig();
        config.name = context.getRequiredTestClass().getSimpleName() + "#"
                + context.getTestMethod().orElseThrow().getName();
        config.maskPayload = annotation.maskPayload();
        config.withPredecessors = annotation.withPredecessors();
        config.hashBody = annotation.hashBody();
        config.logHeaders = annotation.logHeaders();
        config.failStop = annotation.failStop();
        config.optimizeForRetries = annotation.optimizeForRetries();
        config.optimizeForImpactless = annotation.optimizeForImpactless();
        config.checkAllPruners = annotation.checkAllPruners();
        config.maxTestCases = annotation.maxTestCases();
        config.maxTimeS = annotation.maxTimeS();
        config.maxFaultloadSize = annotation.maxFaultloadSize();
        config.initialGetTraceDelay = annotation.initialGetTraceDelay();
        config.pointOrder = annotation.pointOrder();
        config.depthFirstSearchOrder = annotation.depthFirstSearchOrder();

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

        explorer = new Explorer(config, controller);
        strategy = explorer.getStrategy();

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

        return Stream
                .generate(() -> createInvocationContext(explorer))
                .takeWhile(ctx -> ctx != null)
                .onClose(() -> {
                    afterAll();
                });
    }

    private TestTemplateInvocationContext createInvocationContext(Explorer explorer) {
        TrackedFaultload faultload = explorer.next();

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
                        new BeforeTestExtension(faultload, explorer),
                        new AfterTestExtension(faultload, explorer));
            }
        };
    }

    public void afterAll() {
        explorer.close();
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
        private final Explorer explorer;

        BeforeTestExtension(TrackedFaultload faultload, Explorer explorer) {
            this.faultload = faultload;
            this.explorer = explorer;
        }

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            if (faultload == null) {
                return;
            }

            String displayName = context.getDisplayName();
            logger.info("Test " + displayName);

            explorer.start(faultload);
        }
    }

    // After each test, handle the result and update the strategy
    private static class AfterTestExtension implements AfterTestExecutionCallback {
        private final TrackedFaultload faultload;
        private final Explorer explorer;

        AfterTestExtension(TrackedFaultload faultload, Explorer explorer) {
            this.faultload = faultload;
            this.explorer = explorer;
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            boolean testFailed = context.getExecutionException().isPresent();
            explorer.finish(faultload, !testFailed);
        }
    }
}
