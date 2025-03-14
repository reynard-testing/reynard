package nl.dflipse.fit.generators;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.faultload.faultmodes.HttpError;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.IncreasingSizeGenerator;

public class PrunedGeneratorSpaceTest {
    private List<FaultMode> modes3 = List.of(
            ErrorFault.fromError(HttpError.BAD_GATEWAY),
            ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT),
            ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR));
    private List<FaultMode> modes5 = List.of(
            ErrorFault.fromError(HttpError.BAD_GATEWAY),
            ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT),
            ErrorFault.fromError(HttpError.HTTP_VERSION_NOT_SUPPORTED),
            ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE),
            ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR));

    private List<FaultMode> modes12 = List.of(
            new FaultMode("x", List.of("1")),
            new FaultMode("x", List.of("2")),
            new FaultMode("x", List.of("3")),
            new FaultMode("x", List.of("4")),
            new FaultMode("x", List.of("5")),
            new FaultMode("x", List.of("6")),
            new FaultMode("x", List.of("7")),
            new FaultMode("x", List.of("8")),
            new FaultMode("x", List.of("9")),
            new FaultMode("x", List.of("10")),
            new FaultMode("x", List.of("11")),
            new FaultMode("x", List.of("12")));

    private int fidCounter = 0;

    private FaultUid newFault() {
        return new FaultUid("x", "y", "123", "p1", fidCounter++);
    }

    private List<FaultUid> pointsTwelve = List.of(
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault());

    private List<FaultUid> pointsSix = List.of(
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault(),
            newFault());

    private List<FaultUid> pointsThree = List.of(
            newFault(),
            newFault(),
            newFault());

    @Test(timeout = 100)
    public void testSkipAllPoints() {
        var generator = new IncreasingSizeGenerator(modes3);
        generator.reportFaultUids(pointsTwelve);

        for (var fault : pointsTwelve) {
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        var faults = generator.generate();
        assert faults.size() == 0;
    }

    private int expectedSize(int n, int m) {
        return (int) Math.pow(1 + m, n) - 1;
    }

    private int getGenerated(Generator gen) {
        int i = 0;
        while (gen.generate().size() > 0) {
            i++;
        }
        return i;
    }

    @Test(timeout = 1000)
    public void testSkipAllButOnePoint() {
        var generator = new IncreasingSizeGenerator(modes3);
        generator.reportFaultUids(pointsTwelve);

        var ignored = pointsTwelve.get(4);
        for (var fault : pointsTwelve) {
            if (fault == ignored) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        assertEquals(expectedSize(1, modes3.size()), getGenerated(generator));
    }

    @Test(timeout = 1000)
    public void testSkipAllButTwoPoint() {
        var generator = new IncreasingSizeGenerator(modes3);
        generator.reportFaultUids(pointsTwelve);

        var ignoredSet = Set.of(pointsTwelve.get(4), pointsTwelve.get(7));
        for (var fault : pointsTwelve) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        assertEquals(expectedSize(2, modes3.size()), getGenerated(generator));
    }

    @Test(timeout = 1000)
    public void testSkipAllButFourPoint() {
        var generator = new IncreasingSizeGenerator(modes3);
        generator.reportFaultUids(pointsTwelve);

        var ignoredSet = Set.of(pointsTwelve.get(4), pointsTwelve.get(7), pointsTwelve.get(2), pointsTwelve.get(8));
        for (var fault : pointsTwelve) {
            if (ignoredSet.contains(fault)) {
                continue;
            }
            generator.pruneFaultUidSubset(Set.of(fault));
        }

        assertEquals(expectedSize(4, modes3.size()), getGenerated(generator));
    }

    // @Test(timeout = 1000)
    @Test
    public void testNoFaults() {
        var generator = new IncreasingSizeGenerator(modes5);
        generator.reportFaultUids(pointsThree);

        for (var mode : modes5) {
            generator.pruneFaultSubset(Set.of(
                    new Fault(pointsThree.get(0), mode)));
        }

        int expectedSize = 0;
        assertEquals(expectedSize, getGenerated(generator));
    }

    // @Test(timeout = 1000)
    @Test
    public void testOneFault() {
        var generator = new IncreasingSizeGenerator(modes5);
        generator.reportFaultUids(pointsThree);

        for (int i = 1; i < modes5.size(); i++) {
            var mode = modes5.get(i);
            generator.pruneFaultSubset(Set.of(
                    new Fault(pointsThree.get(1), mode)));
        }

        int expectedSize = 71;
        assertEquals(expectedSize, getGenerated(generator));
    }

    @Test
    public void testManyModes() {
        List<FaultMode> modes = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            modes.add(new FaultMode("x", List.of("" + i)));
        }

        var generator = new IncreasingSizeGenerator(modes);
        generator.reportFaultUids(pointsThree);

        Set<Integer> ignored = Set.of(1, 500, 999);

        for (var fault : pointsThree) {
            for (int i = 0; i < modes.size(); i++) {
                if (ignored.contains(i)) {
                    continue;
                }

                var mode = modes.get(i);
                generator.pruneFaultSubset(Set.of(new Fault(fault, mode)));
            }
        }

        int expectedSize = 63;
        assertEquals(expectedSize, getGenerated(generator));
    }

    @Test
    public void testManyFaults() {

        var generator = new IncreasingSizeGenerator(modes12);
        generator.reportFaultUids(pointsSix);

        Set<Integer> ignored = Set.of(1, 4, 8);

        for (var fault : pointsSix) {
            for (int i = 0; i < modes12.size(); i++) {
                if (ignored.contains(i)) {
                    continue;
                }

                var mode = modes12.get(i);
                generator.pruneFaultSubset(Set.of(new Fault(fault, mode)));
            }
        }

        int expectedSize = 4095;
        assertEquals(expectedSize, getGenerated(generator));
    }
}
