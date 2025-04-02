package nl.dflipse.fit.generators;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.SpaceEstimate;
import nl.dflipse.fit.util.Enumerate;
import nl.dflipse.fit.util.FailureModes;
import nl.dflipse.fit.util.FaultInjectionPoints;
import nl.dflipse.fit.util.FaultsBuilder;

public class PrunedGeneratorSpaceTest {

    @Test
    @Timeout(1000)
    public void testSkipAllPoints() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

        for (var fault : points) {
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        var faults = generator.generate();
        assert faults.size() == 0;
    }

    @Test
    @Timeout(1000)
    public void testSkipAllButOnePoint() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

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
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

        var ignoredSet = Set.of(points.get(4), points.get(7));
        for (var fault : points) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), 2);
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testSkipAllButFourPoint() {
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(12);
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

        var ignoredSet = Set.of(points.get(4), points.get(7), points.get(2), points.get(8));

        for (var fault : points) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        long expected = SpaceEstimate.nonEmptySpaceSize(modes.size(), 4);
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testRemoveOneFaultByModes() {
        var modes = FailureModes.getModes(5);
        var points = FaultInjectionPoints.getPoints(3);
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

        for (var mode : modes) {
            generator.pruneFaultSubset(Set.of(
                    new Fault(points.get(0), mode)));
        }

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
        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(points);

        for (int i = 1; i < modes.size(); i++) {
            var mode = modes.get(i);
            generator.pruneFaultSubset(Set.of(new Fault(points.get(1), mode)));
        }

        int expected = 71;
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testManyModes() {
        var modes = FailureModes.getModes(1000);
        var points = FaultInjectionPoints.getPoints(3);

        var generator = new IncreasingSizeGenerator(modes);
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

        int expected = 63;
        long actual = Enumerate.getGeneratedCount(generator);
        assertEquals(expected, actual);
    }

    @Test
    @Timeout(1000)
    public void testManyFaults() {
        var modes = FailureModes.getModes(12);
        var points = FaultInjectionPoints.getPoints(6);

        var generator = new IncreasingSizeGenerator(modes);
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
        var generator1 = new IncreasingSizeGenerator(modes);
        generator1.reportFaultUids(points);
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes);
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
    @Timeout(1000)
    public void testSizeEstimate() {
        // Given - points and modes
        var modes = FailureModes.getModes(3);
        var points = FaultInjectionPoints.getPoints(6);
        var faults = new FaultsBuilder(points, modes);

        // Given - a generator with no pruned faults
        var generator1 = new IncreasingSizeGenerator(modes);
        generator1.reportFaultUids(points);
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes);
        generator2.reportFaultUids(points);

        var faultSubsets = Set.of(
                Set.of(faults.get(2, 0),
                        faults.get(3, 1)),
                Set.of(faults.get(3, 1),
                        faults.get(4, 1),
                        faults.get(5, 2)),
                Set.of(faults.get(2, 1),
                        faults.get(1, 1),
                        faults.get(5, 2)),
                Set.of(faults.get(3, 0),
                        faults.get(2, 0),
                        faults.get(1, 1)));

        var uidSubsets = Set.of(
                Set.of(
                        points.get(1),
                        points.get(4),
                        points.get(5)),
                Set.of(
                        points.get(4),
                        points.get(5)),
                Set.of(
                        points.get(1),
                        points.get(5)));

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
        var generator1 = new IncreasingSizeGenerator(modes);
        generator1.reportFaultUids(points);
        generator1.reportPreconditionOfFaultUid(Set.of(), point3);
        List<Faultload> allFaultloads = Enumerate.getGenerated(generator1);

        // Given a generator and prunable faults
        var generator2 = new IncreasingSizeGenerator(modes);
        generator2.reportFaultUids(points);

        var preconditions = List.of(
                Set.of(faults.get(0, 0)),
                Set.of(faults.get(2, 0)));

        for (var precondition : preconditions) {
            generator2.reportPreconditionOfFaultUid(precondition, point3);
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
}
