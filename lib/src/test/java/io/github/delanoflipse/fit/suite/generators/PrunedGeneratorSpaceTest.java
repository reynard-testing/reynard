package io.github.delanoflipse.fit.suite.generators;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.generators.IncreasingSizeGenerator;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.SpaceEstimate;
import io.github.delanoflipse.fit.suite.testutil.Enumerate;
import io.github.delanoflipse.fit.suite.testutil.FailureModes;
import io.github.delanoflipse.fit.suite.testutil.FaultInjectionPoints;
import io.github.delanoflipse.fit.suite.testutil.FaultsBuilder;

public class PrunedGeneratorSpaceTest {

    @Test
    @Timeout(1000)
    public void testNoPoints() {
        var modes = FailureModes.getModes(3);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        var faults = generator.generate();
        assert faults == null;
    }

    @Test
    @Timeout(1000)
    public void testSkipAllPoints() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);
        generator.exploreFrom(Set.of());

        for (var fault : points) {
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        var faults = generator.generate();
        assert faults == null;
    }

    @Test
    @Timeout(1000)
    public void testVisitAll() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(4);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);
        generator.exploreFrom(Set.of());

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), points.size());
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testSkipAllButOnePoint() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);
        generator.exploreFrom(Set.of());

        var ignored = points.get(4);

        for (var fault : points) {
            if (fault == ignored) {
                continue;
            }

            generator.pruneFaultUidSubset(Set.of(fault));
        }

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), 1);
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testSkipAllButTwoPoint() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        var ignoredSet = Set.of(points.get(4), points.get(7));
        for (var fault : points) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        generator.exploreFrom(Set.of());

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), ignoredSet.size());
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testSkipAllButFourPoint() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        var ignoredSet = Set.of(points.get(4), points.get(7), points.get(2), points.get(8));

        for (var fault : points) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        generator.exploreFrom(Set.of());

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), 4);
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testRemoveOneFaultByModes() {
        var modes = FailureModes.getModes(5);
        var points = FaultInjectionPoints.getPoints(3);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        for (var mode : modes) {
            generator.pruneFaultSubset(Set.of(
                    new Fault(points.get(0), mode)));
        }

        generator.exploreFrom(Set.of());

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), 2);
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    // @Test(timeout = 1000)
    @Test
    @Timeout(1000)
    public void testOneFault() {
        var modes = FailureModes.getModes(5);
        var points = FaultInjectionPoints.getPoints(3);
        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        for (int i = 1; i < modes.size(); i++) {
            var mode = modes.get(i);
            generator.pruneFaultSubset(Set.of(new Fault(points.get(1), mode)));
        }

        generator.exploreFrom(Set.of());

        int expected = 71;
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testManyModes() {
        var modes = FailureModes.getModes(1000);
        var points = FaultInjectionPoints.getPoints(3);

        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        Set<Integer> ignored = Set.of(1, 500, 999);

        for (var fault : points) {
            for (int i = 0; i < modes.size(); i++) {
                if (ignored.contains(i)) {
                    continue;
                }

                var mode = modes.get(i);
                generator.pruneFaultSubset(Set.of(new Fault(fault, mode)));
            }
        }

        generator.exploreFrom(Set.of());

        int expected = 63;
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testManyFaults() {
        var modes = FailureModes.getModes(12);
        var points = FaultInjectionPoints.getPoints(6);

        var generator = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator.reportFaultUids(points);

        Set<Integer> ignored = Set.of(1, 4, 8);

        for (var fault : points) {
            for (int i = 0; i < modes.size(); i++) {
                if (ignored.contains(i)) {
                    continue;
                }

                var mode = modes.get(i);
                generator.pruneFaultSubset(Set.of(new Fault(fault, mode)));
            }
        }

        generator.exploreFrom(Set.of());
        int expected = 4095;
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testDoesNotContain() {
        // Given - points and modes
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(6);
        var faults = new FaultsBuilder(points, modes);

        // Given - a generator with no pruned faults
        var generator1 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator1.reportFaultUids(points);
        generator1.exploreFrom(Set.of());
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator2.reportFaultUids(points);

        var faultSubsets = Set.of(
                Set.of(faults.get(2, 0),
                        faults.get(3, 1)),
                Set.of(faults.get(3, 0),
                        faults.get(2, 0),
                        faults.get(1, 1)));

        var uidSubsets = Set.of(
                Set.of(
                        points.get(4),
                        points.get(5)),
                Set.of(
                        points.get(1),
                        points.get(5)));

        // When the faults are pruned
        for (var faultSubset : faultSubsets) {
            generator2.pruneFaultSubset(faultSubset);
        }
        for (var uidSubset : uidSubsets) {
            generator2.pruneFaultUidSubset(uidSubset);
        }

        generator2.exploreFrom(Set.of());
        Set<Faultload> allFaultloads2 = Enumerate.getGeneratedSet(generator2);

        // Then - for all faultloads in the full enumeration
        for (int i = 0; i < allFaultloads.size(); i++) {
            var faultload = allFaultloads.get(i);
            boolean shouldPruneByFaultSubset = generator2.getStore().hasFaultSubset(faultload.faultSet());
            boolean shouldPruneByUidSubset = generator2.getStore().hasFaultUidSubset(faultload.getFaultUids());
            boolean shouldPrune = shouldPruneByFaultSubset || shouldPruneByUidSubset;

            // Then, if it should be pruned, it should not be generated
            // Or, it should be generated
            boolean wasGenerated = allFaultloads2.contains(faultload);

            if (shouldPrune) {
                if (wasGenerated) {
                    assert false;
                }
            } else {
                if (!wasGenerated) {
                    assert false;
                }
            }
        }
    }

    // TODO: this currently fails when combining both types of subsets
    // but not when only using one type of subset
    @Test
    @Disabled
    @Timeout(1000)
    public void testSizeEstimate() {
        // Given - points and modes
        var modes = FailureModes.getModes(1);
        var points = FaultInjectionPoints.getPoints(5);
        var faults = new FaultsBuilder(points, modes);

        // Given - a generator with no pruned faults
        var generator1 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator1.reportFaultUids(points);
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator2.reportFaultUids(points);

        Set<Set<Fault>> faultSubsets = Set.of(
                Set.of(faults.get(1, 0),
                        faults.get(4, 0)),
                Set.of(faults.get(2, 0),
                        faults.get(3, 0)),
                Set.of(faults.get(3, 0),
                        // faults.get(4, 1),
                        faults.get(4, 0))
        // Set.of(faults.get(2, 1),
        // faults.get(1, 1),
        // faults.get(5, 2)),
        // Set.of(faults.get(3, 0),
        // faults.get(2, 0),
        // faults.get(1, 1))
        );

        Set<Set<FaultUid>> uidSubsets = Set.of(
        // Set.of(
        // points.get(1),
        // points.get(4),
        // points.get(5)),
        // Set.of(
        // points.get(4),
        // points.get(5)),
        // Set.of(
        // points.get(1),
        // points.get(5))
        );

        // When the faults are pruned
        for (var uidSubset : uidSubsets) {
            generator2.pruneFaultUidSubset(uidSubset);
        }

        for (var faultSubset : faultSubsets) {
            generator2.pruneFaultSubset(faultSubset);
        }

        Set<Faultload> allFaultloads2 = Enumerate.getGeneratedSet(generator2);
        long difference = allFaultloads.size() - allFaultloads2.size();
        long estimate = generator2.getStore().estimatePruned();
        assertEquals(difference, estimate);
    }

    @Test
    @Timeout(1000)
    public void testWithPreconditions() {
        // Given - points and modes
        var modes = FailureModes.getModes(2);
        var points = FaultInjectionPoints.getPoints(3);
        var point3 = FaultInjectionPoints.getPoint(3);
        var faults = new FaultsBuilder(points, modes);

        // Given - a generator with no pruned faults
        var generator1 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator1.reportFaultUids(points);
        generator1.reportFaultUid(point3);
        generator1.exploreFrom(Set.of());
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes, x -> PruneDecision.KEEP);
        generator2.reportFaultUids(points);

        var preconditions = List.of(
                Set.of(faults.get(0, 0)),
                Set.of(faults.get(2, 0)));

        generator2.exploreFrom(Set.of());
        for (var precondition : preconditions) {
            generator2.reportPreconditionOfFaultUid(Behaviour.of(precondition), point3);
            generator2.exploreFrom(precondition);
        }

        List<Faultload> allFaultloads2 = Enumerate.getGenerated(generator2);

        // Then - for all faultloads in the full enumeration
        for (int i = 0; i < allFaultloads.size(); i++) {
            var faultload = allFaultloads.get(i);
            boolean hasUid = faultload.getFaultUids().contains(point3);
            boolean hasPrecondition = preconditions.stream()
                    .anyMatch(pre -> Sets.isSubsetOf(pre, faultload.faultSet()));
            boolean shouldPrune = hasUid && !hasPrecondition;

            // Then, if it should be pruned, it should not be generated
            // Or, it should be generated
            boolean wasGenerated = allFaultloads2.contains(faultload);

            if (shouldPrune) {
                if (wasGenerated) {
                    assert false;
                }
            } else {
                if (!wasGenerated) {
                    assert false;
                }
            }
        }
    }

    @Test
    @Timeout(1000)
    public void testNewFound() {
        var modes = FailureModes.getModes(2);
        var points = FaultInjectionPoints.getPoints(4);
        var point5 = FaultInjectionPoints.getPoint(5);
        // spacesize = 3^5-1 = 242

        var pruneCounter = new AtomicInteger(0);
        Function<Set<Fault>, PruneDecision> pruneFunction = x -> {
            pruneCounter.incrementAndGet();
            return PruneDecision.KEEP;
        };

        // -- Generator 1 -- All points are kown up front
        var generator1 = new IncreasingSizeGenerator(modes, pruneFunction);
        generator1.reportFaultUids(points);
        generator1.reportFaultUid(point5);
        generator1.exploreFrom(Set.of());

        long complete = Enumerate.getGeneratedCount(generator1);
        long maxQueue1 = generator1.getMaxQueueSize();
        long pruneCount1 = pruneCounter.get();
        pruneCounter.set(0);

        // -- Generator 2 -- All points except one are known up front
        var generator2 = new IncreasingSizeGenerator(modes, pruneFunction);
        generator2.reportFaultUids(points);
        generator2.exploreFrom(Set.of());
        generator2.reportFaultUid(point5);
        generator2.exploreFrom(Set.of());

        long laterDiscovery = Enumerate.getGeneratedCount(generator2);
        long maxQueue2 = generator2.getMaxQueueSize();
        long pruneCount2 = pruneCounter.get();
        pruneCounter.set(0);
        assertEquals(complete, laterDiscovery);
        assertTrue(maxQueue1 < maxQueue2);
        // assertEquals(complete, pruneCount2);

        // -- Generator 3 -- All points except one are known up front, but we explore
        // directly from the point
        var generator3 = new IncreasingSizeGenerator(modes, pruneFunction);
        generator3.reportFaultUids(points);
        generator3.exploreFrom(Set.of());
        generator3.reportFaultUid(point5);

        for (var mode : modes) {
            generator3.exploreFrom(Set.of(new Fault(point5, mode)));
        }

        long differentExploration = Enumerate.getGeneratedCount(generator3);
        long maxQueue3 = generator3.getMaxQueueSize();
        long pruneCount3 = pruneCounter.get();
        pruneCounter.set(0);
        assertEquals(complete, differentExploration);
        assertTrue(maxQueue1 == maxQueue3);
        assertTrue(pruneCount1 == pruneCount2);
        assertTrue(pruneCount3 == pruneCount2);
    }
}
