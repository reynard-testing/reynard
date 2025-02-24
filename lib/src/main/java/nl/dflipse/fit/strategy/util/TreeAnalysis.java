package nl.dflipse.fit.strategy.util;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class TreeAnalysis {
    public TraceTreeSpan rootNode;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();

    public TreeAnalysis(TraceTreeSpan rootNode) {
        this.rootNode = rootNode;
        // Parent null indicates the root request
        findParentChilds(rootNode, null);
    }

    private void findParentChilds(TraceTreeSpan node, FaultUid parent) {
        FaultUid nextParent = parent;

        if (node.report != null) {
            var child = node.report.faultUid;
            parentChildRelation.addRelation(parent, child);
            nextParent = child;
        }

        for (var child : node.children) {
            findParentChilds(child, nextParent);
        }
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

}
