package nl.dflipse.fit.util;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.FaultUid;

public class FaultInjectionPoints {

    public static FaultUid getPoint(int x) {
        String countStr = "" + x;
        return new FaultUid(countStr, countStr, countStr, countStr, x);
    }

    public static List<FaultUid> getPoints(int n) {
        List<FaultUid> fids = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            fids.add(getPoint(i));
        }

        return fids;
    }
}
