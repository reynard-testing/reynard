package nl.dflipse.fit;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.StrategyRunner;
import nl.dflipse.fit.strategy.generators.BreadthFirstGenerator;
import nl.dflipse.fit.strategy.generators.DepthFirstGenerator;
import nl.dflipse.fit.strategy.generators.RandomPowersetGenerator;
import nl.dflipse.fit.strategy.handlers.RedundancyAnalyzer;
import nl.dflipse.fit.strategy.pruners.FailStopPruner;
import nl.dflipse.fit.strategy.pruners.HappensBeforePruner;
import nl.dflipse.fit.strategy.pruners.ParentChildPruner;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class FiTestExtension
        implements TestTemplateInvocationContextProvider {
    private StrategyRunner strategy;

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        // Check if the annotation is present
        return context.getTestMethod().isPresent() &&
                context.getTestMethod().get().isAnnotationPresent(FiTest.class);
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

        var failStop = new FailStopPruner();
        var parentChild = new ParentChildPruner();
        var happensBefore = new HappensBeforePruner();
        strategy = new StrategyRunner()
                // .withGenerator(new RandomPowersetGenerator())
                // .withGenerator(new BreadthFirstGenerator())
                .withGenerator(new DepthFirstGenerator())
                .withPruner(failStop)
                .withPruner(parentChild)
                .withPruner(happensBefore)
                .withAnalyzer(new RedundancyAnalyzer());

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
        Faultload faultload = strategy.nextFaultload();

        if (faultload == null) {
            return null;
        }

        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return "[" + invocationIndex + "] TraceId=" + faultload.getTraceId() + " Faults="
                        + faultload.readableString();
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
        // all done?
        System.out.println("---- STATS ----");
        System.out.println("Queued : " + strategy.queuedCount);
        System.out.println("Pruned : " + strategy.prunedCount);
    }

    // Parameter resolver to inject the current parameter into the test
    private static class QueueParameterResolver implements ParameterResolver {
        private final Faultload faultload;

        QueueParameterResolver(Faultload faultload) {
            this.faultload = faultload;
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType().equals(Faultload.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return faultload;
        }
    }

    // Before each test, register the faultload with the proxies
    private static class BeforeTestExtension implements BeforeTestExecutionCallback {
        private final Faultload faultload;
        private final FaultController controller;

        BeforeTestExtension(Faultload faultload, FaultController controller) {
            this.faultload = faultload;
            this.controller = controller;
        }

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            if (faultload == null) {
                return;
            }

            controller.registerFaultload(faultload);
        }
    }

    // After each test, handle the result and update the strategy
    private static class AfterTestExtension implements AfterTestExecutionCallback {
        private final Faultload faultload;
        private final StrategyRunner strategy;
        private final FaultController controller;

        AfterTestExtension(Faultload faultload, StrategyRunner strategy, FaultController controller) {
            this.faultload = faultload;
            this.strategy = strategy;
            this.controller = controller;
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {

            // Access the queue and test result
            // var testMethod = context.getTestMethod().orElseThrow();
            // var annotation = testMethod.getAnnotation(FiTest.class);

            String displayName = context.getDisplayName();
            boolean testFailed = context.getExecutionException().isPresent();

            System.out.println(
                    "Test " + displayName + " with result: "
                            + (testFailed ? "FAIL" : "PASS"));

            try {
                TraceAnalysis trace = controller.getTrace(faultload);
                FaultloadResult result = new FaultloadResult(faultload, trace, !testFailed);
                strategy.handleResult(result);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                controller.unregisterFaultload(faultload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
