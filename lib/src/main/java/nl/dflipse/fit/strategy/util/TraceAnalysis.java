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
import nl.dflipse.fit.trace.tree.TraceReport;

public class TraceAnalysis {
    private final Logger logger = LoggerFactory.getLogger(TraceAnalysis.class);

    private final Set<FaultUid> faultUids = new HashSet<>();
    private final Set<Fault> injectedFaults = new HashSet<>();
    private final List<TraceReport> reports = new ArrayList<>();
    private final Map<FaultUid, TraceReport> reportByPoint = new HashMap<>();
    private TraceReport rootReport;

    private boolean anyIncomplete = false;
    private boolean hasInitial = false;
    private boolean hasMultipleRoots = false;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();
    UndirectedRelation<FaultUid> concurrentRelation = new UndirectedRelation<>();

    public TraceAnalysis(List<TraceReport> reports) {
        // Parent null indicates the root request
        for (var report : reports) {
            analyseReport(report);
        }
    }

    private void analyseReport(TraceReport report) {
        if (!reportByPoint.containsKey(report.faultUid)) {
            reports.add(report);
            reportByPoint.put(report.faultUid, report);
        }

        if (report.faultUid.hasParent()) {
            var parent = report.faultUid.getParent();
            parentChildRelation.addRelation(parent, report.faultUid);
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

    public List<TraceReport> getReports() {
        return reports;
    }

    public List<TraceReport> getReports(Set<FaultUid> faultUids) {
        return getReports(List.copyOf(faultUids));
    }

    public List<TraceReport> getReports(List<FaultUid> faultUids) {
        List<TraceReport> reports = new ArrayList<>();
        for (var faultUid : faultUids) {
            var report = getReportByFaultUid(faultUid);
            if (report != null) {
                reports.add(report);
            }
        }
        return reports;
    }

    public TraceReport getReportByFaultUid(FaultUid faultUid) {
        return reportByPoint.get(faultUid);
    }

    public TraceReport getRootReport() {
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

    public List<TraceReport> getChildren(TraceReport report) {
        return getReports(getChildren(report.faultUid));
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
        DEPTH_FIRST, BREADTH_FIRST, RANDOM
    }

    public List<TraceReport> getReports(TraversalStrategy strategy) {
        List<TraceReport> foundReports = new ArrayList<>();
        traverseFaults(strategy, false, f -> {
            var report = getReportByFaultUid(f);
            if (report != null) {
                foundReports.add(report);
            }
        });

        // ensure each known fault is present, not just those in the tree
        int missing = 0;
        for (var report : reports) {
            if (!foundReports.contains(report)) {
                foundReports.add(report);
                missing++;
            }
        }

        if (missing > 0) {
            logger.warn("Missing " + missing + " reports in trace tree!");
        }

        return foundReports;
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
            case RANDOM:
                traverseRandom(null, includeInitial, consumer);
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

    public void traverseRandom(FaultUid node, boolean includeInitial, Consumer<FaultUid> consumer) {
        boolean depthFirstForThisNode = Math.random() < 0.5;
        if (depthFirstForThisNode) {

            for (var child : getChildren(node)) {
                traverseRandom(child, includeInitial, consumer);
            }
        }

        if (node != null) {
            if (includeInitial || !node.isFromInitial()) {
                consumer.accept(node);
            }
        }

        if (!depthFirstForThisNode) {
            for (var child : getChildren(node)) {
                traverseRandom(child, includeInitial, consumer);
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
