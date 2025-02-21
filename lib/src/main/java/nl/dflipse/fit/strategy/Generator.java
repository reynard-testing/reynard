package nl.dflipse.fit.strategy;

import java.util.List;

import nl.dflipse.fit.faultload.Fault;

public interface Generator {
    public List<Fault> generate(FaultloadResult result);
}
