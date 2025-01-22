package nl.dflipse.fit;

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

import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.strategy.FIStrategy;
import nl.dflipse.fit.strategy.Faultload;
import nl.dflipse.fit.trace.data.TraceData;

public class FiTestExtension
        implements TestTemplateInvocationContextProvider, AfterTestExecutionCallback, BeforeTestExecutionCallback {
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
                .generate(() -> {
                    Faultload faultload = strategy.next();
                    TestTemplateInvocationContext invocationContext = createInvocationContext(faultload);
                    
                    if (faultload == null || invocationContext == null) {
                        return invocationContext;
                    }

                    // invocationContext.get
                    var testNamespace = ExtensionContext.Namespace.create(context.getUniqueId(), );
                    context.getStore(testNamespace).put("faultload", faultload);
                    
                    return invocationContext;
                })
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
            return faultload;
        }
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        TestTemplateInvocationContext invocationContext = context.getStore(ExtensionContext.Namespace.GLOBAL)
            .get("invocationContext", TestTemplateInvocationContext.class);
        ExtensionContext.Namespace testNamespace = ExtensionContext.Namespace.create(context.getUniqueId());
        Faultload faultload = context.getStore(testNamespace).get("faultload", Faultload.class);

        // ensure the faultload is registered to the proxies
        app.registerFaultload(faultload);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        // Access the queue and test result
        // var testMethod = context.getTestMethod().orElseThrow();
        // var annotation = testMethod.getAnnotation(FiTest.class);
        String displayName = context.getDisplayName();

        boolean testFailed = context.getExecutionException().isPresent();

        ExtensionContext.Namespace testNamespace = ExtensionContext.Namespace.create(context.getUniqueId());
        Faultload faultload = context.getStore(testNamespace).get("faultload", Faultload.class);

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
