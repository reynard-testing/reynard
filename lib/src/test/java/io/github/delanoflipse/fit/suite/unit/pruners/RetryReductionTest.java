package io.github.delanoflipse.fit.suite.unit.pruners;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.StrategyRunner;
import io.github.delanoflipse.fit.suite.strategy.components.analyzers.ConditionalPointDetector;
import io.github.delanoflipse.fit.suite.strategy.components.analyzers.ErrorPropagationDetector;
import io.github.delanoflipse.fit.suite.strategy.components.analyzers.HappensBeforeNeighbourDetector;
import io.github.delanoflipse.fit.suite.strategy.components.analyzers.HappyPathDetector;
import io.github.delanoflipse.fit.suite.strategy.components.analyzers.ParentChildDetector;
import io.github.delanoflipse.fit.suite.strategy.components.generators.DynamicExplorationGenerator;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;
import io.github.delanoflipse.fit.suite.unit.generators.DynamicExplorationTest;
import io.github.delanoflipse.fit.suite.util.EventBuilder;
import io.github.delanoflipse.fit.suite.util.FailureModes;

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
