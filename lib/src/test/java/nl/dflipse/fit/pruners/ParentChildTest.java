package nl.dflipse.fit.pruners;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.FeedbackContext;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.pruners.ParentChildPruner;
import nl.dflipse.fit.strategy.pruners.PruneDecision;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.util.FailureModes;
import nl.dflipse.fit.util.NodeBuilder;
import nl.dflipse.fit.util.ShapeBuilder;

public class ParentChildTest {
    private final FaultMode mode = FailureModes.getMode(0);

    private FaultUid bySignature(TraceAnalysis trace, String signature) {
        for (var r : trace.getReports()) {
            if (r.faultUid.signature().equals(signature)) {
                return r.faultUid;
            }
        }

        return null;
    }

    private Faultload getFaultload(FaultUid... fs) {
        Set<Fault> faults = new HashSet<>();
        for (var f : fs) {
            faults.add(new Fault(f, mode));
        }
        return new Faultload(faults);
    }

    @Test
    public void testNone() {
        ParentChildPruner pruner = new ParentChildPruner();
        var faultload = new Faultload(Set.of());
        FeedbackContext contextMock = mock(FeedbackContext.class);
        ShapeBuilder builder = new ShapeBuilder("");
        var shape = builder.leafNode();
        var trace = new TraceAnalysis(shape);
        var result = new FaultloadResult(new TrackedFaultload(faultload), trace, true);
        pruner.handleFeedback(result, contextMock);
        assertEquals(PruneDecision.KEEP, pruner.prune(faultload));
    }

    @Test
    public void testParentChild() {
        FeedbackContext contextMock = mock(FeedbackContext.class);
        ParentChildPruner pruner = new ParentChildPruner();

        // -> A/a1 -> B/b1
        var shape = new NodeBuilder()
                .withReport("a1")
                .buildReport()
                .withChild(new NodeBuilder()
                        .withReport("b1")
                        .buildReport())
                .build();
        var trace = new TraceAnalysis(shape);
        var faultload1 = new Faultload(Set.of());

        // When pruner receives feedback
        var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
        pruner.handleFeedback(result, contextMock);

        var f1 = bySignature(trace, "a1");
        var f2 = bySignature(trace, "b1");

        // Then - it should prune the combination
        assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(f1, f2)));
        assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(f1)));
        assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(f2)));
    }
}
