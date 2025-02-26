package nl.dflipse.fit.strategy.generators;

import java.util.List;

import nl.dflipse.fit.faultload.Faultload;

public interface Generator {
    public List<Faultload> generate();
}
