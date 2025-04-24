package nl.dflipse.fit.testutil;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.FaultInjectionPoint;
import nl.dflipse.fit.faultload.FaultUid;

public class FaultInjectionPoints {

    public static FaultUid getPoint(int x) {
        String countStr = "" + x;
        FaultInjectionPoint p = new FaultInjectionPoint(countStr, countStr, countStr, x);
        return new FaultUid(List.of(p));
    }

    public static List<FaultUid> getPoints(int n) {
        List<FaultUid> fids = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            fids.add(getPoint(i));
        }

        return fids;
    }
}
