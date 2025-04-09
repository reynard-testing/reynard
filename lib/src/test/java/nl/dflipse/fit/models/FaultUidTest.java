package nl.dflipse.fit.models;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import nl.dflipse.fit.faultload.FaultInjectionPoint;
import nl.dflipse.fit.faultload.FaultUid;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FaultUidTest {

    private static final FaultInjectionPoint point1 = new FaultInjectionPoint("x", "y", "sign", 0);
    private static final FaultInjectionPoint point2 = point1.asAnyCount();
    private static final FaultInjectionPoint point3 = point1.asAnyPayload();

    public static Collection<Object[]> equalUids() {
        return Arrays.asList(new Object[][] {
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point1)) },
                { new FaultUid(List.of(point1, point2)), new FaultUid(List.of(point1, point1)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point2)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point3)) },

        });
    }

    public static Collection<Object[]> inequalUids() {
        return Arrays.asList(new Object[][] {
                { new FaultUid(List.of(point1, point2)), new FaultUid(List.of(point1)) },

        });
    }

    @Test
    @ParameterizedTest
    @MethodSource("equalUids")
    public void testMatch(FaultUid f1, FaultUid f2) {
        assert f1.matches(f2);
    }

    @Test
    @ParameterizedTest
    @MethodSource("inequalUids")
    public void testNotMatch(FaultUid f1, FaultUid f2) {
        assert !f1.matches(f2);
    }
}