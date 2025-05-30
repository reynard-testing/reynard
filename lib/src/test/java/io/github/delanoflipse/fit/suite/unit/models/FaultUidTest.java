package io.github.delanoflipse.fit.suite.unit.models;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.delanoflipse.fit.suite.faultload.FaultInjectionPoint;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FaultUidTest {

    private static final FaultInjectionPoint point1 = new FaultInjectionPoint("x", "y", "sign", Map.of("x", 1), 0);

    private static final FaultInjectionPoint point1_cs = new FaultInjectionPoint("x", "y", "sign", Map.of("x", 2), 0);
    private static final FaultInjectionPoint point1_csn = new FaultInjectionPoint("x", "y", "sign", Map.of(), 0);
    private static final FaultInjectionPoint point2 = point1.asAnyCount();
    private static final FaultInjectionPoint point3 = point1.asAnyPayload();
    private static final FaultInjectionPoint point4 = new FaultInjectionPoint("z", "z", "x", Map.of(
            point1.asPartial().toString(), point1.count()),
            0);
    private static final FaultInjectionPoint point5 = point1.asAnyCallStack();

    public static Collection<Object[]> equalUids() {
        return Arrays.asList(new Object[][] {
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point1)) },
                { new FaultUid(List.of(point1, point2)), new FaultUid(List.of(point1, point1)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point2)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point3)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point5)) },

        });
    }

    public static Collection<Object[]> inequalUids() {
        return Arrays.asList(new Object[][] {
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point1_cs)) },
                { new FaultUid(List.of(point1)), new FaultUid(List.of(point1_csn)) },
                { new FaultUid(List.of(point1, point2)), new FaultUid(List.of(point1)) },
                { new FaultUid(List.of(point1, point4)), new FaultUid(List.of(point1)) },

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