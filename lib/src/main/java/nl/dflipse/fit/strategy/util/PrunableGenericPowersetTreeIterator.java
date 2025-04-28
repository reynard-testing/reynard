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
            this.visitedPoints.add(Set.of());
        }
    }

    private void updateQueueSize() {
        queueSize.add(toExpand.size());
    }

    private PruneDecision visitIsRedundant(TreeNode node) {
        if (visitedPoints.contains(node.value)) {
            logger.debug("Ignoring and only expanding already visited point {}", node);
            return PruneDecision.PRUNE;
        }

        return PruneDecision.KEEP;
    }

    private PruneDecision max(PruneDecision... decisions) {
        PruneDecision max = PruneDecision.KEEP;
        for (var decision : decisions) {
            if (decision == PruneDecision.PRUNE_SUPERSETS) {
                return PruneDecision.PRUNE_SUPERSETS;
            }

            if (decision == PruneDecision.PRUNE) {
                max = PruneDecision.PRUNE;
            }
        }

        return max;
    }

    private PruneDecision shouldPrune(TreeNode node) {
        PruneDecision localDecision = visitIsRedundant(node);
        PruneDecision storeDecision = store.isRedundant(node.value);
        if (storeDecision == PruneDecision.PRUNE_SUPERSETS) {
            return PruneDecision.PRUNE_SUPERSETS;
        }

        PruneDecision punersDecision = pruneFunction.apply(node.value);

        PruneDecision decision = max(localDecision, storeDecision, punersDecision);
        return decision;
    }

    private List<Fault> expandModes(FaultUid node) {
        List<Fault> collection = new ArrayList<>();
        for (var mode : store.getModes()) {
            collection.add(new Fault(node, mode));
        }
        return collection;
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
                newNodes.add(newNode);
            }
        }

        return newNodes;
    }

    private boolean addToQueue(TreeNode node) {
        // We don't want to add already visited nodes
        if (visitedNodes.contains(node)) {
            return false;
        }

        visitedNodes.add(node);

        int firstIndex = -1;
        int nodeSize = node.value.size();
        for (int i = 0; i < toExpand.size(); i++) {
            int otherNodeSize = toExpand.get(i).value.size();
            if (nodeSize < otherNodeSize) {
                firstIndex = i;
                break;
            }
        }

        if (firstIndex == -1) {
            toExpand.add(node);
            logger.debug("Adding {} to end of the queue", node);
        } else {
            toExpand.add(firstIndex, node);
            logger.debug("Adding {} to queue at index {}", node, firstIndex);
        }

        return true;
    }

    private void addToQueue(Collection<TreeNode> nodes) {
        for (var node : nodes) {
            addToQueue(node);
        }
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
            PruneDecision nodeFate = shouldPrune(node);
            visitedPoints.add(node.value);

            if (nodeFate == PruneDecision.PRUNE_SUPERSETS) {
                // skip this node wholely
                continue;
            }

            addToQueue(expand(node));
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
    public boolean expandFrom(Collection<Fault> nodeValue) {
        // We cannot expand to extensions already in the condition
        List<FaultUid> alreadyExpanded = nodeValue.stream()
                .map(f -> f.uid())
                .toList();

        // Determine the expensions for this node
        List<FaultUid> expansionsLeft = store.getPoints().stream()
                .filter(e -> !alreadyExpanded.stream().anyMatch(x -> x.matches(e)))
                .toList();

        var startingNode = new TreeNode(Set.copyOf(nodeValue), expansionsLeft);
        boolean isNew = addToQueue(startingNode);

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
