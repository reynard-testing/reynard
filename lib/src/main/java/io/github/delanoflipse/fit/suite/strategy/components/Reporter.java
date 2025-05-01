package io.github.delanoflipse.fit.suite.strategy.components;

import java.util.Map;

public interface Reporter {
    public Map<String, String> report(PruneContext context);
}
