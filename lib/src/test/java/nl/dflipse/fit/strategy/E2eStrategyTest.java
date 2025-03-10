package nl.dflipse.fit.strategy;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.generators.Generator;

public class E2eStrategyTest {

    private class DummyGenerator implements Generator {

        private List<Faultload> queue;

        public DummyGenerator(List<Faultload> queue) {
            this.queue = queue;
        }

        @Override
        public void mockFaultUids(List<FaultUid> faultUids) {
            return;
        }

        @Override
        public List<Faultload> generate() {
            if (queue.isEmpty()) {
                return null;
            }

            return List.of(queue.remove(0));
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