package io.github.delanoflipse.fit.suite.strategy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalOrder;
import io.github.delanoflipse.fit.suite.strategy.util.traversal.TraversalStrategy;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class TraceAnalysis {
    private final Logger logger = LoggerFactory.getLogger(TraceAnalysis.class);

    private final Set<FaultUid> faultUids = new LinkedHashSet<>();
    private final Set<Fault> injectedFaults = new LinkedHashSet<>();
    private final Set<Fault> traceFaults = new LinkedHashSet<>();
    private final List<TraceReport> reports = new ArrayList<>();
    private final Map<FaultUid, TraceReport> reportByPoint = new HashMap<>();
    private final List<Behaviour> behaviours = new ArrayList<>();
    private TraceReport rootReport;

    private boolean hasIncomplete = false;
    private boolean hasInitial = false;
    private boolean hasMultipleInitial = false;
    private boolean hasMultipleReports = false;

    // --- Parent-Child relations
    TransativeRelation<FaultUid> parentChildRelation = new TransativeRelation<>();
    UndirectedRelation<FaultUid> concurrentRelation = new UndirectedRelation<>();

    public TraceAnalysis(List<TraceReport> reports) {
        // Parent null indicates the root request
        for (var report : reports) {
            analyseReport(report);
        }

        // Ensure all parents are reported
        for (FaultUid uid : parentChildRelation.getElements()) {
            if (uid == null || uid.isRoot()) {
                continue;
            }

            if (!reportByPoint.containsKey(uid)) {
                hasIncomplete = true;
                logger.debug("Missing report for parent {}", uid);
            }
        }
    }

    private void addParents(FaultUid uid) {
        FaultUid current = uid;
        while (current.hasParent()) {
            FaultUid next = current.getParent();
            parentChildRelation.addRelation(next, current);
            current = next;
        }
    }

    private void analyseReport(TraceReport report) {
        // Save map of points by faultUid
        if (!reportByPoint.containsKey(report.injectionPoint)) {
            reportByPoint.put(report.injectionPoint, report);
            reports.add(report);
        } else {
            hasMultipleReports = true;
        }

        behaviours.add(report.getBehaviour());

        if (report.hasFaultBehaviour()) {
            traceFaults.add(report.getFault());
        }

        // Update parent-child relation
        if (report.injectionPoint.hasParent()) {
            addParents(report.injectionPoint);
        }

        // Update concurrent relations
        if (report.concurrentTo != null) {
            for (var concurrent : report.concurrentTo) {
                concurrentRelation.addRelation(report.injectionPoint, concurrent);
            }
        }

        // Handle initial report
        if (report.isInitial) {
            if (rootReport != null && rootReport.response != null
                    && !rootReport.injectionPoint.equals(report.injectionPoint)) {
                hasMultipleInitial = true;
            }

            rootReport = report;
            hasInitial = true;
        } else {
            // Do not inject faults between client and first proxy
            faultUids.add(report.injectionPoint);
        }

        // Handle injected faults
        if (report.injectedFault != null) {
            injectedFaults.add(report.injectedFault);
        }

        // The response is null if the request was not completed yet
        // This can happen, as the report is updated after the response is sent through
        // the proxy
        if (report.response == null) {
            hasIncomplete = true;
        }

    }

    public Set<FaultUid> getFaultUids() {
        return faultUids;
    }

    public Set<Fault> getInjectedFaults() {
        return injectedFaults;
    }

    public List<Behaviour> getBehaviours() {
        return behaviours;
    }

    public Set<Fault> getReportedFaults() {
        return traceFaults;
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
        if (hasMultipleInitial) {
            logger.debug(
                    "Trace has multiple roots! This is likely because the first request does not go through a proxy. Ensure that the first request goes through a proxy!");
            return true;
        }

        if (hasIncomplete) {
            logger.debug("Trace is incomplete!");
            return true;
        }

        if (!hasInitial) {
            logger.warn(
                    "Trace is not incomplete, but has no initial request! Ensure that the first request goes through a proxy!");
            return true;
        }

        if (hasMultipleReports) {
            logger.warn("Trace has multiple reports for a single point! That should not happen!");
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
        var parents = parentChildRelation.getParentsOf(faultUid);

        if (parents.isEmpty()) {
            return null; // No parent found
        }

        if (parents.size() > 1) {
            // It should be a tree..
            logger.warn("Multiple parents found for faultUid {}: {}", faultUid, parents);
        }
        // Return the first parent found
        return Sets.getOnlyElement(parents);
    }

    public TraceReport getParent(TraceReport faultUid) {
        var parent = getParent(faultUid.injectionPoint);

        if (parent == null) {
            return null; // No parent found
        }

        return getReportByFaultUid(parent);
    }

    public Set<FaultUid> getDecendants(FaultUid node) {
        return parentChildRelation.getDecendants(node);
    }

    public List<TraceReport> getDecendants(TraceReport report) {
        return getReports(getDecendants(report.injectionPoint));
    }

    public Set<FaultUid> getChildren(FaultUid node) {
        return parentChildRelation.getChildren(node);
    }

    public List<TraceReport> getChildren(TraceReport report) {
        return getReports(getChildren(report.injectionPoint));
    }

    public Set<FaultUid> getNeighbours(FaultUid child) {
        FaultUid parent = getParent(child);
        Set<FaultUid> neighbours = getChildren(parent);
        neighbours.remove(child);
        return neighbours;
    }

    public List<TraceReport> getNeighbours(TraceReport report) {
        Set<FaultUid> neighbours = getNeighbours(report.injectionPoint);
        return getReports(neighbours);
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

    public List<TraceReport> getReports(TraversalOrder strategy) {
        var traversal = new TraversalStrategy<TraceReport>(strategy);
        List<Pair<TraceReport, TraceReport>> edges = parentChildRelation.getRelations().stream()
                .filter(pair -> pair.first() != null && pair.second() != null)
                .filter(pair -> reportByPoint.containsKey(pair.first())
                        && reportByPoint.containsKey(pair.second()))
                .map(pair -> new Pair<TraceReport, TraceReport>(getReportByFaultUid(pair.first()),
                        getReportByFaultUid(pair.second())))
                .toList();
        List<TraceReport> foundReports = traversal.traverse(rootReport, edges);

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

    public List<FaultUid> getFaultUids(TraversalOrder strategy) {
        var traversal = new TraversalStrategy<FaultUid>(strategy);
        List<FaultUid> foundFaults = traversal.traverse(rootReport.injectionPoint, parentChildRelation.getRelations());

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

    public void traverseReports(TraversalOrder strategy, boolean includeInitial, Consumer<TraceReport> consumer) {
        Consumer<FaultUid> mappedConsumer = (faultUid) -> {
            var report = getReportByFaultUid(faultUid);
            if (report != null) {
                consumer.accept(report);
            }
        };

        List<TraceReport> reports = getReports(strategy);
        reports.stream()
                .filter(report -> includeInitial || !report.isInitial)
                .map(report -> report.injectionPoint)
                .forEach(mappedConsumer);
    }

}
