package dev.reynard.junit.unit.pruners;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.HttpError;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.components.pruners.DynamicReductionPruner;
import dev.reynard.junit.strategy.util.Pair;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import dev.reynard.junit.util.EventBuilder;

public class DynamicReductionTest {
        @Test
        public void testNoHistoric() {
                DynamicReductionPruner pruner = new DynamicReductionPruner();

                FeedbackContext contextMock = mock(FeedbackContext.class);
                when(contextMock.getHistoricResults()).thenReturn(List.of());

                // The empty faultload is not pruned, there are no historic results
                PruneDecision decision = pruner.prune(new Faultload(Set.of()), contextMock);
                assertEquals(PruneDecision.PRUNE, decision);
        }

        @Test
        public void testErrorPropagation() {
                DynamicReductionPruner pruner = new DynamicReductionPruner();

                HttpError propagated = HttpError.SERVICE_UNAVAILABLE;

                // A -> B -> C
                // Error at C causes error at B, causes error at A
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1")
                                .withResponse(propagated.getErrorCode(), "error");

                EventBuilder nodeB = nodeA.createChild()
                                .withPoint("B", "b1")
                                .withResponse(propagated.getErrorCode(), "error");

                EventBuilder nodeC = nodeB.createChild()
                                .withPoint("C", "c1")
                                .withFault(ErrorFault.fromError(propagated));

                TraceAnalysis initialTrace = nodeA.buildTrace();
                FeedbackContext contextMock = mock(FeedbackContext.class);
                when(contextMock.getHistoricResults()).thenReturn(List.of(
                                Pair.of(nodeA.getFaults(), initialTrace.getBehaviours())));

                Set<Fault> faultSet = Set.of(
                                new Fault(nodeB.uid(), ErrorFault.fromError(propagated)));
                when(contextMock.getExpectedBehaviours(faultSet)).thenReturn(Set.of(
                                initialTrace.getReportByFaultUid(nodeA.uid()).getBehaviour(),
                                initialTrace.getReportByFaultUid(nodeB.uid()).getBehaviour()));

                // The error at just B is pruned
                PruneDecision decision = pruner.prune(new Faultload(faultSet), contextMock);
                assertEquals(PruneDecision.PRUNE, decision);
        }
}
