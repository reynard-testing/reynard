package nl.dflipse.fit.strategy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.tree.TraceSpanReport;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class TraceAnalysis {
    private final Logger logger = LoggerFactory.getLogger(TraceAnalysis.class);

    private final Set<FaultUid> faultUids = new HashSet<>();
    private final Set<Fault> injectedFaults = new HashSet<>();
    private final Set<TraceTreeSpan> treeFaultPoints = new HashSet<>();
    private final List<TraceSpanReport> reports = new ArrayList<>();
    private final Map<FaultUid, TraceSpanReport> reportByPoint = new HashMap<>();
    private TraceSpanReport rootReport;

    private boolean anyIncomplete = false;
    private boolean hasInitial = false;
    private boolean hasMultipleRoots = false;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();
    UndirectedRelation<FaultUid> concurrentRelation = new UndirectedRelation<>();

    public TraceAnalysis(TraceTreeSpan rootNode) {
        this(rootNode, List.of());
    }

    public TraceAnalysis(TraceTreeSpan rootNode, List<TraceSpanReport> reports) {
        // Parent null indicates the root request
        for (var report : reports) {
            analyseReport(report);
        }

        analyseNode(rootNode, null);
    }

    private void analyseReport(TraceSpanReport report) {
        if (!reportByPoint.containsKey(report.faultUid)) {
            reports.add(report);
            reportByPoint.put(report.faultUid, report);
        }

        if (report.isInitial) {
            if (rootReport != null && rootReport.response != null && !rootReport.faultUid.equals(report.faultUid)) {
                hasMultipleRoots = true;
            }

            rootReport = report;
            hasInitial = true;
        } else {
            // Do not inject faults between client and first proxy
            faultUids.add(report.faultUid);
        }

        if (report.injectedFault != null) {
            injectedFaults.add(report.injectedFault);
        }

        if (report.response == null) {
            anyIncomplete = true;
        }

        if (report.concurrentTo != null) {
            for (var concurrent : report.concurrentTo) {
                concurrentRelation.addRelation(report.faultUid, concurrent);
            }
        }
    }

    /** Analyse the node, given the most direct FaultUid ancestor */
    private void analyseNode(TraceTreeSpan node, FaultUid parent) {
        FaultUid nextParent = parent;

        if (node.span.endTime <= 0) {
            anyIncomplete = true;
        }

        // Check if the node has a report from a fault injection proxy
        if (node.hasReport()) {
            treeFaultPoints.add(node);

            var report = node.report;
            analyseReport(report);

            // Save the parent-child relation
            // Update the most direct parent
            var child = report.faultUid;
            parentChildRelation.addRelation(parent, child);
            nextParent = child;

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

    public Set<FaultUid> getFaultUids() {
        return faultUids;
    }

    public Set<Fault> getInjectedFaults() {
        return injectedFaults;
    }

    public Map<FaultUid, Set<FaultUid>> getAllConcurrent() {
        return concurrentRelation.getRelations();
    }

    public boolean hasFaultMode(String... orType) {
        return hasFaultMode(Set.of(orType));
    }

    private boolean hasFaultMode(Set<String> orType) {
        for (var fault : injectedFaults) {
            if (orType.contains(fault.mode().type())) {
                return true;
            }
        }

        return false;
    }

    public List<TraceSpanReport> getReports() {
        return reports;
    }

    public TraceSpanReport getReportByFaultUid(FaultUid faultUid) {
        return reportByPoint.get(faultUid);
    }

    public TraceSpanReport getRootReport() {
        return rootReport;
    }

    public boolean isInvalid() {
        if (hasMultipleRoots) {
            logger.debug(
                    "Trace has multiple roots! This is likely because the first request does not go through a proxy. Ensure that the first request goes through a proxy!");
            return true;
        }

        if (anyIncomplete) {
            logger.debug("Trace is incomplete!");
            return true;
        }

        if (!hasInitial) {
            logger.warn(
                    "Trace is not incomplete, but has no initial request! Ensure that the first request goes through a proxy!");
            return true;
        }

        return false;
    }

    public List<Pair<FaultUid, FaultUid>> getParentsAndChildren() {
        return parentChildRelation.getRelations();
    }

    public List<Pair<FaultUid, FaultUid>> getParentsAndTransativeChildren() {
        return parentChildRelation.getTransativeRelations();
    }

    public FaultUid getParent(FaultUid faultUid) {
        return parentChildRelation.getParent(faultUid);
    }

    public Set<FaultUid> getDecendants(FaultUid node) {
        return parentChildRelation.getDecendants(node);
    }

    public Set<FaultUid> getChildren(FaultUid node) {
        return parentChildRelation.getChildren(node);
    }

    public Set<FaultUid> getNeighbours(FaultUid child) {
        FaultUid parent = getParent(child);
        Set<FaultUid> neighbours = getChildren(parent);
        neighbours.remove(child);
        return neighbours;
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

    public boolean areConcurrent(FaultUid fault1, FaultUid fault2) {
        return concurrentRelation.areRelated(fault1, fault2);
    }

    public enum TraversalStrategy {
        DEPTH_FIRST, BREADTH_FIRST
    }

    public List<FaultUid> getFaultUids(TraversalStrategy strategy) {
        List<FaultUid> foundFaults = new ArrayList<>();
        traverseFaults(strategy, false, foundFaults::add);

        // ensure each known fault is present, not just those in the tree
        int missing = 0;
        for (var fp : faultUids) {
            if (!foundFaults.contains(fp)) {
                foundFaults.add(fp);
                missing++;
            }
        }

        if (missing > 0) {
            logger.warn("Missing " + missing + " faultUids in trace tree!");
        }

        return foundFaults;
    }

    public void traverseFaults(TraversalStrategy strategy, boolean includeInitial, Consumer<FaultUid> consumer) {
        switch (strategy) {
            case DEPTH_FIRST:
                traverseDepthFirst(null, includeInitial, consumer);
                break;
            case BREADTH_FIRST:
                traverseBreadthFirst(null, includeInitial, consumer);
                break;
        }
    }

    public void traverseFaults(Consumer<FaultUid> consumer) {
        traverseFaults(TraversalStrategy.DEPTH_FIRST, true, consumer);
    }

    public void traverseDepthFirst(FaultUid node, boolean includeInitial, Consumer<FaultUid> consumer) {
        for (var child : getChildren(node)) {
            traverseDepthFirst(child, includeInitial, consumer);
        }

        if (node != null) {
            if (includeInitial || !node.isFromInitial()) {
                consumer.accept(node);
            }
        }

    }

    public void traverseBreadthFirst(FaultUid node, boolean includeInitial, Consumer<FaultUid> consumer) {
        if (node != null) {
            if (includeInitial || !node.isFromInitial()) {
                consumer.accept(node);
            }
        }

        for (var child : getChildren(node)) {
            traverseBreadthFirst(child, includeInitial, consumer);
        }

    }

}
