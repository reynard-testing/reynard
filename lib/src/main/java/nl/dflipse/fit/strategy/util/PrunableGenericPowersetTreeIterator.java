package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;

public class PrunableGenericPowersetTreeIterator implements Iterator<Set<Fault>> {
    private final Logger logger = LoggerFactory.getLogger(PrunableGenericPowersetTreeIterator.class);
    private final List<FaultUid> points;
    private final List<FailureMode> modes;
    private final List<TreeNode> toExpand = new ArrayList<>();
    private int maxQueueSize;
    private final Predicate<Set<Fault>> prunePredicate;

    public record TreeNode(Set<Fault> value, List<FaultUid> expansion) {
    }

    public PrunableGenericPowersetTreeIterator(List<FaultUid> points,
            List<FailureMode> modes,
            Predicate<Set<Fault>> prunePredicate,
            boolean skipEmptySet) {

        this.prunePredicate = prunePredicate;
        this.points = new ArrayList<>();
        this.modes = modes;

        if (points != null && !points.isEmpty()) {
            this.points.addAll(points);
        }

        toExpand.add(new TreeNode(Set.of(), List.copyOf(points)));
        maxQueueSize = 1;

        if (skipEmptySet) {
            this.skip();
        }
    }

    public PrunableGenericPowersetTreeIterator(DynamicAnalysisStore store, boolean skipEmptySet) {
        this(store.getFaultUids().stream().toList(), store.getModes(), store::isRedundant, skipEmptySet);
    }

    private boolean shouldPrune(TreeNode node) {
        return prunePredicate.test(node.value);
    }

    private List<Fault> expand(FaultUid node) {
        List<Fault> collection = new ArrayList<>();
        for (var mode : modes) {
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
        maxQueueSize = Math.max(maxQueueSize, toExpand.size());
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
            var newNode = new TreeNode(newValue, List.copyOf(points));

            if (shouldPrune(newNode)) {
                continue;
            }

            toExpand.add(newNode);
        }

        points.add(extension);
    }

    public void addConditional(Set<Fault> condition, FaultUid extension) {

        // We cannot expand to extensions already in the condition
        Set<FaultUid> alreadyExpanded = condition.stream()
                .map(f -> f.uid())
                .collect(Collectors.toSet());

        List<FaultUid> expansionsLeft = points.stream()
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

        if (!points.contains(extension)) {
            points.add(extension);
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
        return maxQueueSize;
    }

    public int getQueuSize() {
        return toExpand.size();
    }
}
