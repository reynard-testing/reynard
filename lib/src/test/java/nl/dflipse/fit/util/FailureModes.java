package nl.dflipse.fit.util;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.faultmodes.FaultMode;

public class FailureModes {
    public static List<FaultMode> getModes(int n) {
        List<FaultMode> modes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String istr = "" + i;
            modes.add(new FaultMode(istr, List.of(istr)));
        }
        return modes;
    }
}
