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
import java.util.stream.Collectors;

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
    private final List<TreeNode> visited = new ArrayList<>();
    private final Set<TreeNode> consideredNodes = new LinkedHashSet<>();
    private final Set<TreeNode> prunedNodes = new HashSet<>();

    // Logging and tracking
    private final Map<TreeNode, List<TreeNode>> expansionTree = new LinkedHashMap<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy, boolean breadthFirst) {
        super(store);
        this.breadthFirst = breadthFirst;
        this.pruneFunction = pruneFunction;
        this.pointOrder = traversalStrategy;

        visited.add(root);
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

    private boolean isConsistent(TreeNode node) {
        Set<Fault> faultload = node.asSet();
        Set<FaultUid> uids = faultload.stream()
                .map(Fault::uid)
                .collect(Collectors.toSet());

        for (Fault f : faultload) {
            if (uids.contains(f.uid().getParent())) {
                logger.debug("Fault {} is inconsistent with its causaul dependency {}", f,
                        f.uid().getParent());
                return false;
            }
        }

        return true;
    }

    private boolean addNode(TreeNode node, boolean addLast) {
        if (consideredNodes.contains(node)) {
            return false;
        }

        if (!isConsistent(node)) {
            logger.debug("Node {} is inconsistent, not adding", node);
            consideredNodes.add(node);
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
                case PRUNE_SUPERSETS, PRUNE -> {
                    logger.debug("Pruning node {} completely", node);
                    prunedNodes.add(node);
                }

                case KEEP -> {
                    logger.info("Found a candidate after {} attempt(s)", ops);
                    updateQueueSize();
                    visited.add(node);
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

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> observed = result.trace.getFaultUids(pointOrder)
                .stream()
                .filter(x -> !x.isInitial())
                .toList();

        for (var point : observed) {
            context.reportFaultUid(point);
        }

        List<Fault> injected = result.trace.getInjectedFaults().stream().toList();
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

    private List<Object> reportOf(Collection<Fault> node) {
        return node.stream().map(x -> {
            Map<String, Object> faultReport = new LinkedHashMap<>();
            faultReport.put("uid", x.uid().toString());
            faultReport.put("mode", x.mode().toString());
            return (Object) faultReport;
        }).toList();
    }

    private Map<String, Object> buildTreeReport(TreeNode node, TreeNode parent) {
        Map<String, Object> report = new LinkedHashMap<>();
        boolean isPruned = prunedNodes.contains(node);
        int index = visited.indexOf(node);
        report.put("index", index);
        report.put("pruned", isPruned);

        if (parent == null) {
            report.put("node", reportOf(node.value()));
        } else {
            var addition = node.value().subList(parent.value().size(), node.value().size());
            report.put("node", reportOf(addition));
        }

        List<TreeNode> children = expansionTree.get(node);
        if (children == null || children.isEmpty()) {
            return report;
        }

        List<Map<String, Object>> childReports = new ArrayList<>();
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
        stats.put("redundant_fault_subsets", store.getRedundantFaultSubsets().size());

        stats.put("max_queue_size", getMaxQueueSize());
        stats.put("avg_queue_size", getAvgQueueSize());
        stats.put("visited_nodes", consideredNodes.size());
        stats.put("pruned_nodes", prunedNodes.size());

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
        ArrayList<Object> visitedInOrder = new ArrayList<>(visited.size());
        for (var i = 0; i < visited.size(); i++) {
            TreeNode node = visited.get(i);

            Map<String, Object> visitedNodeReport = new LinkedHashMap<>();
            List<Object> faults = node.value().stream()
                    .map(x -> {
                        Map<String, Object> faultReport = new LinkedHashMap<>();
                        faultReport.put("uid", x.uid().toString());
                        faultReport.put("mode", x.mode().toString());
                        return (Object) faultReport;
                    })
                    .toList();

            visitedNodeReport.put("index", i);
            visitedNodeReport.put("faultload", faults);
            visitedInOrder.add(visitedNodeReport);
        }

        Map<String, Object> visitReport = new LinkedHashMap<>();
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
