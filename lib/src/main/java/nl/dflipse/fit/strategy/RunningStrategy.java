package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.Faultload;

public class RunningStrategy {
    public List<Faultload> toRun = new ArrayList<>();
    public List<FaultloadResult> historicResults = new ArrayList<>();
    public List<FaultloadResult> prunedFaultloads = new ArrayList<>();
    


}
