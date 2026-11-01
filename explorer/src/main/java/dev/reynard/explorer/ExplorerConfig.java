package dev.reynard.explorer;

import dev.reynard.junit.strategy.util.traversal.TraversalOrder;

/**
 * Options of one exploration. Mirrors the options of the @FiTest annotation, so
 * they can also be given as JSON (absent fields keep their default).
 */
public class ExplorerConfig {
    /** Name of the explored scenario, used in reports */
    public String name = "";
    /** Directory to export reports to (none if null) */
    public String outputDir = null;

    public boolean maskPayload = false;
    public boolean withPredecessors = false;
    public boolean hashBody = false;
    public boolean logHeaders = false;
    public boolean failStop = true;
    public boolean optimizeForRetries = false;
    public boolean optimizeForImpactless = false;
    public boolean checkAllPruners = false;
    /** 0 indicates no bound */
    public long maxTestCases = 0;
    /** 0 indicates no bound */
    public long maxTimeS = 0;
    /** 0 indicates no bound */
    public int maxFaultloadSize = 0;
    public int initialGetTraceDelay = 0;
    public TraversalOrder pointOrder = TraversalOrder.DEPTH_FIRST_POST_ORDER;
    public boolean depthFirstSearchOrder = false;
}
