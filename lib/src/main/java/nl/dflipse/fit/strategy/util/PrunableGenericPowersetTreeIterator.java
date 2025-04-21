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
    private final Set<TreeNode> visited = new LinkedHashSet<>();
    private final Function<Set<Fault>, PruneDecision> pruneFunction;

    public record TreeNode(Set<Fault> value, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(DynamicAnalysisStore store,
            Function<Set<Fault>, PruneDecision> pruneFunction,
            boolean skipEmptySet) {
        this.pruneFunction = pruneFunction;
        this.store = store;
        this.visited.add(new TreeNode(Set.of(), List.of())); // empty
        toExpand.add(new TreeNode(Set.of(), List.copyOf(store.getPoints())));
        maxQueueSize = 1;

        if (skipEmptySet) {
            this.skip();
        }
    }

    private PruneDecision shouldPrune(TreeNode node) {
        if (visited.contains(node)) {
            logger.debug("Pruning node {}: already visited", node);
            return PruneDecision.PRUNE;
        }

        PruneDecision storeDecision = store.isRedundant(node.value);
        if (storeDecision != PruneDecision.KEEP) {
            logger.debug("Pruning node {}: redundant", node);
            return storeDecision;
        }

        return pruneFunction.apply(node.value);
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
                Set<FaultUid> expectedPoints = store.getExpectedPoints(newValue);
                List<FaultUid> reachableExtensions = newExpansion.stream()
                        .filter(x -> expectedPoints.contains(x))
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
        toExpand.addAll(expand(node));
        maxQueueSize = Math.max(maxQueueSize, toExpand.size());
        visited.add(node);
        return node.value;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    // Add new element to explore
    public void add(FaultUid extension) {
        Set<TreeNode> toAdd = new LinkedHashSet<>();

        for (Fault additionalElement : expandModes(extension)) {
            Set<Fault> newValue = Set.of(additionalElement);
            List<FaultUid> expansion = store.getPoints().stream()
                    .filter(x -> x != extension)
                    .collect(Collectors.toList());
            var newNode = new TreeNode(newValue, List.copyOf(expansion));
            toAdd.addAll(addOrPrune(newNode));
        }

        toExpand.addAll(toAdd);
    }

    // Add new element to explore
    // With fixed
    public void expandFrom(Collection<Fault> fixedFaults) {
        // We cannot expand to extensions already in the condition
        Set<FaultUid> alreadyExpanded = fixedFaults.stream()
                .map(f -> f.uid())
                .collect(Collectors.toSet());

        List<FaultUid> expansionsLeft = store.getPoints().stream()
                .filter(e -> !alreadyExpanded.contains(e))
                .toList();

        var newNode = new TreeNode(Set.copyOf(fixedFaults), expansionsLeft);
        var toAdd = addOrPrune(newNode);
        toExpand.addAll(toAdd);
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
