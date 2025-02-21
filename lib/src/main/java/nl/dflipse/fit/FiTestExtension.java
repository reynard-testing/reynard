package nl.dflipse.fit;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.StrategyRunner;
import nl.dflipse.fit.strategy.generators.DepthFirstGenerator;
import nl.dflipse.fit.strategy.handlers.RedundancyAnalyzer;
import nl.dflipse.fit.strategy.pruners.FailStopPruner;
import nl.dflipse.fit.strategy.pruners.HappensBeforePruner;
import nl.dflipse.fit.strategy.pruners.ParentChildPruner;
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
                .withGenerator(new DepthFirstGenerator())
                .withPruner(failStop)
                .withAnalyzer(failStop)
                .withPruner(parentChild)
                .withAnalyzer(parentChild)
                .withPruner(happensBefore)
                .withAnalyzer(happensBefore)
                .withAnalyzer(new RedundancyAnalyzer());

        Class<?> testClass = context.getRequiredTestClass();
        InstrumentedApp app;

        try {
            var appField = testClass.getDeclaredField("app");
            appField.setAccessible(true); // Make the field accessible

            app = (InstrumentedApp) appField.get(null); // Access the static field
            if (app == null) {
                throw new IllegalStateException(
                        "InstrumentedApp is not initialized. Ensure setupServices() is called.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access InstrumentedApp from test class", e);
        }

        return Stream
                .generate(() -> createInvocationContext(strategy, app))
                .takeWhile(ctx -> ctx != null)
                .onClose(() -> {
                    afterAll();
                });
    }

    private TestTemplateInvocationContext createInvocationContext(StrategyRunner strategy, InstrumentedApp app) {
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
                        new BeforeTestExtension(faultload, app),
                        new AfterTestExtension(faultload, strategy, app));
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
        private final InstrumentedApp app;

        BeforeTestExtension(Faultload faultload, InstrumentedApp app) {
            this.faultload = faultload;
            this.app = app;
        }

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            if (faultload == null) {
                return;
            }

            app.registerFaultload(faultload);
        }
    }

    // After each test, handle the result and update the strategy
    private static class AfterTestExtension implements AfterTestExecutionCallback {
        private final Faultload faultload;
        private final StrategyRunner strategy;
        private final InstrumentedApp app;

        AfterTestExtension(Faultload faultload, StrategyRunner strategy, InstrumentedApp app) {
            this.faultload = faultload;
            this.strategy = strategy;
            this.app = app;
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
                TraceTreeSpan trace = app.getTrace(faultload.getTraceId());
                FaultloadResult result = new FaultloadResult(faultload, trace, !testFailed);
                strategy.handleResult(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
