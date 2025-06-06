package io.github.delanoflipse.fit.suite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;

@Target(ElementType.METHOD) // Apply to methods
@Retention(RetentionPolicy.RUNTIME) // Retain at runtime so JUnit can read it
@TestTemplate // Mark this as a template for multiple executions
@ExtendWith(FiTestExtension.class) // Link to the extension
public @interface FiTest {
    boolean maskPayload() default false;

    boolean hashBody() default false;

    boolean logHeaders() default false;

    boolean withCallStack() default false;

    boolean failStop() default true;

    boolean optimizeForRetries() default false;

    boolean optimizeForImpactless() default false;

    boolean checkAllPruners() default false;

    long maxTestCases() default 0;

    long maxTimeS() default 0;

    int maxFaultloadSize() default 0;

    int initialGetTraceDelay() default 0;

    Class<?>[] additionalComponents() default {};

    /** The order in which points in the trace analysis are considered */
    TraversalOrder pointOrder() default TraversalOrder.DEPTH_FIRST_POST_ORDER;

    /** The order in which the search tree is visited */
    boolean depthFirstSearchOrder() default false;
}
