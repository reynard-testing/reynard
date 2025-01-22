package nl.dflipse.fit.strategy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import nl.dflipse.fit.collector.TraceData;
import nl.dflipse.fit.instrument.InstrumentedApp;

public class FiTestExtension implements TestTemplateInvocationContextProvider, AfterTestExecutionCallback {
    private FIStrategy strategy;
    private InstrumentedApp app;

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

        try {
            strategy = annotation.strategy().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate strategy", e);
        }

        return Stream
                .generate(() -> createInvocationContext(strategy.next()))
                .takeWhile(ctx -> ctx != null);
    }

    private TestTemplateInvocationContext createInvocationContext(Faultload faultload) {
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
                return List.of(new QueueParameterResolver(faultload));
            }
        };
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
            ExtensionContext.Namespace testNamespace = ExtensionContext.Namespace
                    .create(extensionContext.getUniqueId());

            extensionContext.getStore(testNamespace).put("faultload", faultload);

            return faultload;
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        // Access the queue and test result
        // var testMethod = context.getTestMethod().orElseThrow();
        // var annotation = testMethod.getAnnotation(FiTest.class);
        var displayName = context.getDisplayName();

        var testFailed = context.getExecutionException().isPresent();

        ExtensionContext.Namespace testNamespace = ExtensionContext.Namespace.create(context.getUniqueId());
        var faultload = context.getStore(testNamespace).get("faultload", Faultload.class);

        System.out.println(
                "Test " + displayName + " with result: "
                        + (testFailed ? "FAIL" : "PASS"));

        // Get the test instance and check if it implements InstrumentedTest
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof InstrumentedTest) {
            app = ((InstrumentedTest) testInstance).getApp();
        } else {
            throw new RuntimeException("Test does not implement InstrumentedTest");
        }

        TraceData trace = app.getTrace(faultload.getTraceId());
        strategy.handleResult(faultload, trace, !testFailed);
    }
}
