package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.generators.Generator;

public class E2eStrategyTest {

    private class DummyGenerator implements Generator {

        private final List<Faultload> queue;
        private Set<FaultMode> modes;

        public DummyGenerator(List<Faultload> queue) {
            for (var faultload : queue) {
                for (var fault : faultload.faultSet()) {
                    modes.add(fault.mode());
                }
            }
            this.queue = queue;
        }

        @Override
        public List<Faultload> generate() {
            if (queue.isEmpty()) {
                return null;
            }

            return List.of(queue.remove(0));
        }

        @Override
        public void reportFaultUids(List<FaultUid> faultInjectionPoints) {

        }

        @Override
        public long pruneFaultUidSubset(Set<FaultUid> subset) {
            return 0;
        }

        @Override
        public long pruneFaultSubset(Set<Fault> subset) {
            return 0;
        }

        @Override
        public long pruneFaultload(Faultload faultload) {
            return 0;
        }

        @Override
        public long spaceSize() {
            return 0;
        }

        @Override
        public Set<FaultMode> getFaultModes() {
            return modes;
        }

        @Override
        public void reportConditionalFaultUid(Set<Fault> subset, FaultUid faultInjectionPoints) {

        }

        @Override
        public Set<FaultUid> getFaultInjectionPoints() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getFaultInjectionPoints'");
        }
    }

    @Test
    public void testEmptyGenerator() {
        StrategyRunner strategyRunner = new StrategyRunner()
                .withGenerator(new DummyGenerator(List.of()));

        var firstEmpty = strategyRunner.nextFaultload();
        assert firstEmpty.size() == 0;
        var next = strategyRunner.nextFaultload();
        assert next == null;
    }

    @Test
    public void testGenerator() {
        Fault fault = new Fault(new FaultUid(null, null, null, null, 0), new FaultMode("ASDF", List.of("x", "y")));
        Set<Fault> faults = Set.of(fault);
        List<Faultload> queue = new ArrayList<>();
        queue.add(new Faultload(faults));

        StrategyRunner strategyRunner = new StrategyRunner()
                .withGenerator(new DummyGenerator(queue));

        var firstEmpty = strategyRunner.nextFaultload();
        assert firstEmpty.size() == 0;
        strategyRunner.generateAndPruneTillNext();
        var next = strategyRunner.nextFaultload();
        assertEquals(next.size(), 1);
        assertEquals(next.getFaultload().faultSet(), faults);
    }

}