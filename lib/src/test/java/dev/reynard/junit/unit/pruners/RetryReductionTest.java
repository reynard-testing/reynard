package dev.reynard.junit.unit.pruners;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.StrategyRunner;
import dev.reynard.junit.strategy.components.analyzers.ConditionalPointDetector;
import dev.reynard.junit.strategy.components.analyzers.ErrorPropagationDetector;
import dev.reynard.junit.strategy.components.analyzers.HappensBeforeNeighbourDetector;
import dev.reynard.junit.strategy.components.analyzers.HappyPathDetector;
import dev.reynard.junit.strategy.components.analyzers.ParentChildDetector;
import dev.reynard.junit.strategy.components.generators.DynamicExplorationGenerator;
import dev.reynard.junit.strategy.store.ImplicationsStore;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;
import dev.reynard.junit.unit.generators.DynamicExplorationTest;
import dev.reynard.junit.util.EventBuilder;
import dev.reynard.junit.util.FailureModes;

public class RetryReductionTest {

    private List<FaultloadResult> playout(StrategyRunner runner, ImplicationsStore store) {

        List<FaultloadResult> visited = new ArrayList<>();

        while (true) {
            var next = runner.nextFaultload();
            if (next == null) {
                break;
            }

            FaultloadResult result = DynamicExplorationTest.toResult(next.getFaultload(), store);
            visited.add(result);
            runner.handleResult(result);
        }

        return visited;
    }

    @Test
    public void testWithRetries() {
        ConditionalPointDetector retryPolicy = new ConditionalPointDetector(true);

        var modes = FailureModes.getModes(1);

        var a = new EventBuilder("A");
        var b = a.createChild().withPoint("B");

        ImplicationsStore storedTruth = new ImplicationsStore();
        storedTruth.addDownstreamRequests(a.uid(), List.of(b.uid()));

        List<EventBuilder> retriedBs = new ArrayList<>();
        int RETRIES = 3;
        for (int i = 0; i < RETRIES; i++) {
            EventBuilder bRetry = a.createChild().withPoint("B", i + 1);
            retriedBs.add(bRetry);
        }

        for (int i = 0; i < retriedBs.size(); i++) {
            var pred = i == 0 ? b : retriedBs.get(i - 1);
            var retry = retriedBs.get(i);
            // B failure includes B retry
            storedTruth.addInclusionEffect(Set.of(pred.behaviour().asMode(modes.get(0))), retry.uid());
        }

        StrategyRunner runner = new StrategyRunner(modes);
        DynamicExplorationGenerator generator = new DynamicExplorationGenerator(runner.getStore(), runner::prune,
                TraversalOrder.BREADTH_FIRST);
        runner.withComponent(generator)
                .withComponent(generator)
                .withComponent(new HappyPathDetector())
                .withComponent(new ParentChildDetector())
                .withComponent(new HappensBeforeNeighbourDetector())
                .withComponent(new ErrorPropagationDetector())
                .withComponent(retryPolicy);
        var result = playout(runner, storedTruth);

        // [], B, Binf
        assertEquals(3, result.size());
    }
}
