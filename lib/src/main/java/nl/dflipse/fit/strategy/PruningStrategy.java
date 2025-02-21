package nl.dflipse.fit.strategy;

import java.util.List;

import nl.dflipse.fit.faultload.Faultload;

public interface PruningStrategy {
    public boolean filter(Faultload faultload, List<FaultloadResult> historicResults);
}
