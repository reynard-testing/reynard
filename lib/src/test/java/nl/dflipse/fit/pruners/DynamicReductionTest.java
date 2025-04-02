package nl.dflipse.fit.pruners;

import java.util.Set;

import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.faultload.faultmodes.HttpError;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.pruners.DynamicReductionPruner;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;
import nl.dflipse.fit.util.NodeBuilder;

public class DynamicReductionTest {

    @Test
    public void testEmpty() {
        DynamicReductionPruner pruner = new DynamicReductionPruner();

        TrackedFaultload initialFaultload = new TrackedFaultload();

        String serviceB = "B";
        String apiB1 = "B1";

        String serviceA = "A";
        String apiA1 = "A1";

        TraceTreeSpan spanB = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withReport(serviceA, apiB1)
                .withResponse(200, "OK")
                .build();

        TraceTreeSpan spanA = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withChildren(spanB)
                .withReport(null, apiA1)
                .withResponse(200, "OK")
                .build();

        TraceAnalysis initialTrace = new TraceAnalysis(spanA);
        FaultloadResult result = new FaultloadResult(initialFaultload, initialTrace, true);
        pruner.handleFeedback(result, null);

        // The empty faultload is pruned
        assertTrue(pruner.prune(initialFaultload.getFaultload()));
    }

    @Test
    public void testBase() {
        // A -> B -> C
        DynamicReductionPruner pruner = new DynamicReductionPruner();

        TrackedFaultload initialFaultload = new TrackedFaultload();

        String serviceA = "A";
        String apiA1 = "A1";

        String serviceB = "B";
        String apiB1 = "B1";

        String serviceC = "C";
        String apiC1 = "C1";

        TraceTreeSpan spanC1 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceC)
                .withReport(serviceB, apiC1)
                .withResponse(200, "OK")
                .build();

        TraceTreeSpan spanB1 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withChildren(spanC1)
                .withReport(serviceA, apiB1)
                .withResponse(200, "OK")
                .build();

        TraceTreeSpan spanA1 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withChildren(spanB1)
                .withReport(null, apiA1)
                .withResponse(200, "OK")
                .build();

        TraceAnalysis initialTrace = new TraceAnalysis(spanA1);
        FaultloadResult result = new FaultloadResult(initialFaultload, initialTrace, true);
        pruner.handleFeedback(result, null);

        FaultMode injectedFault = ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE);
        FaultMode resultedFault = ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR);

        TraceTreeSpan spanC2 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceC)
                .withReport(serviceB, apiC1)
                .withFault(injectedFault)
                .build();

        TraceTreeSpan spanB2 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withChildren(spanC2)
                .withReport(serviceA, apiB1)
                .withResponse(HttpError.INTERNAL_SERVER_ERROR.getErrorCode(), "err")
                .build();

        TraceTreeSpan spanA2 = new NodeBuilder(initialFaultload.getTraceId())
                .withService(serviceB)
                .withChildren(spanB2)
                .withReport(null, apiA1)
                .withResponse(200, "OK")
                .build();

        // The empty faultload is pruned
        Faultload faultload = new Faultload(Set.of(new Fault(spanC2.reports.get(0).faultUid, injectedFault)));
        TraceAnalysis trace = new TraceAnalysis(spanA2);
        FaultloadResult result2 = new FaultloadResult(new TrackedFaultload(faultload), trace, true);
        pruner.handleFeedback(result2, null);

        Faultload faultloadToPrune = new Faultload(
                Set.of(new Fault(spanB2.reports.get(0).faultUid, resultedFault)));
        assertTrue(pruner.prune(faultloadToPrune));
    }
}
