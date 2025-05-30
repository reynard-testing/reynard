package io.github.delanoflipse.fit.suite.strategy.components.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;
import io.github.delanoflipse.fit.suite.strategy.util.Pair;
import io.github.delanoflipse.fit.suite.strategy.util.SpaceEstimate;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public abstract class StoreBasedGenerator extends Generator {
    protected final DynamicAnalysisStore store;

    public StoreBasedGenerator(DynamicAnalysisStore store) {
        this.store = store;
    }

    @Override
    public void reportFaultUid(FaultUid faultInjectionPoint) {
        if (faultInjectionPoint == null) {
            return;
        }

        store.addFaultUid(faultInjectionPoint);
    }

    @Override
    public List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults() {
        return store.getHistoricResults();
    }

    @Override
    public Map<FaultUid, TraceReport> getHappyPath() {
        return store.getHappyPath();
    }

    @Override
    public void reportHappyPath(TraceReport report) {
        store.addHappyPath(report.injectionPoint, report);
    }

    @Override
    public List<FailureMode> getFailureModes() {
        return store.getModes();
    }

    @Override
    public List<FaultUid> getFaultInjectionPoints() {
        return store.getPoints();
    }

    public List<FaultUid> getSimplifiedFaultInjectionPoints() {
        var points = store.getPoints();
        List<FaultUid> res = new ArrayList<>();

        for (var point : points) {
            if (point.isPersistent()) {
                continue;
            }

            FaultUid simplePoint = point.withoutCallStack();

            boolean alreadyPresent = res.stream()
                    .anyMatch(p -> simplePoint.matches(p));
            if (alreadyPresent) {
                continue;
            }

            res.add(simplePoint);
        }

        return res;
    }

    @Override
    public Set<FaultUid> getExpectedPoints(Set<Fault> faultload) {
        return store.getExpectedPoints(faultload);
    }

    @Override
    public Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload) {
        return store.getExpectedBehaviour(faultload);
    }

    protected int getNumerOfPoints() {
        return store.getPoints().size();
    }

    @Override
    public long spaceSize() {
        return SpaceEstimate.spaceSize(getFailureModes().size(), getNumerOfPoints());
    }

    @Override
    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        store.pruneFaultUidSubset(subset);
    }

    @Override
    public void pruneFaultSubset(Set<Fault> subset) {
        store.pruneFaultSubset(subset);
    }

    @Override
    public void pruneFaultload(Faultload faultload) {
        store.pruneFaultload(faultload);
    }

    @Override
    public boolean reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid exclusion) {
        if (exclusion == null) {
            return true;
        }

        return store.addExclusionForFaultUid(condition, exclusion);
    }

    @Override
    public boolean reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect) {
        return store.addDownstreamEffect(condition, effect);
    }

    @Override
    public boolean reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect) {
        return store.addUpstreamEffect(cause, effect);
    }

    @Override
    public boolean reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        if (fid == null) {
            return true;
        }

        return store.addConditionForFaultUid(condition, fid);
    }

    public DynamicAnalysisStore getStore() {
        return store;
    }

}
