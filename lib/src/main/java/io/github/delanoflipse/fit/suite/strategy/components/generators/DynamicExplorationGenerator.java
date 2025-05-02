package io.github.delanoflipse.fit.suite.strategy.components.generators;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.Faultload;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.components.PruneContext;
import io.github.delanoflipse.fit.suite.strategy.components.PruneDecision;
import io.github.delanoflipse.fit.suite.strategy.components.Reporter;
import io.github.delanoflipse.fit.suite.strategy.store.DynamicAnalysisStore;

public class DynamicExplorationGenerator extends StoreBasedGenerator implements FeedbackHandler, Reporter {

    public DynamicExplorationGenerator(DynamicAnalysisStore store, Function<Set<Fault>, PruneDecision> pruneFunction) {
        super(store);
    }

    public DynamicExplorationGenerator(List<FailureMode> modes, Function<Set<Fault>, PruneDecision> pruneFunction) {
        this(new DynamicAnalysisStore(modes), pruneFunction);
    }

    @Override
    public Map<String, String> report(PruneContext context) {
        return Map.of();
    }

    @Override
    public Faultload generate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'generate'");
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'exploreFrom'");
    }

    @Override
    public boolean exploreFrom(Collection<Fault> startingNode, Collection<FaultUid> combinations) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'exploreFrom'");
    }

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleFeedback'");
    }

}
