package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;

public class HistoricStore {
    public List<FaultloadResult> results = new ArrayList<>();

    public void add(FaultloadResult faultloadResult) {
        results.add(faultloadResult);
    }

}
