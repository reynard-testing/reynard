package io.github.delanoflipse.fit.suite.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;

public class FaultInjectionPoints {

    private static FaultInjectionPoint root = new FaultInjectionPoint("", "", "", Map.of(), 0);

    public static FaultUid getPoint(int x) {
        String countStr = "" + x;
        FaultInjectionPoint p = new FaultInjectionPoint(countStr, countStr, countStr, Map.of(), x);
        return new FaultUid(List.of(root, p));
    }

    public static List<FaultUid> getPoints(int n) {
        List<FaultUid> fids = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            fids.add(getPoint(i));
        }

        return fids;
    }
}
