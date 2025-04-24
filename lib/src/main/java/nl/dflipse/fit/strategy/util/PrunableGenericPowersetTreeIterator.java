package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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

    private final List<TreeNode> toExpand = new ArrayList<>();

    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();
    private final Set<Set<Fault>> visitedPoints = new LinkedHashSet<>();

    private final List<Integer> queueSize = new ArrayList<>();

    public record TreeNode(Set<Fault> value, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(DynamicAnalysisStore store,
            Function<Set<Fault>, PruneDecision> pruneFunction,
            boolean skipEmptySet) {
        this.pruneFunction = pruneFunction;
        this.store = store;

        // Initialize the queue with the empty set
        // And all points
        toExpand.add(new TreeNode(Set.of(), List.copyOf(store.getPoints())));
        updateQueueSize();

        if (skipEmptySet) {
            // Pretend we already visited the empty set
            this.store.pruneFaultload(Set.of());
        }
    }

    private void updateQueueSize() {
        queueSize.add(toExpand.size());
    }

    private void trackVisited(TreeNode node, PruneDecision decision) {
        switch (decision) {
            case KEEP -> {
                // do nothing
            }
            case PRUNE -> {
                // do nothing
            }

            case PRUNE_SUPERSETS -> {
                store.pruneFaultSubset(node.value);
            }
        }
    }

    private PruneDecision visitIsRedundant(TreeNode node) {
        if (visitedNodes.contains(node)) {
            logger.debug("Completely ignoring already visited node {}", node);
            return PruneDecision.PRUNE_SUPERSETS;
        }

        visitedNodes.add(node);

        if (visitedPoints.contains(node.value)) {
            logger.debug("Ignoring and only expanding already visited point {}", node);
            return PruneDecision.PRUNE;
        }

        visitedPoints.add(node.value);

        return PruneDecision.KEEP;
    }

    private PruneDecision shouldPrune(TreeNode node, boolean onAddition) {
        if (onAddition) {
            PruneDecision localDecision = visitIsRedundant(node);
            if (localDecision != PruneDecision.KEEP) {
                return localDecision;
            }
        }

        PruneDecision storeDecision = store.isRedundant(node.value);
        if (storeDecision != PruneDecision.KEEP) {
            return storeDecision;
        }

        PruneDecision punersDecision = pruneFunction.apply(node.value);
        trackVisited(node, punersDecision);
        return punersDecision;
    }

    private List<Fault> expandModes(FaultUid node) {
        List<Fault> collection = new ArrayList<>();
        for (var mode : store.getModes()) {
            collection.add(new Fault(node, mode));
        }
        return collection;
    }

    private Set<TreeNode> addOrPrune(TreeNode node) {
        switch (shouldPrune(node, true)) {
            case KEEP -> {
                // Add the node to the queue, it it's not already there
                if (toExpand.contains(node)) {
                    logger.debug("Node {} already in queue", node);
                    return Set.of();
                }

                return Set.of(node);
            }
            case PRUNE_SUPERSETS -> {
                // do nothing
                return Set.of();
            }
            case PRUNE -> {
                // don't add this exact node, but maybe its children
                return expand(node);
            }
        }

        return Set.of();
    }

    public Set<TreeNode> expand(TreeNode node) {
        // Base case: cannot expand this node
        if (node == null || node.expansion.isEmpty()) {
            return Set.of();
        }

        // We expand once for each element in the expansion
        // i.e. we increase the size of the set by one
        // and for each element, we take all modes
        Set<TreeNode> newNodes = new LinkedHashSet<>();
        for (int i = 0; i < node.expansion.size(); i++) {
            FaultUid expansionElement = node.expansion.get(i);

            // This way, we don't expand twice to the same subsets
            List<FaultUid> newExpansion = node.expansion
                    .subList(i + 1, node.expansion.size());

            // Create a new node for each mode
            for (Fault additionalElement : expandModes(expansionElement)) {
                Set<Fault> newValue = Sets.plus(node.value(), additionalElement);

                var newNode = new TreeNode(newValue, newExpansion);
                newNodes.addAll(addOrPrune(newNode));
            }
        }

        return newNodes;
    }

    // Return the next, non-pruned node
    // Returns null if there are no more nodes to explore
    public Set<Fault> next() {
        long counter = 0;
        int orders = 1;

        while (!toExpand.isEmpty()) {

            long order = (long) Math.pow(10, orders);
            if (counter++ > order) {
                logger.info("Progress: generated and pruned >" + order + " faultloads");
                orders++;
            }

            TreeNode node = toExpand.remove(0);
            PruneDecision nodeFate = shouldPrune(node, false);

            visitedPoints.add(node.value);
            visitedNodes.add(node);

            if (nodeFate == PruneDecision.PRUNE_SUPERSETS) {
                // skip this node wholely
                continue;
            }

            toExpand.addAll(expand(node));
            updateQueueSize();

            if (nodeFate == PruneDecision.PRUNE) {
                // We don't want to return this node
                // but we want to expand it
                continue;
            }
            return node.value;
        }

        return null;
    }

    // Explore (again) from a given node value
    // Determines expansions for this node
    // and adds them to the queue
    public Set<TreeNode> expandFrom(Collection<Fault> nodeValue) {
        // We cannot expand to extensions already in the condition
        List<FaultUid> alreadyExpanded = nodeValue.stream()
                .map(f -> f.uid())
                .toList();

        // Determine the expensions for this node
        List<FaultUid> expansionsLeft = store.getPoints().stream()
                .filter(e -> !alreadyExpanded.stream().anyMatch(x -> x.matches(e)))
                .toList();

        var startingNode = new TreeNode(Set.copyOf(nodeValue), expansionsLeft);
        var toAdd = addOrPrune(startingNode);
        toExpand.addAll(toAdd);
        logger.debug("Expanding from {}, added {} new to the queue", startingNode, toAdd.size());
        return toAdd;
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
