package nl.dflipse.fit.strategy.generators;

import java.util.List;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.FaultloadResult;

public interface Generator {
    public List<Faultload> generate(FaultloadResult result);
}
