package nl.dflipse.fit.generators;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.faultload.faultmodes.HttpError;
import nl.dflipse.fit.strategy.generators.BreadthFirstGenerator;
import nl.dflipse.fit.strategy.generators.DepthFirstGenerator;
import nl.dflipse.fit.strategy.generators.Generator;
import nl.dflipse.fit.strategy.generators.RandomPowersetGenerator;
import nl.dflipse.fit.strategy.util.Combinatorics;

public class CompletenessTest {
    private static FaultMode mode = ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE);

    private static List<FaultUid> faultUids = List.of(
            new FaultUid("x", "y", "123", "p1", 0),
            new FaultUid("b", "x", "456", "p2", 0),
            new FaultUid("b", "x", "456", "p2", 1),
            new FaultUid("z", "d", "789", "p3", 0));

    private static List<Fault> faults = faultUids
            .stream()
            .map(faultUid -> new Fault(faultUid, mode))
            .collect(Collectors.toList());

    private static List<List<Fault>> powerset = Combinatorics.generatePowerSet(faults);
    private static Set<Set<Fault>> powersetSet = powerset
            .stream()
            .filter(x -> !x.isEmpty())
            .map(faultList -> Set.copyOf(faultList))
            .collect(Collectors.toSet());

    static Stream<Arguments> generatorArguments() {
        return Stream.of(
                Arguments.of(new DepthFirstGenerator(List.of(mode))),
                Arguments.of(new BreadthFirstGenerator(List.of(mode))),
                Arguments.of(new RandomPowersetGenerator(List.of(mode))));
    }

    /*
     * Invariant for generators: They generate the powerset, order does not matter
     */
    @Test
    @ParameterizedTest
    @MethodSource("generatorArguments")
    public void testAllCombinationsGenerated(Generator generator) {
        // Arrange: keep track of all combinations that need to be generated
        Set<Set<Fault>> toGenerate = new HashSet<>(powersetSet);
        generator.reportFaultUids(faultUids);
        int casesGenerated = 0;
        int shouldGenerate = powersetSet.size();

        // Act: generate faultloads until generator is exhausted
        while (true) {
            List<Faultload> faultloads = generator.generate();
            if (faultloads.isEmpty()) {
                break;
            }

            for (var faultload : faultloads) {
                Set<Fault> faultsInLoad = faultload.faultSet();
                toGenerate.remove(faultsInLoad);
                casesGenerated++;
            }
        }

        // Assert: the generator should have generated all combinations (in potentially
        // different orders)
        assert toGenerate.isEmpty();
        assert casesGenerated == shouldGenerate;
    }
}
