package nl.dflipse.fit.strategy.generators;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.PrunableGenericPowersetTreeIterator;
import nl.dflipse.fit.strategy.util.SpaceEstimate;

public class IncreasingSizeGenerator extends Generator implements Reporter {
    private final Logger logger = LoggerFactory.getLogger(IncreasingSizeGenerator.class);
    private final PrunableGenericPowersetTreeIterator iterator;

    private final DynamicAnalysisStore store;

    public IncreasingSizeGenerator(DynamicAnalysisStore store) {
        this.store = store;
        iterator = new PrunableGenericPowersetTreeIterator(store, true);
    }

    public IncreasingSizeGenerator(List<FailureMode> modes) {
        this(new DynamicAnalysisStore(modes));
    }

    @Override
    public void reportFaultUid(FaultUid potentialFault) {
        if (potentialFault == null) {
            return;
        }

        boolean isNew = store.addFaultUid(potentialFault);
        if (!isNew) {
            return;
        }

        iterator.add(potentialFault);
    }

    @Override
    public void reportUpstreamEffect(FaultUid cause, Collection<FaultUid> effect) {
        store.addUpstreamEffect(cause, effect);
    }

    @Override
    public void reportPreconditionOfFaultUid(Collection<Behaviour> condition, FaultUid fid) {
        if (fid == null) {
            return;
        }

        if (iterator == null) {
            throw new IllegalStateException(
                    "Cannot add conditional fault injection point if no normal fault injection points are discovered");
        }

        store.addConditionForFaultUid(condition, fid);
    }

    @Override
    public void exploreFrom(Collection<Fault> startingNode) {
        if (iterator == null) {
            throw new IllegalStateException(
                    "Cannot explore from fault injection point if no normal fault injection points are discovered");
        }

        iterator.expandFrom(startingNode);
    }

    @Override
    public void reportExclusionOfFaultUid(Collection<Behaviour> condition, FaultUid exclusion) {
        if (exclusion == null) {
            return;
        }

        if (iterator == null) {
            throw new IllegalStateException(
                    "Cannot exlude fault injection point if no normal fault injection points are discovered");
        }

        boolean isNew = store.addExclusionForFaultUid(condition, exclusion);

        if (!isNew) {
            return;
        }

        iterator.pruneQueue();
    }

    @Override
    public void reportDownstreamEffect(Collection<Behaviour> condition, Behaviour effect) {
        store.addDownstreamEffect(condition, effect);
    }

    @Override
    public Faultload generate() {
        // If we have exhausted the mode-faultUid pairings
        // we need to get the next fault injection space point (subset of fault
        // injection points)
        if (iterator == null || !iterator.hasNext()) {
            return null;
        }

        // create next faultload
        var nextFaults = iterator.next();
        Faultload faultLoad = new Faultload(nextFaults);

        // if we should skip this specific faultload
        // then we should generate the next one
        if (store.hasFaultload(faultLoad)) {
            return generate();
        }

        return faultLoad;
    }

    @Override
    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        boolean isNew = false;
        var allFaults = Fault.allFaults(subset, getFailureModes());
        for (Set<Fault> prunedSet : allFaults) {
            boolean wasNew = store.pruneFaultSubset(prunedSet);
            isNew = isNew || wasNew;
        }

        if (isNew && iterator != null) {
            iterator.pruneQueue();
        }
    }

    private int getNumerOfPoints() {
        return store.getPoints().size();
    }

    @Override
    public void pruneFaultSubset(Set<Fault> subset) {
        boolean isNew = store.pruneFaultSubset(subset);
        if (isNew && iterator != null) {
            iterator.pruneQueue();
        }
    }

    @Override
    public void pruneFaultload(Faultload faultload) {
        boolean isNew = store.pruneFaultload(faultload);
        if (isNew && iterator != null) {
            iterator.pruneQueue();
        }
    }

    @Override
    public long spaceSize() {
        return SpaceEstimate.spaceSize(getFailureModes().size(), getNumerOfPoints());
    }

    @Override
    public List<FailureMode> getFailureModes() {
        return store.getModes();
    }

    @Override
    public List<FaultUid> getFaultInjectionPoints() {
        return store.getPoints();
    }

    @Override
    public Set<FaultUid> getExpectedPoints(Set<Fault> faultload) {
        return store.getExpectedPoints(faultload);
    }

    @Override
    public Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload) {
        return store.getExpectedBehaviour(faultload);
    }

    public DynamicAnalysisStore getStore() {
        return store;
    }

    public int getMaxQueueSize() {
        return iterator == null ? 0 : iterator.getMaxQueueSize();
    }

    public int getQueuSize() {
        return iterator == null ? 0 : iterator.getQueuSize();
    }

    public long getSpaceLeft() {
        return iterator == null ? 0 : iterator.size(getFailureModes().size());
    }

    @Override
    public Map<String, String> report() {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("Fault injection points", String.valueOf(getNumerOfPoints()));
        report.put("Modes", String.valueOf(getFailureModes().size()));
        report.put("Redundant faultloads", String.valueOf(store.getRedundantFaultloads().size()));
        report.put("Redundant fault points", String.valueOf(store.getRedundantUidSubsets().size()));
        report.put("Redundant fault subsets", String.valueOf(store.getRedundantFaultSubsets().size()));
        report.put("Max queue size", String.valueOf(getMaxQueueSize()));
        int queueSize = getQueuSize();
        if (queueSize > 0) {
            report.put("Queue size (left)", String.valueOf(getQueuSize()));
            report.put("Space left", String.valueOf(getSpaceLeft()));
        }

        int i = 0;
        for (var point : getFaultInjectionPoints()) {
            report.put("FID(" + i + ")", point.toString());
            i++;
        }

        return report;
    }

}
