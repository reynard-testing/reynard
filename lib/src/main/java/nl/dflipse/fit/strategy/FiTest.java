package nl.dflipse.fit.strategy;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import nl.dflipse.fit.strategy.strategies.DepthFirstStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // Apply to methods
@Retention(RetentionPolicy.RUNTIME) // Retain at runtime so JUnit can read it
@TestTemplate // Mark this as a template for multiple executions
@ExtendWith(FiTestExtension.class) // Link to the extension
public @interface FiTest {
    Class<? extends FIStrategy> strategy() default DepthFirstStrategy.class;
}
