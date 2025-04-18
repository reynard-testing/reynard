package nl.dflipse.fit.pruners;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.ErrorFault;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.faultload.modes.HttpError;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.pruners.DynamicReductionPruner;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.util.EventBuilder;

public class DynamicReductionTest {

    @Test
    public void testEmpty() {
        DynamicReductionPruner pruner = new DynamicReductionPruner();

        TrackedFaultload initialFaultload = new TrackedFaultload();

        EventBuilder nodeA = new EventBuilder()
                .withPoint("A", "a1");

        EventBuilder nodeB = nodeA.createChild()
                .withPoint("B", "b1");

        TraceAnalysis initialTrace = nodeA.buildTrace();
        FaultloadResult result = new FaultloadResult(initialFaultload, initialTrace, true);
        FeedbackContext contextMock = mock(FeedbackContext.class);
        pruner.handleFeedback(result, contextMock);

        // The empty faultload is pruned
        PruneDecision decision = pruner.prune(initialFaultload.getFaultload(), contextMock);
        assertEquals(PruneDecision.PRUNE, decision);
    }

    @Test
    public void testBase() {
        DynamicReductionPruner pruner = new DynamicReductionPruner();

        TrackedFaultload initialFaultload = new TrackedFaultload();

        // A -> B -> C
        EventBuilder nodeA = new EventBuilder()
                .withPoint("A", "a1");

        EventBuilder nodeB = nodeA.createChild()
                .withPoint("B", "b1");

        EventBuilder nodeC = nodeB.createChild()
                .withPoint("C", "c1");

        TraceAnalysis initialTrace = nodeA.buildTrace();
        FaultloadResult result = new FaultloadResult(initialFaultload, initialTrace, true);
        FeedbackContext contextMock = mock(FeedbackContext.class);
        pruner.handleFeedback(result, contextMock);

        // Inject a fault in node C, and propogate it to node B
        FailureMode injectedMode = ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE);
        FailureMode resultedFault = ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR);

        nodeC.withFault(injectedMode);
        nodeB.withFault(resultedFault);

        // The empty faultload is pruned
        Faultload faultload = new Faultload(Set.of(new Fault(nodeC.getFaultUid(), injectedMode)));
        TraceAnalysis trace = nodeA.buildTrace();
        FaultloadResult result2 = new FaultloadResult(new TrackedFaultload(faultload), trace, true);
        pruner.handleFeedback(result2, contextMock);

        Faultload faultloadToPrune = new Faultload(
                Set.of(new Fault(nodeB.getFaultUid(), resultedFault)));

        // The empty faultload is pruned
        PruneDecision decision = pruner.prune(faultloadToPrune, contextMock);
        assertEquals(PruneDecision.PRUNE, decision);
    }
}
