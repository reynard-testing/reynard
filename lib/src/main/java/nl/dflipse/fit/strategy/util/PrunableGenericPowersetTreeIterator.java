package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public class PrunableGenericPowersetTreeIterator implements Iterator<Set<Fault>> {
    private final Logger logger = LoggerFactory.getLogger(PrunableGenericPowersetTreeIterator.class);
    private final List<FaultUid> elements;
    private final List<TreeNode> toExpand = new ArrayList<>();

    private final DynamicAnalysisStore store;
    private int maxSize;

    public record TreeNode(Set<Fault> value, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(List<FaultUid> elements, DynamicAnalysisStore store,
            boolean skipEmptySet) {
        this.store = store;
        this.elements = new ArrayList<>();

        if (elements != null && !elements.isEmpty()) {
            this.elements.addAll(elements);
        }

        toExpand.add(new TreeNode(Set.of(), List.copyOf(elements)));
        maxSize = 1;

        if (skipEmptySet) {
            this.skip();
        }
    }

    private boolean shouldPrune(TreeNode node) {
        for (Fault fault : node.value) {
            FaultUid point = fault.uid();

            // Prune on preconditions
            if (store.getInclusionConditions().hasConditions(point)) {
                boolean hasPrecondition = store.getInclusionConditions().hasCondition(node.value, point);

                if (!hasPrecondition) {
                    logger.debug("Pruning node due to not matching preconditions for {}", fault);
                    return true;
                }
            }

            // Prune on exclusions
            if (store.getExclusionConditions().hasConditions(point)) {
                boolean hasExclusion = store.getExclusionConditions().hasCondition(node.value, point);

                if (hasExclusion) {
                    logger.debug("Pruning node due to matching exclusion for {}", fault);
                    return true;
                }
            }
        }

        // Prune on subsets
        if (store.hasFaultSubset(node.value)) {
            logger.debug("Pruning node {} due pruned subset", node.value);
            return true;
        }

        // Prune on faultload
        if (store.hasFaultload(node.value)) {
            logger.debug("Pruning node {} due pruned faultload", node.value);
            return true;
        }

        return false;
    }

    private List<Fault> expand(FaultUid node) {
        List<Fault> collection = new ArrayList<>();
        for (var mode : store.getModes()) {
            collection.add(new Fault(node, mode));
        }
        return collection;
    }

    public void expand(TreeNode node) {
        if (node == null || node.expansion.isEmpty()) {
            return;
        }

        for (int i = 0; i < node.expansion.size(); i++) {
            FaultUid expansionElement = node.expansion.get(i);
            List<FaultUid> newExpansion = node.expansion.subList(i + 1, node.expansion.size());

            for (Fault additionalElement : expand(expansionElement)) {
                Set<Fault> newValue = Sets.plus(node.value(), additionalElement);
                var newNode = new TreeNode(newValue, newExpansion);

                // Check if the new value is supposed to be pruned
                if (shouldPrune(newNode)) {
                    continue;
                }

                toExpand.add(newNode);
            }

        }
    }

    @Override
    public boolean hasNext() {
        boolean hasMore = !toExpand.isEmpty();
        return hasMore;
    }

    public void skip() {
        expand(toExpand.remove(0));
    }

    @Override
    public Set<Fault> next() {
        TreeNode node = toExpand.remove(0);
        expand(node);
        maxSize = Math.max(maxSize, toExpand.size());
        return node.value;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    // Add new element to explore
    public void add(FaultUid extension) {
        for (Fault additionalElement : expand(extension)) {
            Set<Fault> newValue = Set.of(additionalElement);
            var newNode = new TreeNode(newValue, List.copyOf(elements));

            if (shouldPrune(newNode)) {
                continue;
            }

            toExpand.add(newNode);
        }

        elements.add(extension);
    }

    public void addConditional(Set<Fault> condition, FaultUid extension) {

        // We cannot expand to extensions already in the condition
        Set<FaultUid> alreadyExpanded = condition.stream()
                .map(f -> f.uid())
                .collect(Collectors.toSet());

        List<FaultUid> expansionsLeft = elements.stream()
                .filter(e -> !alreadyExpanded.contains(e))
                .filter(e -> !e.equals(extension))
                .toList();

        for (Fault additionalElement : expand(extension)) {
            // Add new node
            Set<Fault> newValue = Sets.plus(condition, additionalElement);
            var newNode = new TreeNode(newValue, expansionsLeft);

            if (shouldPrune(newNode)) {
                continue;
            }

            toExpand.add(newNode);
        }

        if (!elements.contains(extension)) {
            elements.add(extension);
        }
    }

    public void pruneQueue() {
        // Remove pruned nodes
        toExpand.removeIf(this::shouldPrune);
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
        return maxSize;
    }

    public int getQueuSize() {
        return toExpand.size();
    }
}
