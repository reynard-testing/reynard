package dev.reynard.junit.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.ErrorFault;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.faultload.modes.HttpError;
import dev.reynard.junit.instrumentation.trace.tree.TraceReport;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.StrategyRunner;
import dev.reynard.junit.strategy.TrackedFaultload;
import dev.reynard.junit.strategy.components.analyzers.BehaviorAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.ConcurrencyDetector;
import dev.reynard.junit.strategy.components.analyzers.ConditionalPointDetector;
import dev.reynard.junit.strategy.components.analyzers.ErrorPropagationDetector;
import dev.reynard.junit.strategy.components.analyzers.FaultsWithoutCauseOracle;
import dev.reynard.junit.strategy.components.analyzers.HappensBeforeNeighbourDetector;
import dev.reynard.junit.strategy.components.analyzers.HappyPathDetector;
import dev.reynard.junit.strategy.components.analyzers.ParentChildDetector;
import dev.reynard.junit.strategy.components.analyzers.RedundancyAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.StatusAnalyzer;
import dev.reynard.junit.strategy.components.analyzers.StatusPropagationOracle;
import dev.reynard.junit.strategy.components.analyzers.TimingAnalyzer;
import dev.reynard.junit.strategy.components.generators.DynamicExplorationGenerator;
import dev.reynard.junit.strategy.components.pruners.DynamicReductionPruner;
import dev.reynard.junit.strategy.components.pruners.NoImpactPruner;
import dev.reynard.junit.strategy.components.pruners.UnreachabilityPruner;
import dev.reynard.junit.strategy.store.ImplicationsModel;
import dev.reynard.junit.strategy.store.ImplicationsStore;
import dev.reynard.junit.strategy.util.TraceAnalysis;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;
import dev.reynard.junit.unit.generators.DynamicExplorationTest;
import dev.reynard.junit.util.EventBuilder;

/**
 * Measures the cost of the search itself (generating and pruning faultloads),
 * against a simulated system, so no containers are needed.
 *
 * Not part of the default test run (surefire only picks up *Test classes). Run
 * with: mvn test -Dtest=StrategyBenchmark [-Dbench.width=6 -Dbench.depth=4
 * -Dbench.tests=1500]
 *
 * Compare "search ms" before and after a change; "explored" must stay equal,
 * or the change altered which faultloads are explored.
 */
public class StrategyBenchmark {
    private static final List<FailureMode> MODES = List.of(
            ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR),
            ErrorFault.fromError(HttpError.BAD_GATEWAY),
            ErrorFault.fromError(HttpError.SERVICE_UNAVAILABLE),
            ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT));
    private static final FailureMode PROPAGATED = ErrorFault.fromError(HttpError.INTERNAL_SERVER_ERROR);

    /**
     * Ground truth: A calls B1..Bw, each Bi calls Ci1..Cid. A failing Bi, or an
     * odd Cij, makes the caller fail too; even Cij are handled by a fallback.
     * Bi retries Ci1 once when it fails.
     */
    static ImplicationsStore system(int width, int depth) {
        ImplicationsStore store = new ImplicationsStore();
        EventBuilder a = new EventBuilder("A");
        List<EventBuilder> bs = new ArrayList<>();
        for (int i = 1; i <= width; i++) {
            EventBuilder b = a.createChild("B" + i);
            bs.add(b);

            List<EventBuilder> cs = new ArrayList<>();
            for (int j = 1; j <= depth; j++) {
                cs.add(b.createChild("C" + i + "_" + j));
            }
            store.addDownstreamRequests(b.uid(), cs.stream().map(EventBuilder::uid).toList());

            EventBuilder retry = b.createChild().withPoint("C" + i + "_1", "C" + i + "_11", 1);
            for (FailureMode mode : MODES) {
                store.addInclusionEffect(Set.of(cs.get(0).behaviour().asMode(mode)), retry.uid());
                for (int j = 1; j <= depth; j += 2) {
                    store.addUpstreamResponse(Set.of(cs.get(j - 1).behaviour().asMode(mode)),
                            new Behaviour(b.uid(), PROPAGATED));
                }
                store.addUpstreamResponse(Set.of(b.behaviour().asMode(mode)), new Behaviour(a.uid(), PROPAGATED));
            }
        }
        store.addDownstreamRequests(a.uid(), bs.stream().map(EventBuilder::uid).toList());
        return store;
    }

    static TraceAnalysis run(Faultload faultload, ImplicationsStore system) {
        var root = system.getRootCause();
        List<TraceReport> reports = new ArrayList<>();
        for (var behaviour : new ImplicationsModel(system).getBehaviours(faultload.faultSet())) {
            TraceReport report = DynamicExplorationTest.asReport(behaviour, faultload);
            report.isInitial = behaviour.uid().matches(root);
            reports.add(report);
        }
        return new TraceAnalysis(reports);
    }

    // Mirrors the components registered by FiTestExtension with default @FiTest options
    static StrategyRunner defaultStrategy(long maxTests) {
        StrategyRunner strategy = new StrategyRunner(MODES);
        strategy
                .withComponent(new DynamicExplorationGenerator(strategy.getStore(), strategy::prune,
                        TraversalOrder.DEPTH_FIRST_POST_ORDER, true))
                .withComponent(new HappyPathDetector())
                .withComponent(new ParentChildDetector())
                .withComponent(new HappensBeforeNeighbourDetector())
                .withComponent(new ErrorPropagationDetector())
                .withComponent(new ConditionalPointDetector(false))
                .withComponent(new RedundancyAnalyzer())
                .withComponent(new StatusAnalyzer())
                .withComponent(new BehaviorAnalyzer())
                .withComponent(new TimingAnalyzer())
                .withComponent(new StatusPropagationOracle())
                .withComponent(new FaultsWithoutCauseOracle())
                .withComponent(new ConcurrencyDetector())
                .withComponent(new UnreachabilityPruner())
                .withComponent(new NoImpactPruner(false))
                .withComponent(new DynamicReductionPruner())
                .withMaxTestCases(maxTests);
        return strategy;
    }

    @Test
    public void benchmarkSearch() {
        // INFO logging per faultload would dominate the measurement
        Configurator.setLevel("dev.reynard", Level.WARN);
        int width = Integer.getInteger("bench.width", 6);
        int depth = Integer.getInteger("bench.depth", 4);
        long maxTests = Long.getLong("bench.tests", 1500);

        ImplicationsStore system = system(width, depth);
        StrategyRunner strategy = defaultStrategy(maxTests);

        long searchNs = 0;
        long feedbackNs = 0;
        // Sorted, so the fingerprint does not depend on hash iteration order
        TreeSet<String> explored = new TreeSet<>();
        while (true) {
            long start = System.nanoTime();
            TrackedFaultload next = strategy.nextFaultload();
            searchNs += System.nanoTime() - start;
            if (next == null) {
                break;
            }
            explored.add(new TreeSet<>(next.getFaultload().faultSet().stream().map(Object::toString).toList())
                    .toString());

            TraceAnalysis trace = run(next.getFaultload(), system);
            start = System.nanoTime();
            strategy.handleResult(new FaultloadResult(next, trace, true));
            feedbackNs += System.nanoTime() - start;
        }

        System.out.printf(
                "%n[benchmark] width=%d depth=%d | explored=%d (fingerprint %08x) | search ms=%d | feedback ms=%d%n%n",
                width, depth, explored.size(), explored.hashCode(), searchNs / 1_000_000, feedbackNs / 1_000_000);
        assertTrue(explored.size() > 1, "the simulated system should be explored beyond the happy path");
    }
}
