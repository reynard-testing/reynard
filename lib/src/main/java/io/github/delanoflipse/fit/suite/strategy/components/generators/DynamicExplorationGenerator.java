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
import io.github.delanoflipse.fit.suite.strategy.util.TraversalStrategy;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;

public class DynamicExplorationGenerator extends StoreBasedGenerator implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(DynamicExplorationGenerator.class);

    private final TreeNode root = new TreeNode(Set.of());
    private final List<TreeNode> toVisit = new ArrayList<>();
    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();
    private final Map<TreeNode, List<TreeNode>> expansionTree = new LinkedHashMap<>();
    private int nodeCounter = 0;
    private final Map<TreeNode, Integer> nodeIndex = new LinkedHashMap<>();
    private final List<Integer> queueSize = new ArrayList<>();
    private final TraversalOrder nodeOrder;
    private final TraversalOrder treeOrder = TraversalOrder.DEPTH_FIRST_POST_ORDER;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy) {
        super(store);
        this.pruneFunction = pruneFunction;
        this.nodeOrder = traversalStrategy;

        nodeIndex.put(root, 0);
    }

    public DynamicExplorationGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction, TraversalOrder.BREADTH_FIRST);
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

    private void addToTree(TreeNode parent, TreeNode node) {
        expansionTree.putIfAbsent(parent, new ArrayList<>());
        expansionTree.get(parent).add(node);
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
                    addToTree(node, newNode);
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
                    updateQueueSize();
                    return new Faultload(node.value);
                }
            }

        }

        logger.info("Found no candidate after {} attempt(s)!", ops);
        updateQueueSize();
        return null;
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        TreeNode node = new TreeNode(Set.copyOf(startingNode));
        boolean isNew = addNode(node);

        if (isNew) {
            addToTree(root, node);
            logger.info("Exploring new point {}", node);
        }

        return isNew;
    }

    private boolean addNode(TreeNode node) {
        if (visitedNodes.contains(node) || toVisit.contains(node)) {
            return false;
        }

        int insertionIndex = -1;
        switch (treeOrder) {
            case DEPTH_FIRST_POST_ORDER,
                    DEPTH_FIRST_REVERSE_POST_ORDER,
                    DEPTH_FIRST_PRE_ORDER,
                    DEPTH_FIRST_REVERSE_PRE_ORDER -> {
                // explore new node first
                insertionIndex = Lists.addBefore(toVisit, node, x -> x.value.size() <= node.value.size());
            }
            default -> {
                // add before the first node that has a larger size
                insertionIndex = Lists.addBefore(toVisit, node, x -> x.value.size() > node.value.size());
            }

        }

        if (insertionIndex == -1) {
            logger.debug("Adding {} to end of the queue", node);
        } else {
            logger.debug("Adding {} to queue at index {}", node, insertionIndex);
        }

        return true;

    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> observed = result.trace.getFaultUids(nodeOrder);

        for (var point : observed) {
            context.reportFaultUid(point);
        }

        Set<Fault> injected = result.trace.getInjectedFaults();
        List<FaultUid> injectedPoints = injected.stream()
                .map(Fault::uid)
                .toList();
        List<FaultUid> known = context.getFaultInjectionPoints();
        List<FaultUid> toExplore = new ArrayList<>();

        for (var point : observed) {
            // Also included known points that are similar (e.g., persistent faults)
            // And ignore points that are already injected
            var relatedPoints = known.stream()
                    .filter(p -> point.matches(p))
                    .filter(p -> !FaultUid.contains(injectedPoints, p))
                    .toList();

            toExplore.addAll(relatedPoints);
        }

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
            var addition = Sets.difference(node.value, parent.value);
            if (addition.size() == 1) {
                report.put("node", Sets.getOnlyElement(addition).toString());
            } else {
                report.put("node", node.value.toString());
            }
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

        List<String> modes = getFailureModes().stream()
                .map(FailureMode::toString)
                .toList();
        stats.put("failure_modes", modes.size());
        details.put("failure_modes", modes);

        stats.put("fault_injection_points", getFaultInjectionPoints().size());
        details.put("fault_injection_point_names", getFaultInjectionPoints().stream()
                .map(FaultUid::toString)
                .toList());
        details.put("fault_injection_points", getFaultInjectionPoints());

        stats.put("redundant_faultloads", store.getRedundantFaultloads().size());
        stats.put("redundant_fault_points", store.getRedundantUidSubsets().size());
        stats.put("Rredundant_fault_subsets", store.getRedundantFaultSubsets().size());

        stats.put("max_queue_size", getMaxQueueSize());
        stats.put("avg_queue_size", getAvgQueueSize());
        stats.put("visited_nodes", visitedNodes.size());

        int queueSizeLeft = getQueuSize();
        if (queueSizeLeft > 0) {
            stats.put("queue_size_left", queueSizeLeft);
        }

        var simplifiedPoints = getSimplifiedFaultInjectionPoints();
        stats.put("simplified_fault_injection_points", simplifiedPoints.size());
        details.put("simplified_fault_injection_points_names", simplifiedPoints.stream()
                .map(FaultUid::toString)
                .toList());
        details.put("simplified_fault_injection_points", simplifiedPoints);
        details.put("queue_size", queueSize);

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

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stats", stats);
        report.put("details", details);

        report.put("implications", store.getImplicationsReport());
        report.put("visited", visitReport);
        report.put("tree", buildTreeReport(new TreeNode(Set.of()), null));
        return report;
    }
}
