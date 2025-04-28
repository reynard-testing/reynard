package nl.dflipse.fit.testutil;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.faultload.modes.FailureMode;

public class FailureModes {
    public static FailureMode getMode(int i) {
        String istr = "" + i;
        return new FailureMode(istr, List.of(istr));
    }

    public static List<FailureMode> getModes(int n) {
        List<FailureMode> modes = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            modes.add(getMode(i));
        }

        return modes;
    }
}
