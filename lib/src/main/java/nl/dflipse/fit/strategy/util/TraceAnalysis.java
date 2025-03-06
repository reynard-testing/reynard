package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.tree.TraceSpanReport;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class TraceAnalysis {
    private final Set<FaultUid> faultUids = new HashSet<>();
    private final Set<Fault> faults = new HashSet<>();
    private final Set<TraceTreeSpan> treeFaultPoints = new HashSet<>();

    private boolean isIncomplete = false;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();
    UndirectedRelation<FaultUid> concurrentRelation = new UndirectedRelation<>();

    public TraceAnalysis(TraceTreeSpan rootNode, List<TraceSpanReport> reports) {
        // Parent null indicates the root request
        for (var report : reports) {
            faultUids.add(report.faultUid);

            if (report.injectedFault != null) {
                faults.add(report.injectedFault);
            }
        }

        analyseNode(rootNode, null);
        findConcurrent();
    }

    /** Analyse the node, given the most direct FaultUid ancestor */
    private void analyseNode(TraceTreeSpan node, FaultUid parent) {
        FaultUid nextParent = parent;

        // Check if the node has a report from a fault injection proxy
        if (node.hasReport()) {
            // Save the faultUid
            treeFaultPoints.add(node);

            // Save the parent-child relation
            // Update the most direct parent
            var child = node.report.faultUid;
            parentChildRelation.addRelation(parent, child);
            nextParent = child;

            // Check if the node is incomplete
            // If the response is null, the fault point was reached, but the response was
            // not yet received
            if (node.report.response == null) {
                isIncomplete = true;
            }

            // If a remote call is detected (so our own proxy)
            // But no children exist.
            // TODO: is this solvable? Often is a misconfiguration, but we can still
            // exercise the FI point.
            // if (node.children.isEmpty()) {
            // isIncomplete = true;
            // }
        }

        for (var child : node.children) {
            analyseNode(child, nextParent);
        }
    }

    private boolean timeOverlap(long start1, long end1, long start2, long end2) {
        return start1 < end2 && end1 > start2;
    }

    private boolean timeOverlap(TraceTreeSpan n1, TraceTreeSpan n2) {
        return timeOverlap(n1.span.startTime, n1.span.endTime, n2.span.startTime, n2.span.endTime);
    }

    private void areConcurrent(TraceTreeSpan n1, TraceTreeSpan n2) {
        // Must have the same parent

        if (n1.span.parentSpanId == null || !n1.span.parentSpanId.equals(n2.span.parentSpanId)) {
            return;
        }

        if (timeOverlap(n1, n2)) {
            concurrentRelation.addRelation(n1.report.faultUid, n2.report.faultUid);
        }
    }

    private void findConcurrent() {
        var fiArray = treeFaultPoints.toArray(new TraceTreeSpan[0]);

        for (int i = 0; i < fiArray.length; i++) {
            for (int j = i + 1; j < fiArray.length; j++) {
                var s1 = fiArray[i];
                var s2 = fiArray[j];

                areConcurrent(s1, s2);
            }
        }
    }

    public Set<FaultUid> getFaultUids() {
        return faultUids;
    }

    public Set<Fault> getFaults() {
        return faults;
    }

    public boolean isIncomplete() {
        return isIncomplete;
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

        return fault1.equals(fault2);
    }

    public boolean sameParent(FaultUid fault1, FaultUid fault2) {
        return isEqual(getParent(fault1), getParent(fault2));
    }

    public enum TraversalStrategy {
        DEPTH_FIRST, BREADTH_FIRST
    }

    public List<FaultUid> getFaultUids(TraversalStrategy strategy) {
        List<FaultUid> foundFaults = new ArrayList<>();
        traverseFaults(foundFaults::add, strategy);

        // ensure each known fault is present, not just those in the tree
        int missing = 0;
        for (var fp : faultUids) {
            if (!foundFaults.contains(fp)) {
                foundFaults.add(fp);
                missing++;
            }
        }

        if (missing > 0) {
            System.out.println("[WARN] Missing " + missing + " faultUids in trace tree!");
        }

        return foundFaults;
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
