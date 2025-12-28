package dev.reynard.junit.unit.models;

import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.reynard.junit.faultload.FaultInjectionPoint;
import dev.reynard.junit.faultload.FaultUid;

public class FaultUidTest {
    private static final FaultInjectionPoint pointA = new FaultInjectionPoint("A", "a1", "", Map.of(), 0);

    private static final FaultInjectionPoint pointB = new FaultInjectionPoint("B", "b1", "", Map.of(), 0);

    private static final FaultInjectionPoint pointB2 = pointB.withCount(1);

    private static final FaultInjectionPoint pointBalt1 = pointB.withSignature("b2");

    private static final FaultInjectionPoint pointBalt2 = pointB.withPayload("x");

    private static final FaultInjectionPoint pointC = new FaultInjectionPoint("C", "c1", "x",
            Map.of(), 0);

    // call [B, C] (in order)
    private static final FaultInjectionPoint pointBC = pointC.withPredecessors(
            Map.of(pointB.asPartial().toString(), pointB.count()));

    // call [B, C, B] (in order)
    private static final FaultInjectionPoint pointBCB = pointB
            .withPredecessors(Map.of(pointBC.asPartial().toString(), pointBC.count()));

    private static final FaultUid uidA = new FaultUid(List.of(pointA));
    private static final FaultUid uidA_B = new FaultUid(List.of(pointA, pointB));
    private static final FaultUid uidA_B2 = new FaultUid(List.of(pointA, pointB2));
    private static final FaultUid uidA_Ba1 = new FaultUid(List.of(pointA, pointBalt1));
    private static final FaultUid uidA_Ba2 = new FaultUid(List.of(pointA, pointBalt2));
    private static final FaultUid uidB_C = new FaultUid(List.of(pointB, pointC));
    private static final FaultUid uidA_C = new FaultUid(List.of(pointA, pointC));
    private static final FaultUid uidA_BC = new FaultUid(List.of(pointA, pointBC));
    private static final FaultUid uidA_BCB = new FaultUid(List.of(pointA, pointBCB));

    public static Collection<Object[]> uids() {
        return Arrays.asList(new Object[][] {
                { uidA },
                { uidA_B },
                { uidA_B2 },
                { uidA_Ba1 },
                { uidA_Ba2 },
                { uidB_C },
                { uidA_C },
                { uidA_BC },
                { uidA_BCB },
        });
    }

    public static Collection<Object[]> equalUids() {
        return Arrays.asList(new Object[][] {
                { uidA_BC, uidA_BC.asAnyDestination() },
                { uidA_BC, uidA_BC.asAnySignature() },
                { uidA_BC, uidA_BC.asAnyPayload() },
                { uidA_BC, uidA_BC.asAnyPredecessors() },
                { uidA_BC, uidA_BC.asAnyCount() },
                { uidA_BC, uidA_BC.asAnyOrigin() },
                { uidA_C, uidB_C.asAnyOrigin() },
                { uidA_B, new FaultUid(List.of(FaultInjectionPoint.Any(), pointB)) },
                { uidA_B, new FaultUid(List.of(pointA, FaultInjectionPoint.Any())) },
        });
    }

    public static Collection<Object[]> inequalUids() {
        return Arrays.asList(new Object[][] {
                { uidA, uidA_B.asAnyCount() },
                { uidA, uidA_B.asAnyCount() },
        });
    }

    public static Collection<Object[]> validUids() {
        return Arrays.asList(new Object[][] {
                { Arrays.asList(null, pointB) },
        });
    }

    public static Collection<Object[]> invalidUids() {
        return Arrays.asList(new Object[][] {
                { Arrays.asList(pointA, null) },
                { Arrays.asList(pointA, null, pointA) },
                { Arrays.asList(null, null) },
                { Arrays.asList(null, null, null) },

        });
    }

    @ParameterizedTest
    @MethodSource("uids")
    public void testIdentity(FaultUid f1) {
        assert f1.matches(f1);
    }

    @ParameterizedTest
    @MethodSource("uids")
    public void testIdentityInverse(FaultUid f1) {
        for (var args : uids()) {
            FaultUid f2 = (FaultUid) args[0];
            if (f1 == f2) {
                continue; // Skip if they are the same instance
            }

            // All other uids are different
            assertFalse(f1.matches(f2));
        }
    }

    @ParameterizedTest
    @MethodSource("uids")
    public void testNormalForm(FaultUid f) {
        assert f.isNormalForm();
        assert f.asAnyPredecessors().isNormalForm();
        assert f.asAnyPayload().isNormalForm();
    }

    @Test
    public void testNotNormalForm2() {
        FaultUid anyOrigin = new FaultUid(List.of(FaultInjectionPoint.Any(), pointB));
        assert !anyOrigin.isNormalForm();
    }

    @ParameterizedTest
    @MethodSource("uids")
    public void testNotNormalForm(FaultUid f) {
        assert !f.asAnyDestination().isNormalForm();
        assert !f.asAnySignature().isNormalForm();
        assert !f.asAnyCount().isNormalForm();
        assert !f.asAnyOrigin().isNormalForm();
    }

    @ParameterizedTest
    @MethodSource("equalUids")
    public void testMatch(FaultUid f1, FaultUid f2) {
        assert f1.matches(f2);
    }

    @ParameterizedTest
    @MethodSource("inequalUids")
    public void testNotMatch(FaultUid f1, FaultUid f2) {
        assert !f1.matches(f2);
    }

    @ParameterizedTest
    @MethodSource("validUids")
    public void testValid(List<FaultInjectionPoint> f1) {
        new FaultUid(f1); // Should not throw an exception
    }

    @ParameterizedTest
    @MethodSource("invalidUids")
    public void testInvalid(List<FaultInjectionPoint> f1) {
        try {
            new FaultUid(f1);
            assert false : "Expected IllegalArgumentException for invalid FaultUid";
        } catch (IllegalArgumentException e) {
            // Expected exception
        }
    }
}
