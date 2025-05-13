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
    private final Map<TreeNode, List<TreeNode>> expansionTree = new LinkedHashMap<>();
    private int nodeCounter = 0;
    private final Map<TreeNode, Integer> nodeIndex = new LinkedHashMap<>();
    private final List<Integer> queueSize = new ArrayList<>();
    private final TraversalStrategy traversalStrategy;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalStrategy traversalStrategy) {
        super(store);
        this.pruneFunction = pruneFunction;
        this.traversalStrategy = traversalStrategy;

        nodeIndex.put(new TreeNode(Set.of()), 0);
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
                boolean expanded = addNode(newNode);
                if (expanded) {
                    expansionTree.putIfAbsent(node, new ArrayList<>());
                    expansionTree.get(node).add(newNode);
                }
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
                    nodeCounter++;
                    nodeIndex.put(node, nodeCounter);
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

        // Note: we could already prune any faults that contain a causual parent
        // currenlty, this is only caught by the reachability pruner
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

    public double getAvgQueueSize() {
        return queueSize.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    public int getQueuSize() {
        return toVisit.size();
    }

    private Object buildTreeReport(TreeNode node, TreeNode parent) {
        if (!nodeIndex.containsKey(node)) {
            return null;
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("index", nodeIndex.get(node));

        if (parent == null) {
            report.put("node", node.value.toString());
        } else {
            Fault addition = Sets.getOnlyElement(Sets.difference(node.value, parent.value));
            report.put("node", addition.toString());
        }

        if (nodeIndex.containsKey(node)) {
            report.put("index", nodeIndex.get(node));
        }

        List<TreeNode> children = expansionTree.get(node);
        if (children == null || children.isEmpty()) {
            return report;
        }

        List<Object> childReports = new ArrayList<>();
        for (var child : children) {
            var childReport = buildTreeReport(child, node);
            if (childReport == null) {
                continue;
            }
            childReports.add(childReport);
        }

        if (childReports.isEmpty()) {
            return report;
        }

        report.put("children", childReports);
        return report;
    }

    @Override
    public Object report(PruneContext context) {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> details = new LinkedHashMap<>();

        List<String> points = getFaultInjectionPoints().stream()
                .map(FaultUid::toString)
                .toList();
        stats.put("fault_injection_points", points.size());
        details.put("fault_injection_points", points);

        List<String> modes = getFailureModes().stream()
                .map(FailureMode::toString)
                .toList();
        stats.put("failure_modes", modes.size());
        details.put("failure_modes", modes);

        stats.put("redundant_faultloads", store.getRedundantFaultloads().size());
        stats.put("redundant_fault_points", store.getRedundantUidSubsets().size());
        stats.put("Rredundant_fault_subsets", store.getRedundantFaultSubsets().size());

        stats.put("max_queue_size", getMaxQueueSize());
        stats.put("avg_queue_size", getAvgQueueSize());
        details.put("queue_size", queueSize);

        int queueSize = getQueuSize();
        if (queueSize > 0) {
            stats.put("Queue size (left)", queueSize);
        }

        var simplifiedPoints = getSimplifiedFaultInjectionPoints();
        stats.put("simplified_fault_injection_points", simplifiedPoints.size());
        details.put("simplified_fault_injection_points", simplifiedPoints.stream()
                .map(FaultUid::toString)
                .toList());

        // Report the visited faultloads
        var faultloads = store.getHistoricResults().stream()
                .map(x -> x.first())
                .toList();

        // Unique visited
        List<Object> visitByUid = new ArrayList<>();
        List<Object> visitByFaults = new ArrayList<>();

        var simplified = Simplify.simplify(faultloads, getFailureModes());
        var failureModesCount = getFailureModes().size();
        for (Set<FaultUid> cause : simplified.second()) {
            int increase = (int) Math.pow(failureModesCount, cause.size());
            Map<String, Object> causeReport = new LinkedHashMap<>();
            List<String> causeList = cause.stream()
                    .map(FaultUid::toString)
                    .toList();
            causeReport.put("faultload", causeList);
            causeReport.put("count", increase);
            visitByUid.add(causeReport);
        }

        for (Set<Fault> cause : simplified.first()) {
            Map<String, Object> causeReport = new LinkedHashMap<>();
            List<String> causeList = cause.stream()
                    .map(Fault::toString)
                    .toList();
            causeReport.put("faultload", causeList);
            visitByFaults.add(causeReport);
        }

        Map<String, Object> visitReport = new LinkedHashMap<>();
        visitReport.put("by_uid", visitByUid);
        visitReport.put("by_faults", visitByFaults);
        details.put("visited", visitReport);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stats", stats);
        report.put("tree", buildTreeReport(new TreeNode(Set.of()), null));
        report.put("details", details);
        return report;
    }
}
