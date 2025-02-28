package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class TreeAnalysis {
    public TraceTreeSpan rootNode;

    private Set<FaultUid> faultUids;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();

    public TreeAnalysis(TraceTreeSpan rootNode) {
        this.rootNode = rootNode;
        this.faultUids = new HashSet<>();
        // Parent null indicates the root request
        findParentChilds(rootNode, null);
    }

    private void findParentChilds(TraceTreeSpan node, FaultUid parent) {
        FaultUid nextParent = parent;

        if (node.report != null) {
            faultUids.add(node.report.faultUid);
            var child = node.report.faultUid;
            parentChildRelation.addRelation(parent, child);
            nextParent = child;
        }

        for (var child : node.children) {
            findParentChilds(child, nextParent);
        }
    }

    public Set<FaultUid> getFaultUids() {
        return faultUids;
    }

    public List<Pair<FaultUid, FaultUid>> getRelations() {
        return parentChildRelation.getRelations();
    }

    public List<Pair<FaultUid, FaultUid>> getTransativeRelations() {
        return parentChildRelation.getTransativeRelations();
    }

    public FaultUid getParent(FaultUid faultUid) {
        return parentChildRelation.getParent(faultUid);
    }

    public Set<FaultUid> getDecendants(FaultUid parent) {
        return parentChildRelation.getDecendants(parent);
    }

    public Set<FaultUid> getChildren(FaultUid parent) {
        return parentChildRelation.getChildren(parent);
    }

    public List<FaultUid> getParents(FaultUid parent) {
        List<FaultUid> parents = parentChildRelation.getParents(parent);
        parents.add(null);
        return parents;
    }

    public FaultUid getFirstCommonAncestor(FaultUid fault1, FaultUid fault2) {
        return parentChildRelation.getFirstCommonAncestor(fault1, fault2);
    }

    public boolean isDecendantOf(FaultUid parent, FaultUid child) {
        return parentChildRelation.hasTransativeRelation(parent, child);
    }

    public boolean isEqual(FaultUid fault1, FaultUid fault2) {
        if (fault1 == null) {
            return fault2 == null;
        }

        if (fault1.equals(fault2)) {
            return true;
        }

        return false;
    }

    public boolean sameParent(FaultUid fault1, FaultUid fault2) {
        return isEqual(getParent(fault1), getParent(fault2));
    }

    public enum TraversalStrategy {
        DEPTH_FIRST, BREADTH_FIRST
    }

    public List<FaultUid> getFaultUids(TraversalStrategy strategy) {
        List<FaultUid> faults = new ArrayList<>();
        traverseFaults(faults::add, strategy);
        return faults;
    }

    public void traverseFaults(Consumer<FaultUid> consumer, TraversalStrategy strategy) {
        switch (strategy) {
            case DEPTH_FIRST:
                traverseDepthFirst(null, consumer);
                break;
            case BREADTH_FIRST:
                traverseBreadthFirst(null, consumer);
                break;
        }
    }

    public void traverseDepthFirst(FaultUid node, Consumer<FaultUid> consumer) {
        for (var child : getChildren(node)) {
            traverseDepthFirst(child, consumer);
        }

        if (node != null) {
            consumer.accept(node);
        }

    }

    public void traverseBreadthFirst(FaultUid node, Consumer<FaultUid> consumer) {
        if (node != null) {
            consumer.accept(node);
        }

        for (var child : getChildren(node)) {
            traverseBreadthFirst(child, consumer);
        }

    }

}
