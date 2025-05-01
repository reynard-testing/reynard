package io.github.delanoflipse.fit.generators;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.faultload.FaultUid;
import io.github.delanoflipse.fit.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.strategy.util.DynamicPowersetTree;
import io.github.delanoflipse.fit.testutil.FailureModes;
import io.github.delanoflipse.fit.testutil.FaultInjectionPoints;
import io.github.delanoflipse.fit.testutil.FaultsBuilder;

public class DynamicPowersetTreeTest {
    List<FailureMode> modes = FailureModes.getModes(3);
    List<FaultUid> points = FaultInjectionPoints.getPoints(12);
    FaultsBuilder faultsBuilder = new FaultsBuilder(points, modes);

    @Test
    public void testEqualityNodes() {
        var f1 = faultsBuilder.get(0, 0);
        var f2 = faultsBuilder.get(1, 0);

        DynamicPowersetTree.TreeNode node1 = new DynamicPowersetTree.TreeNode(List.of(f1, f2));
        DynamicPowersetTree.TreeNode node2 = new DynamicPowersetTree.TreeNode(List.of(f2, f1));
        assertTrue(node1.equals(node2));
    }

    @Test
    public void testEqualityExpansionNodes() {
        var f1 = faultsBuilder.get(0, 0);
        var f2 = faultsBuilder.get(1, 0);
        var p3 = points.get(3);
        var p4 = points.get(4);

        DynamicPowersetTree.TreeNode node1 = new DynamicPowersetTree.TreeNode(List.of(f1, f2));
        DynamicPowersetTree.TreeNode node2 = new DynamicPowersetTree.TreeNode(List.of(f2, f1));
        DynamicPowersetTree.ExpansionNode exp1 = new DynamicPowersetTree.ExpansionNode(node1, List.of(p3, p4));
        DynamicPowersetTree.ExpansionNode exp2 = new DynamicPowersetTree.ExpansionNode(node2, List.of(p4, p3));
        assertTrue(exp1.equals(exp2));
    }

    @Test
    public void testSetContains() {
        var f1 = faultsBuilder.get(0, 0);
        var f2 = faultsBuilder.get(1, 0);

        DynamicPowersetTree.TreeNode node1 = new DynamicPowersetTree.TreeNode(List.of(f1, f2));
        DynamicPowersetTree.TreeNode node2 = new DynamicPowersetTree.TreeNode(List.of(f2, f1));
        var nodeSet1 = Set.of(node1);
        assertTrue(nodeSet1.contains(node1));
        assertTrue(nodeSet1.contains(node2));
        var nodeSet2 = Set.of(node2);
        assertTrue(nodeSet2.contains(node1));
        assertTrue(nodeSet2.contains(node2));
    }

    @Test
    public void testExpSetContains() {
        var f1 = faultsBuilder.get(0, 0);
        var f2 = faultsBuilder.get(1, 0);
        var p3 = points.get(3);
        var p4 = points.get(4);

        DynamicPowersetTree.TreeNode node1 = new DynamicPowersetTree.TreeNode(List.of(f1, f2));
        DynamicPowersetTree.TreeNode node2 = new DynamicPowersetTree.TreeNode(List.of(f2, f1));
        DynamicPowersetTree.ExpansionNode exp1 = new DynamicPowersetTree.ExpansionNode(node1, List.of(p3, p4));
        DynamicPowersetTree.ExpansionNode exp2 = new DynamicPowersetTree.ExpansionNode(node2, List.of(p4, p3));

        var nodeSet1 = Set.of(exp1);
        assertTrue(nodeSet1.contains(exp1));
        assertTrue(nodeSet1.contains(exp2));
        var nodeSet2 = Set.of(exp2);
        assertTrue(nodeSet2.contains(exp1));
        assertTrue(nodeSet2.contains(exp2));
    }
}
