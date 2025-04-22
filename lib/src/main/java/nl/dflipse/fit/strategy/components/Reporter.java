package nl.dflipse.fit.strategy.components;

import java.util.Map;

public interface Reporter {
    public Map<String, String> report(PruneContext context);
}
