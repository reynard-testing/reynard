package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

// TODO: rename, its not an iterator anymore
// TODO: just merge with generator? Its pretty hardwired atm
public class PrunableGenericPowersetTreeIterator {
    private final Logger logger = LoggerFactory.getLogger(PrunableGenericPowersetTreeIterator.class);
    private final DynamicAnalysisStore store;
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    private final List<TreeNode> toVisit = new ArrayList<>();
    private final List<ExpansionNode> toExpand = new ArrayList<>();

    private final Set<ExpansionNode> visitedExpansions = new LinkedHashSet<>();
    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public record TreeNode(List<Fault> value) {
        public Set<Fault> valueSet() {
            return Set.copyOf(value);
        }
    }

    public record ExpansionNode(TreeNode node, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(DynamicAnalysisStore store,
            Function<Set<Fault>, PruneDecision> pruneFunction,
            boolean skipEmptySet) {
        this.pruneFunction = pruneFunction;
        this.store = store;

        // Initialize the queue with the empty set
        // And all points
        updateQueueSize();
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
    }

    private void updateQueueSize() {
        queueSize.add(toExpand.size());
    }

    private PruneDecision shouldPrune(TreeNode node) {
        if (node.value.isEmpty()) {
            return PruneDecision.PRUNE;
        }

        PruneDecision storeDecision = store.isRedundant(node.valueSet());
        if (storeDecision == PruneDecision.PRUNE_SUPERSETS) {
            return PruneDecision.PRUNE_SUPERSETS;
        }

        PruneDecision punersDecision = pruneFunction.apply(node.valueSet());
        return PruneDecision.max(storeDecision, punersDecision);
    }

    private void pruneExpansions(TreeNode node) {
        List<ExpansionNode> expansions = toExpand.stream()
                .filter(e -> Sets.isSubsetOf(node.valueSet(), e.node.valueSet()))
                .toList();
        toExpand.removeAll(expansions);
        visitedExpansions.addAll(expansions);
    }

    public Pair<List<TreeNode>, List<ExpansionNode>> expand(ExpansionNode exp) {
        // Base case: cannot expand this node
        if (exp == null || exp.expansion.isEmpty()) {
            return Pair.of(List.of(), List.of());
        }

        visitedExpansions.add(exp);

        // We expand once for each element in the expansion
        // i.e. we increase the size of the set by one
        // and for each element, we take all modes
        FaultUid expansionElement = exp.expansion.get(0);

        List<TreeNode> newNodes = Fault.getFaults(expansionElement, store.getModes()).stream()
                .map(f -> new TreeNode(Lists.plus(exp.node.value, f)))
                .toList();

        List<FaultUid> expansionsLeft = exp.expansion.subList(1, exp.expansion.size());
        if (expansionsLeft.isEmpty()) {
            // We are done expanding this node
            return Pair.of(newNodes, List.of());
        }

        List<FaultUid> consistentExpansions = expansionsLeft.stream()
                .filter(e -> !expansionElement.matches(e))
                .toList();

        if (consistentExpansions.isEmpty()) {
            // We are done expanding this node
            return Pair.of(newNodes, List.of(new ExpansionNode(exp.node, expansionsLeft)));
        }

        List<ExpansionNode> newExpansions = Lists.plus(
                newNodes.stream()
                        .map(n -> new ExpansionNode(n, consistentExpansions))
                        .toList(),
                new ExpansionNode(exp.node, expansionsLeft));

        return Pair.of(newNodes, newExpansions);
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

        int insertionIndex = Lists.addAfter(toVisit, node, n -> n.value.size() > node.value.size());

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

        if (visitedExpansions.contains(exp) || toExpand.contains(exp)) {
            logger.debug("Ignoring expansion {}", exp);
            return false;
        }

        int insertionIndex = Lists.addAfter(toExpand, exp, e -> e.node.value.size() > exp.node.value.size());

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
        // Prune the prune queue
        List<TreeNode> nodesToRemove = new ArrayList<>();
        for (var node : toVisit) {
            switch (shouldPrune(node)) {
                case PRUNE_SUPERSETS -> {
                    pruneExpansions(node);
                    nodesToRemove.add(node);
                }
                case PRUNE -> {
                    nodesToRemove.add(node);
                }
                default -> {
                    // do nothing
                }
            }
        }
        toVisit.removeAll(nodesToRemove);

        // Prune the expansion queue
        List<ExpansionNode> expansionToRemove = new ArrayList<>();
        for (var exp : toExpand) {
            switch (shouldPrune(exp.node)) {
                case PRUNE_SUPERSETS -> {
                    expansionToRemove.add(exp);
                }
                default -> {
                    // do nothing
                }
            }
        }
        toExpand.removeAll(expansionToRemove);
        logger.info("Pruned {} nodes and {} expansions", nodesToRemove.size(), expansionToRemove.size());
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
