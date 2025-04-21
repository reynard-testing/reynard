package nl.dflipse.fit.strategy.components;

import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.util.Pair;

public interface PruneContext {
    public List<Pair<Set<Fault>, List<Behaviour>>> getHistoricResults();

    public List<FailureMode> getFailureModes();

    public List<FaultUid> getFaultInjectionPoints();

    public Set<Behaviour> getExpectedBehaviours(Set<Fault> faultload);

    public Set<FaultUid> getExpectedPoints(Set<Fault> faultload);

    public long spaceSize();
}
