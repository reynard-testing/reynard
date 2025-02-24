package nl.dflipse.fit.instrument;

import java.io.IOException;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public interface FaultController {
    public TraceTreeSpan getTrace(Faultload faultload) throws IOException;

    public void registerFaultload(Faultload faultload);
}
