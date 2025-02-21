package nl.dflipse.fit.strategy.util;

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

    public FaultUid getParent(FaultUid faultUid) {
        return parentChildRelation.getParent(faultUid);
    }

    public Set<FaultUid> getDecendants(FaultUid parent) {
        return parentChildRelation.getDecendants(parent);
    }

    public boolean isDecendantOf(FaultUid parent, FaultUid child) {
        return parentChildRelation.hasTransativeRelation(parent, child);
    }

}
