package dev.reynard.junit.strategy.components.generators;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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

import dev.reynard.junit.faultload.Behaviour;
import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.FailureMode;
import dev.reynard.junit.strategy.FaultloadResult;
import dev.reynard.junit.strategy.components.FeedbackContext;
import dev.reynard.junit.strategy.components.FeedbackHandler;
import dev.reynard.junit.strategy.components.PruneDecision;
import dev.reynard.junit.strategy.store.DynamicAnalysisStore;
import dev.reynard.junit.strategy.util.Lists;
import dev.reynard.junit.strategy.util.Sets;
import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

public class GuidedExplorationGenerator extends StoreBasedGenerator implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(GuidedExplorationGenerator.class);

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

    private final Map<TreeNode, FaultloadResult> results = new LinkedHashMap<>();
    private final Map<TreeNode, TreeNode> predecessors = new LinkedHashMap<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public GuidedExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy, boolean breadthFirst) {
        super(store);
        this.breadthFirst = breadthFirst;
        this.pruneFunction = pruneFunction;
        this.pointOrder = traversalStrategy;

        visited.add(root);
    }

    public GuidedExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction,
            TraversalOrder traversalStrategy) {
        this(store, pruneFunction, traversalStrategy, true);
    }

    public GuidedExplorationGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction, TraversalOrder.DEPTH_FIRST_POST_ORDER);
    }

    private void updateQueueSize() {
        queueSize.add(toVisit.size());
    }

    private boolean isConsistent(TreeNode node) {
        Set<Fault> faultload = node.asSet();
        Set<FaultUid> uids = faultload.stream()
                .map(Fault::uid)
                .collect(Collectors.toSet());

        for (Fault f : faultload) {
            FaultUid uid = f.uid();
            // Check if any parent is also in the faultload
            while (uid.hasParent()) {
                uid = uid.getParent();
                if (uids.contains(uid)) {
                    logger.debug("Fault {} is inconsistent with its causaul dependency {}", f,
                            f.uid().getParent());
                    return false;
                }
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
        return addNode(node, false);
    }

    private List<FaultUid> getObservedPoints(FaultloadResult result) {
        // Get the observed points from the result trace
        return result.trace.getFaultUids(pointOrder)
                .stream()
                .filter(x -> !x.isInitial())
                .toList();
    }

    private List<FaultUid> getExpansionSet(List<FaultUid> observed, List<FaultUid> injected, List<FaultUid> known) {
        List<FaultUid> toExplore = new ArrayList<>();

        for (var point : observed) {
            // Also included known points that are similar (e.g., persistent faults)
            // And ignore points that are already injected
            var relatedPoints = known.stream()
                    .filter(point::matches)
                    .filter(p -> !FaultUid.contains(injected, p))
                    .toList();

            toExplore.addAll(relatedPoints);
        }

        return toExplore;
    }

    private boolean introducedNewBehaviour(FaultloadResult current, FaultloadResult before) {
        var additions = Lists.difference(current.trackedFaultload.getFaultload().faultSet(),
                before.trackedFaultload.getFaultload().faultSet());

        Set<FaultUid> addedPoints = additions.stream()
                .map(Fault::uid)
                .collect(Collectors.toSet());

        Set<FaultUid> points = Sets.union(
                current.trace.getFaultUids(pointOrder),
                before.trace.getFaultUids(pointOrder));

        Set<FaultUid> unrelatedPoints = Sets.difference(points, addedPoints);

        for (var point : unrelatedPoints) {
            Behaviour cb = current.trace.getBehaviourByPoint(point);
            Behaviour bb = before.trace.getBehaviourByPoint(point);
            if (cb == null || bb == null) {
                logger.debug("Behaviour for point {} is not available in one of the traces", point);
                return true;
            }

            if (!cb.equals(bb)) {
                logger.debug("Behaviour for point {} has changed from {} to {}", point, bb, cb);
                return true;
            }
        }

        return false;
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        List<FaultUid> observed = getObservedPoints(result);

        for (var point : observed) {
            context.reportFaultUid(point);
        }

        List<Fault> injected = result.trace.getInjectedFaults().stream().toList();
        TreeNode current = new TreeNode(injected);
        results.put(current, result);
        List<FaultUid> injectedPoints = injected.stream()
                .map(Fault::uid)
                .toList();
        List<FaultUid> known = context.getFaultInjectionPoints();
        List<FaultUid> possibleExpansion = getExpansionSet(observed, injectedPoints, known);

        TreeNode predecessor = predecessors.get(current);

        if (predecessor != null && !introducedNewBehaviour(result, results.get(predecessor))) {
            logger.debug("Skipping {} as it does not introduce new behaviour compared to its predecessor {}",
                    current, predecessor);
            return;
        }

        for (FaultUid point : possibleExpansion) {
            for (Fault fault : Fault.allFaults(point, getFailureModes())) {
                TreeNode newNode = new TreeNode(Lists.plus(injected, fault));

                if (!predecessors.containsKey(newNode)) {
                    addNode(newNode, breadthFirst);
                    predecessors.put(newNode, current);
                    logger.debug("Adding {} as predecessor of {}", current, newNode);
                }
            }
        }

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
}
