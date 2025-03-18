package nl.dflipse.fit.generators;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeMixedGenerator;
import nl.dflipse.fit.util.FailureModes;
import nl.dflipse.fit.util.FaultInjectionPoints;

public class GeneratorsEquivTest {

    private Set<Faultload> expandComplete(Generator generator) {
        Set<Faultload> faultloads = new HashSet<>();
        while (true) {
            var newFaultloads = generator.generate();

            if (newFaultloads.size() == 0) {
                break;
            }

            faultloads.addAll(newFaultloads);
        }
        return faultloads;
    }

    @Test
    public void testEquivGeneratedAll() {
        List<FaultMode> modes = FailureModes.getModes(3);
        List<FaultUid> points = FaultInjectionPoints.getPoints(4);
        var gen1 = new IncreasingSizeMixedGenerator(modes);
        gen1.reportFaultUids(points);
        var gen2 = new IncreasingSizeGenerator(modes);
        gen2.reportFaultUids(points);

        var faultloads1 = expandComplete(gen1);
        var faultloads2 = expandComplete(gen2);
        assertEquals(faultloads1, faultloads2);
    }

    @Test
    public void testEquivPrunedSmall() {
        List<FaultMode> modes = FailureModes.getModes(2);
        List<FaultUid> points = FaultInjectionPoints.getPoints(3);

        var gen1 = new IncreasingSizeMixedGenerator(modes);
        gen1.reportFaultUids(points);
        var gen2 = new IncreasingSizeGenerator(modes);
        gen2.reportFaultUids(points);

        List<Set<FaultUid>> redundantUids = List.of(
                Set.of(points.get(1), points.get(2)));

        for (var uids : redundantUids) {
            gen1.pruneFaultUidSubset(uids);
            gen2.pruneFaultUidSubset(uids);
        }

        List<Set<Fault>> redundantFaults = List.of(
                Set.of(new Fault(points.get(0), modes.get(0)), new Fault(points.get(2), modes.get(1))),
                Set.of(new Fault(points.get(1), modes.get(0)), new Fault(points.get(2), modes.get(1))));

        for (var faults : redundantFaults) {
            gen1.pruneFaultSubset(faults);
            gen2.pruneFaultSubset(faults);
        }

        var faultloads1 = expandComplete(gen1);
        var faultloads2 = expandComplete(gen2);
        assertEquals(faultloads1.size(), faultloads2.size());
        assertEquals(faultloads1, faultloads2);
    }

    @Test
    public void testEquivPruned() {
        List<FaultMode> modes = FailureModes.getModes(4);
        List<FaultUid> points = FaultInjectionPoints.getPoints(6);

        var gen1 = new IncreasingSizeMixedGenerator(modes);
        gen1.reportFaultUids(points);
        var gen2 = new IncreasingSizeGenerator(modes);
        gen2.reportFaultUids(points);

        List<Set<FaultUid>> redundantUids = List.of(
                Set.of(points.get(0), points.get(1)),
                Set.of(points.get(0), points.get(2)),
                Set.of(points.get(2), points.get(3)));

        for (var uids : redundantUids) {
            gen1.pruneFaultUidSubset(uids);
            gen2.pruneFaultUidSubset(uids);
        }

        List<Set<Fault>> redundantFaults = List.of(
                Set.of(new Fault(points.get(4), modes.get(0)), new Fault(points.get(3), modes.get(1))),
                Set.of(new Fault(points.get(3), modes.get(1)), new Fault(points.get(1), modes.get(1))));

        for (var faults : redundantFaults) {
            gen1.pruneFaultSubset(faults);
            gen2.pruneFaultSubset(faults);
        }

        var faultloads1 = expandComplete(gen1);
        var faultloads2 = expandComplete(gen2);
        assertEquals(faultloads1.size(), faultloads2.size());
        assertEquals(faultloads1, faultloads2);
    }

    // @Test
    // public void testSize() {
    // List<FaultMode> modes = FailureModes.getModes(12);
    // List<FaultUid> points = FaultInjectionPoints.getPoints(5);
    // var gen1 = new IncreasingSizeMixedGenerator(modes);
    // gen1.reportFaultUids(points);
    // var gen2 = new IncreasingSizeGenerator(modes);
    // gen2.reportFaultUids(points);
    // }
}
