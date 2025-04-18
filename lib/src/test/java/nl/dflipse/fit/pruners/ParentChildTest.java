package nl.dflipse.fit.pruners;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.components.FeedbackContext;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.components.analyzers.ParentChildDetector;
import nl.dflipse.fit.util.FailureModes;
import nl.dflipse.fit.util.EventBuilder;

public class ParentChildTest {
        private final FailureMode mode = FailureModes.getMode(0);

        private Faultload getFaultload(FaultUid... fs) {
                Set<Fault> faults = new HashSet<>();
                for (var f : fs) {
                        faults.add(new Fault(f, mode));
                }
                return new Faultload(faults);
        }

        @Test
        public void testNone() {
                ParentChildDetector pruner = new ParentChildDetector();
                var faultload = new Faultload(Set.of());
                FeedbackContext contextMock = mock(FeedbackContext.class);
                EventBuilder root = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder node1 = root.createChild()
                                .withPoint("B", "b1");

                var trace = root.buildTrace();
                var result = new FaultloadResult(new TrackedFaultload(faultload), trace, true);
                pruner.handleFeedback(result, contextMock);
                assertEquals(PruneDecision.KEEP, pruner.prune(faultload));
        }

        @Test
        public void testParentChild() {
                FeedbackContext contextMock = mock(FeedbackContext.class);
                ParentChildDetector pruner = new ParentChildDetector();

                // -> A/a1 -> B/b1
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder nodeB = nodeA.createChild()
                                .withPoint("B", "b1");
                var trace = nodeA.buildTrace();
                var faultload1 = new Faultload(Set.of());

                // When pruner receives feedback
                var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
                pruner.handleFeedback(result, contextMock);

                var f1 = nodeA.getFaultUid();
                var f2 = nodeB.getFaultUid();

                // Then - it should prune the combination
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(f1, f2)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(f1)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(f2)));
        }

        @Test
        public void testNestedChild() {
                FeedbackContext contextMock = mock(FeedbackContext.class);
                ParentChildDetector pruner = new ParentChildDetector();

                // Given structure
                // -> A/a1 -> B/b1 -> C/c1
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder nodeB = nodeA.createChild()
                                .withPoint("B", "b1");
                EventBuilder nodeC = nodeB.createChild()
                                .withPoint("C", "c1");
                var trace = nodeA.buildTrace();
                var faultload1 = new Faultload(Set.of());

                // When pruner receives feedback
                var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
                pruner.handleFeedback(result, contextMock);

                var fa = nodeA.getFaultUid();
                var fb = nodeB.getFaultUid();
                var fc = nodeC.getFaultUid();

                // Then - it should prune the combination
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fc)));
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fb)));
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fb, fc)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fa)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fb)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fc)));
        }

        @Test
        public void testArity() {
                FeedbackContext contextMock = mock(FeedbackContext.class);
                ParentChildDetector pruner = new ParentChildDetector();

                // Given structure
                // -> A/a1 -> B/b1
                // -> C/c1
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder nodeB = nodeA.createChild()
                                .withPoint("B", "b1");
                EventBuilder nodeC = nodeA.createChild()
                                .withPoint("C", "c1");
                var trace = nodeA.buildTrace();
                var faultload1 = new Faultload(Set.of());

                // When pruner receives feedback
                var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
                pruner.handleFeedback(result, contextMock);

                var fa = nodeA.getFaultUid();
                var fb = nodeB.getFaultUid();
                var fc = nodeC.getFaultUid();

                // Then - it should prune the combination
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fc)));
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fc)));
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fb, fc)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fb, fc)));
        }

        @Test
        public void testNestedArity() {
                FeedbackContext contextMock = mock(FeedbackContext.class);
                ParentChildDetector pruner = new ParentChildDetector();

                // Given structure
                // -> A/a1 -> B/b1
                // -> C/c1 -> B/b1
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder nodeB = nodeA.createChild()
                                .withPoint("B", "b1");
                EventBuilder nodeC = nodeA.createChild()
                                .withPoint("C", "c1");
                EventBuilder nodeB2 = nodeC.createChild()
                                .withPoint("B", "b1");
                var trace = nodeA.buildTrace();
                var faultload1 = new Faultload(Set.of());

                // When pruner receives feedback
                var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
                pruner.handleFeedback(result, contextMock);

                var fa = nodeA.getFaultUid();
                var fb = nodeB.getFaultUid();
                var fc = nodeC.getFaultUid();
                var fcb = nodeB2.getFaultUid();

                // Then - it should prune the combination
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fa, fcb)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fcb, fb)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fc, fb)));
        }

        @Test
        public void testDifferentEndpoints() {
                FeedbackContext contextMock = mock(FeedbackContext.class);
                ParentChildDetector pruner = new ParentChildDetector();

                // Given structure
                // -> A/a1 -> (B/b1 | B/b2) --> C/c1
                EventBuilder nodeA = new EventBuilder()
                                .withPoint("A", "a1");
                EventBuilder nodeB1 = nodeA.createChild()
                                .withPoint("B", "b1");
                EventBuilder nodeB2 = nodeA.createChild()
                                .withPoint("B", "b2");
                EventBuilder nodeB1C = nodeB1.createChild()
                                .withPoint("C", "c1");
                EventBuilder nodeB2C = nodeB2.createChild()
                                .withPoint("C", "c1");
                var trace = nodeA.buildTrace();
                var faultload1 = new Faultload(Set.of());

                // When pruner receives feedback
                var result = new FaultloadResult(new TrackedFaultload(faultload1), trace, true);
                pruner.handleFeedback(result, contextMock);

                var fb1 = nodeB1.getFaultUid();
                var fb2 = nodeB2.getFaultUid();
                var fb1c = nodeB1C.getFaultUid();
                var fb2c = nodeB2C.getFaultUid();

                // Then - it should prune the combination
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fb1, fb1c)));
                assertEquals(PruneDecision.PRUNE_SUBTREE, pruner.prune(getFaultload(fb2, fb2c)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fb1, fb2)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fb1, fb2c)));
                assertEquals(PruneDecision.KEEP, pruner.prune(getFaultload(fb2, fb1c)));
        }
}
