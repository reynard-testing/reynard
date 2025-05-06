package io.github.delanoflipse.fit.suite.strategy.components.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.StrategyReporter;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;
import io.github.delanoflipse.fit.suite.strategy.util.Lists;
import io.github.delanoflipse.fit.suite.strategy.util.Sets;
import io.github.delanoflipse.fit.suite.strategy.util.Simplify;
import io.github.delanoflipse.fit.suite.strategy.util.TraceAnalysis.TraversalStrategy;

public class DynamicExplorationGenerator extends StoreBasedGenerator implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(DynamicExplorationGenerator.class);

    private final List<TreeNode> toVisit = new ArrayList<>();
    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();
    private final List<Integer> queueSize = new ArrayList<>();
    private final TraversalStrategy traversalStrategy;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalStrategy traversalStrategy) {
        super(store);
        this.pruneFunction = pruneFunction;
        this.traversalStrategy = traversalStrategy;
    }

    public DynamicExplorationGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction, TraversalStrategy.BREADTH_FIRST);
    }

    public record TreeNode(Set<Fault> value) {

        // For equality, the list is a set
        @Override
        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (o instanceof TreeNode other) {
                return value.equals(other.value);
            }

            return false;
        }

        // In the hashcode, we use the set representation
        // So in a hashset, our equality check still works
        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }
    }

    private void updateQueueSize() {
        queueSize.add(toVisit.size());
    }

    private void expand(TreeNode node, List<FaultUid> expansion) {
        if (expansion.isEmpty()) {
            return;
        }

        for (var i = 0; i < expansion.size(); i++) {
            var point = expansion.get(i);
            for (Fault newFault : Fault.allFaults(point, getFailureModes())) {
                TreeNode newNode = new TreeNode(Sets.plus(node.value, newFault));
                addNode(newNode);
            }
        }
    }

    private PruneDecision pruneFunction(TreeNode node) {
        return PruneDecision.max(store.isRedundant(node.value), pruneFunction.apply(node.value));
    }

    @Override
    public Faultload generate() {
        long ops = 0;
        int orders = 2;

        while (!toVisit.isEmpty()) {
            // 1. Get a new node to visit from the node queue
            TreeNode node = toVisit.remove(0);
            visitedNodes.add(node);

            long order = (long) Math.pow(10, orders);
            if (ops++ > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }

            switch (pruneFunction(node)) {
                case PRUNE_SUPERSETS -> {
                    logger.debug("Pruning node {} completely", node);
                }

                case PRUNE -> {
                    logger.debug("Pruning node {} completely", node);
                }

                case KEEP -> {
                    logger.info("Found a candidate after {} attempt(s)", ops);
                    return new Faultload(node.value);
                }
            }

            updateQueueSize();
        }

        logger.info("Found no candidate after {} attempt(s)!", ops);
        return null;
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        TreeNode node = new TreeNode(Set.copyOf(startingNode));
        boolean isNew = addNode(node);

        if (isNew) {
            logger.info("Exploring new point {}", node);
        }

        return isNew;
    }

    private boolean addNode(TreeNode node) {
        if (visitedNodes.contains(node) || toVisit.contains(node)) {
            return false;
        }

        int insertionIndex = Lists.addBefore(toVisit, node, x -> x.value.size() > node.value.size());

        if (insertionIndex == -1) {
            logger.debug("Adding {} to end of the queue", node);
        } else {
            logger.debug("Adding {} to queue at index {}", node, insertionIndex);
        }

        return true;

    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> observed = result.trace.getFaultUids(traversalStrategy);

        for (var point : observed) {
            context.reportFaultUid(point);
        }

        // Also included known points that are similar (e.g., persistent faults)
        List<FaultUid> knownObserved = context.getFaultInjectionPoints().stream()
                .filter(p -> FaultUid.contains(observed, p))
                .toList();

        Set<Fault> injected = result.trace.getInjectedFaults();
        List<FaultUid> injectedPoints = injected.stream()
                .map(Fault::uid)
                .toList();

        List<FaultUid> toExplore = knownObserved.stream()
                .filter(p -> !FaultUid.contains(injectedPoints, p))
                .toList();

        TreeNode currentNode = new TreeNode(Set.copyOf(injected));
        expand(currentNode, toExplore);
    }

    public int getMaxQueueSize() {
        return queueSize.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    public int getQueuSize() {
        return toVisit.size();
    }

    @Override
    public Map<String, String> report(PruneContext context) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("Fault injection points", String.valueOf(getNumerOfPoints()));
        report.put("Modes", String.valueOf(getFailureModes().size()));
        report.put("Redundant faultloads", String.valueOf(store.getRedundantFaultloads().size()));
        report.put("Redundant fault points", String.valueOf(store.getRedundantUidSubsets().size()));
        report.put("Redundant fault subsets", String.valueOf(store.getRedundantFaultSubsets().size()));
        report.put("Max queue size", String.valueOf(getMaxQueueSize()));
        int qs = getQueuSize();
        if (qs > 0) {
            report.put("Queue size (left)", String.valueOf(getQueuSize()));
        }

        int i = 0;
        for (var point : getFaultInjectionPoints()) {
            report.put("FID(" + i + ")", point.toString());
            i++;
        }

        i = 0;
        for (var point : getSimplifiedFaultInjectionPoints()) {
            report.put("FID [simplified] (" + i + ")", point.toString());
            i++;
        }

        // Report the visited faultloads
        var faultloads = store.getHistoricResults().stream()
                .map(x -> x.first())
                .toList();

        // Unique visited
        Map<String, String> simplifiedReport = new LinkedHashMap<>();
        var simplified = Simplify.simplify(faultloads, getFailureModes());
        var failureModesCount = getFailureModes().size();
        int simpleIndex = 1;
        for (Set<FaultUid> cause : simplified.second()) {
            int start = simpleIndex;
            int increase = (int) Math.pow(failureModesCount, cause.size());
            simpleIndex += increase;
            String key = "[" + start + " - " + (start + increase - 1) + " (+" + increase + ")" + "]";
            if (cause.isEmpty()) {
                simplifiedReport.put(key, "(initial empty set)");
                continue;
            }

            simplifiedReport.put(key, cause.toString() + " (all modes)");
        }

        for (Set<Fault> cause : simplified.first()) {
            simplifiedReport.put("[" + simpleIndex + "]", cause.toString());
            simpleIndex++;
        }

        StrategyReporter.printReport("Visited (simplified)", simplifiedReport);

        return report;
    }
}
