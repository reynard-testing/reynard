package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.components.PruneDecision;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public class PrunableGenericPowersetTreeIterator implements Iterator<Set<Fault>> {
    private final Logger logger = LoggerFactory.getLogger(PrunableGenericPowersetTreeIterator.class);
    private final DynamicAnalysisStore store;
    private final List<TreeNode> toExpand = new ArrayList<>();
    private int maxQueueSize;
    private final Set<TreeNode> visitedNodes = new LinkedHashSet<>();
    private final Set<Set<Fault>> visitedPoints = new LinkedHashSet<>();
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    public record TreeNode(Set<Fault> value, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(DynamicAnalysisStore store,
            Function<Set<Fault>, PruneDecision> pruneFunction,
            boolean skipEmptySet) {
        this.pruneFunction = pruneFunction;
        this.store = store;

        this.visitedNodes.add(new TreeNode(Set.of(), List.of())); // empty
        this.visitedPoints.add(Set.of()); // empty

        toExpand.add(new TreeNode(Set.of(), List.copyOf(store.getPoints())));
        maxQueueSize = 1;

        if (skipEmptySet) {
            this.skip();
        }
    }

    private void trackVisited(TreeNode node, PruneDecision decision) {
        switch (decision) {
            case KEEP -> {
                // do nothing
            }
            case PRUNE_SUBTREE -> {
                visitedNodes.add(node);
            }
            case PRUNE -> {
                visitedPoints.add(node.value);
            }
        }
    }

    private PruneDecision shouldPrune(TreeNode node) {
        if (visitedPoints.contains(node.value)) {
            logger.debug("Pruning node for point {}: already visited", node);
            visitedNodes.add(node);
            return PruneDecision.PRUNE;
        }

        if (visitedNodes.contains(node)) {
            logger.debug("Pruning node {}: already visited", node);
            return PruneDecision.PRUNE_SUBTREE;
        }

        PruneDecision storeDecision = store.isRedundant(node.value);
        if (storeDecision != PruneDecision.KEEP) {
            logger.debug("Pruning node {}: redundant", node);
            trackVisited(node, storeDecision);
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
        switch (shouldPrune(node)) {
            case KEEP -> {
                // Add the node to the queue, it it's not already there
                if (toExpand.contains(node)) {
                    logger.debug("Node {} already in queue", node);
                    return Set.of();
                }

                return Set.of(node);
            }
            case PRUNE_SUBTREE -> {
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
        if (node == null || node.expansion.isEmpty()) {
            return Set.of();
        }

        Set<TreeNode> newNodes = new LinkedHashSet<>();
        for (int i = 0; i < node.expansion.size(); i++) {
            FaultUid expansionElement = node.expansion.get(i);
            List<FaultUid> newExpansion = node.expansion.subList(i + 1, node.expansion.size());

            for (Fault additionalElement : expandModes(expansionElement)) {
                Set<Fault> newValue = Sets.plus(node.value(), additionalElement);
                Set<FaultUid> reachablePoints = store.getExpectedPoints(newValue);
                List<FaultUid> reachableExtensions = newExpansion.stream()
                        .filter(x -> reachablePoints.contains(x))
                        .collect(Collectors.toList());

                var newNode = new TreeNode(newValue, reachableExtensions);
                newNodes.addAll(addOrPrune(newNode));
            }
        }

        return newNodes;
    }

    @Override
    public boolean hasNext() {
        boolean hasMore = !toExpand.isEmpty();
        return hasMore;
    }

    public void skip() {
        toExpand.addAll(expand(toExpand.remove(0)));
    }

    @Override
    public Set<Fault> next() {
        TreeNode node = toExpand.remove(0);
        visitedPoints.add(node.value);
        visitedNodes.add(node);
        toExpand.addAll(expand(node));
        maxQueueSize = Math.max(maxQueueSize, toExpand.size());
        return node.value;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    // Add new element to explore
    // With fixed
    public Set<TreeNode> expandFrom(Collection<Fault> fixedFaults) {
        // We cannot expand to extensions already in the condition
        Set<FaultUid> alreadyExpanded = fixedFaults.stream()
                .map(f -> f.uid())
                .collect(Collectors.toSet());

        // We cannot expand to extensions that are unreachable
        Set<FaultUid> reachablePoints = store.getExpectedPoints(fixedFaults);

        // Determine the expensions for this node
        List<FaultUid> expansionsLeft = store.getPoints().stream()
                .filter(e -> !alreadyExpanded.contains(e))
                .filter(e -> reachablePoints.contains(e))
                .toList();

        var newNode = new TreeNode(Set.copyOf(fixedFaults), expansionsLeft);
        var toAdd = addOrPrune(newNode);
        toExpand.addAll(toAdd);
        return toAdd;
    }

    public void pruneQueue() {
        // Remove pruned nodes
        Set<TreeNode> toAdd = new LinkedHashSet<>();
        Iterator<TreeNode> iterator = toExpand.iterator();

        while (iterator.hasNext()) {
            TreeNode node = iterator.next();
            switch (shouldPrune(node)) {
                case KEEP -> {
                    // do nothing
                    continue;
                }

                case PRUNE_SUBTREE -> {
                    // remove all children (node and expansion)
                    iterator.remove();
                    continue;
                }

                case PRUNE -> {
                    // remove this node,
                    toAdd.addAll(expand(node));
                    iterator.remove();
                    continue;
                }
            }
        }

        toExpand.addAll(toAdd);
    }

    public long size(int m) {
        long sum = 0;
        for (var el : toExpand) {
            long contribution = SpaceEstimate.spaceSize(m, el.expansion.size());
            sum += contribution;
        }

        return sum;
    }

    public long size() {
        return size(1);
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getQueuSize() {
        return toExpand.size();
    }
}
