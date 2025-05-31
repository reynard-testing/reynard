package io.github.delanoflipse.fit.suite.strategy.components.generators;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;

public class DynamicExplorationGenerator extends StoreBasedGenerator implements FeedbackHandler, Reporter {
    private final Logger logger = LoggerFactory.getLogger(DynamicExplorationGenerator.class);

    // Parameters
    private final TraversalOrder pointOrder;
    private final boolean breadthFirst;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    // Internal structures
    private final TreeNode root = new TreeNode(List.of());
    private final Deque<TreeNode> toVisit = new ArrayDeque<>();
    private final Set<TreeNode> consideredNodes = new LinkedHashSet<>();
    private final Set<TreeNode> prunedNodes = new HashSet<>();

    // Logging and tracking
    private int nodeCounter = 0;
    private final Map<TreeNode, List<TreeNode>> expansionTree = new LinkedHashMap<>();
    private final Map<TreeNode, Integer> nodeIndex = new LinkedHashMap<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy, boolean breadthFirst) {
        super(store);
        this.breadthFirst = breadthFirst;
        this.pruneFunction = pruneFunction;
        this.pointOrder = traversalStrategy;

        nodeIndex.put(root, 0);
    }

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy) {
        this(store, pruneFunction, traversalStrategy, true);
    }

    public DynamicExplorationGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction, TraversalOrder.DEPTH_FIRST_POST_ORDER);
    }

    private void updateQueueSize() {
        queueSize.add(toVisit.size());
    }

    private void addToTree(TreeNode parent, TreeNode node) {
        expansionTree.putIfAbsent(parent, new ArrayList<>());
        var children = expansionTree.get(parent);
        if (!children.contains(node)) {
            children.add(node);
        } else {
            logger.debug("Node {} already exists in the tree under parent {}", node, parent);
        }
    }

    private void expand(TreeNode node, List<FaultUid> expansion) {
        if (expansion.isEmpty()) {
            return;
        }

        if (!breadthFirst) {
            Collections.reverse(expansion);
        }

        for (var i = 0; i < expansion.size(); i++) {
            var point = expansion.get(i);
            for (Fault newFault : Fault.allFaults(point, getFailureModes())) {
                TreeNode newNode = new TreeNode(Lists.plus(node.value(), newFault));
                boolean expanded = addNode(newNode, breadthFirst);
                if (expanded) {
                    addToTree(node, newNode);
                }
            }
        }
    }

    private PruneDecision pruneFunction(TreeNode node) {
        Set<Fault> nodeSet = node.asSet();
        return PruneDecision.max(store.isRedundant(nodeSet), pruneFunction.apply(nodeSet));
    }

    @Override
    public Faultload generate() {
        long ops = 0;
        int orders = 2;

        while (!toVisit.isEmpty()) {
            // 1. Get a new node to visit from the node queue
            TreeNode node = toVisit.poll();

            long order = (long) Math.pow(10, orders);
            if (ops++ > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }

            switch (pruneFunction(node)) {
                case PRUNE_SUPERSETS -> {
                    logger.debug("Pruning node {} completely", node);
                    prunedNodes.add(node);
                }

                case PRUNE -> {
                    logger.debug("Pruning node {} completely", node);
                    prunedNodes.add(node);
                }

                case KEEP -> {
                    logger.info("Found a candidate after {} attempt(s)", ops);
                    nodeCounter++;
                    if (nodeIndex.containsKey(node)) {
                        logger.warn("Node {} already exists in the index! This is a bug!", node);
                    } else {
                        nodeIndex.put(node, nodeCounter);
                    }
                    updateQueueSize();
                    return new Faultload(node.asSet());
                }
            }
        }

        logger.info("Found no candidate after {} attempt(s)!", ops);
        updateQueueSize();
        return null;
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        TreeNode node = new TreeNode(List.copyOf(startingNode));
        // Always explore the node immediately
        boolean isNew = addNode(node, false);

        if (isNew) {
            addToTree(root, node);
            logger.info("Exploring new point {}", node);
        }

        return isNew;
    }

    private boolean sameOrigin(TreeNode node) {
        List<FaultUid> uids = node.value().stream()
                .filter(x -> !x.uid().isInitial() && !x.uid().isRoot())
                .map(Fault::uid)
                .toList();

        if (uids.isEmpty()) {
            return true; // Root node
        }

        FaultUid firstUid = uids.get(0);

        return uids.stream()
                .allMatch(uid -> uid.getParent().matches(firstUid.getParent()));
    }

    private boolean addNode(TreeNode node, boolean addLast) {
        if (consideredNodes.contains(node)) {
            return false;
        }

        if (addLast) {
            logger.debug("Adding {} to end of the queue", node);
            toVisit.addLast(node);
        } else {
            logger.debug("Adding {} to start of the dequeu", node);
            toVisit.addFirst(node);
        }

        consideredNodes.add(node);
        return true;
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> observed = result.trace.getFaultUids(pointOrder);

        for (var point : observed) {
            context.reportFaultUid(point);
        }

        List<Fault> injected = result.trace.getInjectedFaults().stream().toList();
        List<FaultUid> injectedPoints = injected.stream()
                .map(Fault::uid)
                .toList();
        List<FaultUid> known = context.getFaultInjectionPoints()
                .stream()
                .filter(x -> !x.isInitial())
                .toList();
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

        TreeNode currentNode = new TreeNode(injected);
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
        Map<String, Object> report = new LinkedHashMap<>();
        boolean isPruned = prunedNodes.contains(node);
        report.put("index", nodeIndex.getOrDefault(node, -(nodeCounter++)));
        report.put("pruned", isPruned);

        if (parent == null) {
            report.put("node", node.value().toString());
        } else {
            var addition = Sets.difference(node.value(), parent.value());
            if (addition.size() == 1) {
                report.put("node", Sets.getOnlyElement(addition).toString());
            } else {
                report.put("node", node.value().toString());
            }
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
        // TODO: the report is a bit excessive, we should probably
        // only report the most important information
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> details = new LinkedHashMap<>();

        List<String> modes = getFailureModes().stream()
                .map(FailureMode::toString)
                .toList();
        stats.put("failure_modes", modes.size());

        details.put("node_order", pointOrder.toString());
        details.put("breadth_first", breadthFirst);
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
        stats.put("visited_nodes", consideredNodes.size());

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

        List<Object> visitedInOrder = new ArrayList<>();
        for (var node : consideredNodes) {
            if (prunedNodes.contains(node)) {
                continue; // Skip pruned nodes
            }

            Map<String, Object> visitedNodeReport = new LinkedHashMap<>();
            List<String> causeList = node.value().stream()
                    .map(Fault::toString)
                    .toList();
            visitedNodeReport.put("faultload", causeList);
            visitedNodeReport.put("index", nodeIndex.getOrDefault(node, -1));
            visitedInOrder.add(visitedNodeReport);
        }

        Map<String, Object> visitReport = new LinkedHashMap<>();
        visitReport.put("by_uid", visitByUid);
        visitReport.put("by_faults", visitByFaults);
        visitReport.put("in_order", visitedInOrder);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stats", stats);
        report.put("details", details);

        report.put("implications", store.getImplicationsReport());
        report.put("visited", visitReport);
        report.put("tree", buildTreeReport(root, null));
        return report;
    }
}
