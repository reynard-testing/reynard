package nl.dflipse.fit.strategy.generators;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.strategy.Reporter;
import nl.dflipse.fit.strategy.store.DynamicAnalysisStore;
import nl.dflipse.fit.strategy.util.PrunableGenericPowersetTreeIterator;
import nl.dflipse.fit.strategy.util.Sets;
import nl.dflipse.fit.strategy.util.SpaceEstimate;

public class IncreasingSizeGenerator implements Generator, Reporter {
    private final Logger logger = LoggerFactory.getLogger(IncreasingSizeGenerator.class);
    private Set<FaultMode> modes;
    private PrunableGenericPowersetTreeIterator iterator;

    private final DynamicAnalysisStore store;

    public IncreasingSizeGenerator(List<FaultMode> modes) {
        this(Set.copyOf(modes));
    }

    public IncreasingSizeGenerator(Set<FaultMode> modes) {
        this.modes = modes;
        this.store = new DynamicAnalysisStore(modes);
    }

    private Set<Fault> faultUidtoFaults(FaultUid uid) {
        return modes.stream()
                .map(mode -> new Fault(uid, mode))
                .collect(Collectors.toSet());
    }

    @Override
    public void reportFaultUids(List<FaultUid> potentialFaults) {
        if (potentialFaults == null || potentialFaults.isEmpty()) {
            return;
        }

        int m = modes.size();

        if (iterator == null) {
            store.addFaultUids(potentialFaults);
            iterator = new PrunableGenericPowersetTreeIterator(potentialFaults,
                    this.store, true);

            long expectedSize = iterator.size(m);
            logger.info("Found " + potentialFaults.size() + " fault points. Will generate at most "
                    + expectedSize
                    + " new test cases");
        } else {
            long oldSize = iterator.size(m);

            for (var fid : potentialFaults) {
                boolean isNew = store.addFaultUid(fid);
                if (!isNew) {
                    continue;
                }

                logger.info("Found NEW fault injection point: " + fid);
                iterator.add(fid);
            }

            long newSize = iterator.size(m) - oldSize;
            logger.info("Added " + newSize + " new test cases");
        }
    }

    @Override
    public void reportPreconditionOfFaultUid(Set<Fault> condition, FaultUid fid) {
        if (fid == null) {
            return;
        }

        if (condition.isEmpty()) {
            reportFaultUids(List.of(fid));
            return;
        }

        if (iterator == null) {
            throw new IllegalStateException(
                    "Cannot add conditional fault injection point if no normal fault injection points are discovered");
        }

        int m = modes.size();
        long oldSize = iterator.size(m);
        boolean isNew = store.addConditionForFaultUid(condition, fid);

        if (!isNew) {
            return;
        }

        iterator.pruneQueue();
        iterator.addConditional(condition, fid);

        long newSize = iterator.size(m) - oldSize;
        logger.info("Added " + newSize + " new test cases");
    }

    @Override
    public void reportExclusionOfFaultUid(Set<Fault> condition, FaultUid fid) {
        if (fid == null) {
            return;
        }

        if (condition.isEmpty()) {
            throw new IllegalArgumentException("Exclusion must be non-empty");
        }

        if (iterator == null) {
            throw new IllegalStateException(
                    "Cannot exlude fault injection point if no normal fault injection points are discovered");
        }

        boolean isNew = store.addExclusionForFaultUid(condition, fid);

        if (!isNew) {
            return;
        }

        iterator.pruneQueue();
    }

    @Override
    public List<Faultload> generate() {
        // If we have exhausted the mode-faultUid pairings
        // we need to get the next fault injection space point (subset of fault
        // injection points)
        if (iterator == null || !iterator.hasNext()) {
            return List.of();
        }

        // create next faultload
        var nextFaults = iterator.next();
        Faultload faultLoad = new Faultload(nextFaults);

        // if we should skip this specific faultload
        // then we should generate the next one
        if (store.hasFaultload(faultLoad)) {
            return generate();
        }

        return List.of(faultLoad);
    }

    private Set<Set<Fault>> allFaults(List<FaultUid> uids) {
        return allFaults(uids, Set.of());
    }

    private Set<Set<Fault>> allFaults(List<FaultUid> uids, Set<Fault> current) {
        if (uids.isEmpty()) {
            return Set.of(current);
        }

        var head = uids.get(0);
        var tail = uids.subList(1, uids.size());
        Set<Fault> additions = faultUidtoFaults(head);
        Set<Set<Fault>> allSets = new HashSet<>();
        for (var addition : additions) {
            Set<Fault> newCurrent = Sets.plus(current, addition);
            allSets.addAll(allFaults(tail, newCurrent));
        }

        return allSets;
    }

    @Override
    public void pruneFaultUidSubset(Set<FaultUid> subset) {
        boolean isNew = false;
        for (Set<Fault> prunedSet : allFaults(List.copyOf(subset))) {
            isNew = isNew || store.pruneFaultSubset(prunedSet);
        }

        if (isNew && iterator != null) {
            iterator.pruneQueue();
        }

    }

    private int getNumerOfPoints() {
        return store.getFaultInjectionPoints().size();
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
        return SpaceEstimate.spaceSize(modes.size(), getNumerOfPoints());
    }

    @Override
    public Set<FaultMode> getFaultModes() {
        return modes;
    }

    @Override
    public Set<FaultUid> getFaultInjectionPoints() {
        return store.getFaultInjectionPoints();
    }

    @Override
    public Map<FaultUid, Set<Set<Fault>>> getConditionalFaultInjectionPoints() {
        return store.getAppearPreconditions().getStore();
    }

    @Override
    public Map<FaultUid, Set<Set<Fault>>> getExclusionsForFaultInjectionPoints() {
        return store.getDisappearPreconditions().getStore();
    }

    @Override
    public Set<FaultUid> getExpectedPoints(Faultload faultload) {
        Set<Fault> faults = faultload.faultSet();
        Set<FaultUid> basePoints = store.getNonConditionalFaultUids();
        basePoints.addAll(store.getAppearPreconditions().getForPrecondition(faults));
        basePoints.removeAll(store.getDisappearPreconditions().getForPrecondition(faults));

        return basePoints;
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
        return iterator == null ? 0 : iterator.size(this.modes.size());
    }

    @Override
    public Map<String, String> report() {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("Fault injection points", String.valueOf(getNumerOfPoints()));
        report.put("Modes", String.valueOf(getFaultModes().size()));
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
