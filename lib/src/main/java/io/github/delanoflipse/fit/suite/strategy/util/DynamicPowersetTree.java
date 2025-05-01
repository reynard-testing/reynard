package io.github.delanoflipse.fit.suite.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;

// TODO: just merge with generator? Its pretty hardwired atm
public class DynamicPowersetTree {
    private final Logger logger = LoggerFactory.getLogger(DynamicPowersetTree.class);
    private final DynamicAnalysisStore store;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    private final List<TreeNode> toVisit = new ArrayList<>();
    private final List<ExpansionNode> toExpand = new ArrayList<>();

    private final Set<ExpansionNode> prunedExpansions = new LinkedHashSet<>();
    private final Set<ExpansionNode> visitedExpansions = new LinkedHashSet<>();
    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public record TreeNode(List<Fault> value) {

        public Set<Fault> valueSet() {
            return Set.copyOf(value);
        }

        // For equality, the list is a set
        @Override
        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (o instanceof TreeNode other) {
                return Sets.areEqual(value, other.value);
            }

            return false;
        }

        // In the hashcode, we use the set representation
        // So in a hashset, our equality check still works
        @Override
        public final int hashCode() {
            return Objects.hash(Set.copyOf(value));
        }

    }

    public record ExpansionNode(TreeNode node, List<FaultUid> expansion) {

        // For equality, the list is a set
        @Override
        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (o instanceof ExpansionNode other) {
                return node.equals(other.node) && Sets.areEqual(expansion, other.expansion);
            }
            return false;
        }

        // In the hashcode, we use the set representation
        // So in a hashset, our equality check still works
        @Override
        public final int hashCode() {
            return Objects.hash(node, Set.copyOf(expansion));
        }
    }

    public DynamicPowersetTree(DynamicAnalysisStore store,
            Function<Set<Fault>, PruneDecision> pruneFunction,
            boolean skipEmptySet) {
        this.pruneFunction = pruneFunction;
        this.store = store;

        // Initialize the queue with the empty set
        // And all points
        TreeNode initialNode = new TreeNode(List.of());

        // If there are already points in the store, we can start by expanding
        if (!store.getPoints().isEmpty()) {
            ExpansionNode initialExpansion = new ExpansionNode(initialNode, List.copyOf(store.getPoints()));
            toExpand.add(initialExpansion);
        }

        // only visit the empty set if we are not skipping it
        if (!skipEmptySet) {
            toVisit.add(initialNode);
        } else {
            visitedNodes.add(initialNode);
        }

        updateQueueSize();
    }

    private void updateQueueSize() {
        // The to expand queue is the most important one
        // the other one is just a temporary storage for a single expansion
        queueSize.add(toExpand.size());
    }

    private PruneDecision shouldPrune(TreeNode node) {
        // Either we have stored a decision for this node
        PruneDecision storeDecision = store.isRedundant(node.valueSet());
        if (storeDecision == PruneDecision.PRUNE_SUPERSETS) {
            return PruneDecision.PRUNE_SUPERSETS;
        }

        // Or one of the pruners has a decision for this node
        PruneDecision punersDecision = pruneFunction.apply(node.valueSet());

        // Take the most impactful decision
        return PruneDecision.max(storeDecision, punersDecision);
    }

    private void pruneExpansions(TreeNode node) {
        List<ExpansionNode> prunedToVisit = toExpand.stream()
                .filter(e -> Sets.isSubsetOf(node.value, e.node.value))
                .toList();
        toExpand.removeAll(prunedToVisit);
        prunedExpansions.addAll(prunedToVisit);

        List<ExpansionNode> prunedVisited = visitedExpansions.stream()
                .filter(e -> Sets.isSubsetOf(node.value, e.node.value))
                .toList();
        visitedExpansions.removeAll(prunedVisited);
        prunedExpansions.addAll(prunedVisited);
    }

    // expand(n, nil) = ({}, {})
    // expand(n, b::X) = ({∀ m | n ++ (b, m)}, expand(a, X) ++ {∀ m | expand(n ++
    // (b, m), X) })
    // i.e., the subtree of a node is the expansion of the first element,
    // their subexpansions, and any expansions left for the node.
    public Pair<List<TreeNode>, List<ExpansionNode>> expand(ExpansionNode exp) {
        // Base case: cannot expand this node
        if (exp == null || exp.expansion.isEmpty()) {
            return Pair.of(List.of(), List.of());
        }

        // We have already visited this expansion, so we can skip it in the future
        visitedExpansions.add(exp);

        // Expand the head of the expansion
        // increasing the size of the node by one
        FaultUid expansionElement = exp.expansion.get(0);

        // and for each element, we take all modes
        List<TreeNode> newNodes = Fault.allFaults(expansionElement, store.getModes()).stream()
                .map(f -> new TreeNode(Lists.plus(exp.node.value, f)))
                .toList();

        List<FaultUid> expansionsLeft = exp.expansion.subList(1, exp.expansion.size());
        if (expansionsLeft.isEmpty()) {
            // We are done expanding this node, only add the new nodes
            return Pair.of(newNodes, List.of());
        }

        // the expansion with wich to replace the current node
        ExpansionNode directExpansion = new ExpansionNode(exp.node, expansionsLeft);

        // only keep extensions that are consistent with the current expansion
        // e.g., if we have a count=inf, we cannot add a count=1
        List<FaultUid> consistentSubexpansions = expansionsLeft.stream()
                .filter(e -> !expansionElement.matches(e))
                .toList();

        if (consistentSubexpansions.isEmpty()) {
            // We cannot expand the newly created nodes
            return Pair.of(newNodes, List.of(directExpansion));
        }

        List<ExpansionNode> subExpansions = newNodes.stream()
                .map(n -> new ExpansionNode(n, consistentSubexpansions))
                .toList();

        return Pair.of(newNodes, Lists.plus(directExpansion, subExpansions));
    }

    private void addNodes(Collection<TreeNode> nodes) {
        for (var node : nodes) {
            addNode(node);
        }
    }

    private boolean addNode(TreeNode node) {
        if (visitedNodes.contains(node) || toVisit.contains(node)) {
            logger.debug("Ignoring already visited point {}", node);
            return false;
        }

        int insertionIndex = Lists.addBefore(toVisit, node, n -> n.value.size() > node.value.size());

        if (insertionIndex == -1) {
            logger.debug("Adding {} to end of the queue", node);
        } else {
            logger.debug("Adding {} to queue at index {}", node, insertionIndex);
        }

        return true;
    }

    private void addExpensions(Collection<ExpansionNode> expansions) {
        for (var exp : expansions) {
            addExpension(exp);
        }
    }

    private boolean addExpension(ExpansionNode exp) {
        if (exp.expansion.isEmpty()) {
            logger.debug("Ignoring empty expansion {}", exp);
            return false;
        }

        if (visitedExpansions.contains(exp) || prunedExpansions.contains(exp) || toExpand.contains(exp)) {
            logger.debug("Ignoring expansion {}", exp);
            return false;
        }

        int insertionIndex = Lists.addBefore(toExpand, exp, e -> e.node.value.size() > exp.node.value.size());

        if (insertionIndex == -1) {
            logger.debug("Adding {} to end of the queue", exp);
        } else {
            logger.debug("Adding {} to queue at index {}", exp, insertionIndex);
        }

        return true;
    }

    public TreeNode getNextToVisit() {
        TreeNode node = toVisit.remove(0);
        visitedNodes.add(node);

        switch (shouldPrune(node)) {
            case PRUNE -> {
                return null;
            }

            case PRUNE_SUPERSETS -> {
                pruneExpansions(node);
                return null;
            }

            default -> {
                return node;
            }
        }
    }

    // Return the next, non-pruned node
    // Returns null if there are no more nodes to explore
    public Set<Fault> next() {
        long ops = 0;
        int orders = 2;

        while (true) {
            // 1. Get a new node to visit from the node queue
            while (!toVisit.isEmpty()) {
                TreeNode nextNode = getNextToVisit();
                if (nextNode == null) {
                    ops++;
                    continue;
                }

                return nextNode.valueSet();
            }

            // If we have nothing left to expand or visit, we are done
            if (toExpand.isEmpty()) {
                return null;
            }

            long order = (long) Math.pow(10, orders);
            if (ops++ > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }

            // 2. Expand the next in expansion queue
            ExpansionNode nextExpansion = toExpand.remove(0);
            var expansion = expand(nextExpansion);
            var nodes = expansion.first();
            addNodes(nodes);
            var newExpansion = expansion.second();
            addExpensions(newExpansion);
            updateQueueSize();
        }
    }

    public void pruneQueue() {
        // Could prune the node queue, but this is not necessary
        // As it is usually very small

        // Prune the expansion queue
        List<ExpansionNode> expansionToRemove = new ArrayList<>();

        Set<TreeNode> checked = new LinkedHashSet<>(visitedNodes);
        Set<TreeNode> pruned = new LinkedHashSet<>(visitedNodes);

        for (var exp : toExpand) {
            if (checked.contains(exp.node)) {
                continue;
            }

            if (pruned.contains(exp.node)) {
                expansionToRemove.add(exp);
                continue;
            }

            switch (shouldPrune(exp.node)) {
                case PRUNE_SUPERSETS -> {
                    pruned.add(exp.node);
                    expansionToRemove.add(exp);
                }
                default -> {
                    // do nothing
                    checked.add(exp.node);
                }
            }
        }

        toExpand.removeAll(expansionToRemove);
        logger.info("Pruned {} expansions", expansionToRemove.size());
    }

    // Explore (again) from a given node value
    // Determines expansions for this node
    // and adds them to the queue
    public boolean expandFrom(Collection<Fault> nodeValue) {
        // We cannot expand to extensions already in the condition
        List<FaultUid> alreadyExpanded = nodeValue.stream()
                .map(f -> f.uid())
                .toList();

        // Determine the expensions for this node
        List<FaultUid> expansionsLeft = store.getPoints().stream()
                .filter(e -> !alreadyExpanded.stream().anyMatch(x -> x.matches(e)))
                .toList();

        var startingNode = new TreeNode(List.copyOf(nodeValue));
        // Visit node if it is not already visited
        addNode(startingNode);

        // Add the expansion to the queue
        boolean isNew = addExpension(new ExpansionNode(startingNode, expansionsLeft));

        if (isNew) {
            logger.debug("Expanding from {}", startingNode);
        }

        return isNew;
    }

    public long getQueueSpaceSize() {
        int m = store.getModes().size();
        long sum = 0;
        for (var el : toExpand) {
            long contribution = SpaceEstimate.spaceSize(m, el.expansion.size());
            sum += contribution;
        }

        return sum;
    }

    public int getMaxQueueSize() {
        return queueSize.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    public int getQueuSize() {
        return toExpand.size();
    }
}
