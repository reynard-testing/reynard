package dev.reynard.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.reynard.junit.strategy.components.generators.Generators;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

@Target(ElementType.METHOD) // Apply to methods
@Retention(RetentionPolicy.RUNTIME) // Retain at runtime so JUnit can read it
@TestTemplate // Mark this as a template for multiple executions
@ExtendWith(FiTestExtension.class) // Link to the extension
public @interface FiTest {
    /** Ignore the payload field in the identifier */
    boolean maskPayload() default false;

    /** Include the predecessors field */
    boolean withPredecessors() default false;

    /** Use a hash of the response body */
    boolean hashBody() default false;

    /** For debuggin purposes: instruct the proxies to log all headers */
    boolean logHeaders() default false;

    /** Do we when we find a failing test? */
    boolean failStop() default true;

    /**
     * Assume that retries are implemented correctly, and reduce the search space
     */
    boolean optimizeForRetries() default false;

    boolean optimizeForImpactless() default false;

    /**
     * Give all pruners the change to determine the redundancy of a faultload.
     * Instead, if a pruner determine it redundant, we skip checking the others.
     */
    boolean checkAllPruners() default false;

    /** The maximum number of test executions. 0 indicates no bound. */
    long maxTestCases() default 0;

    /** The maximum time the tests should take in seconds. 0 indicates no bound. */
    long maxTimeS() default 0;

    /** Bound the size of the faultloads. 0 indicates no bound. */
    int maxFaultloadSize() default 0;

    /**
     * Delay before retrieving the reports from the proxies. Useful to wait for
     * asynchronous communication.
     */
    int initialGetTraceDelay() default 0;

    /**
     * Add custom components to the search strategy, such as analyzers and pruners.
     */
    Class<?>[] additionalComponents() default {};

    /** The order in which points in the call graph are considered */
    TraversalOrder pointOrder() default TraversalOrder.DEPTH_FIRST_POST_ORDER;

    /** Exploration generator */
    Generators generator() default Generators.DYNAMIC;

    /** The order in which the search tree is visited */
    boolean depthFirstSearchOrder() default false;
}
