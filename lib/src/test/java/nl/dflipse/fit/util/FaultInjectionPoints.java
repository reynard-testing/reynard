package nl.dflipse.fit.util;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.FaultUid;

public class FaultInjectionPoints {

    public static List<FaultUid> getPoints(int n) {
        List<FaultUid> fids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String countStr = "" + i;
            var fid = new FaultUid(countStr, countStr, countStr, countStr, i);
            fids.add(fid);
        }
        return fids;
    }
}
