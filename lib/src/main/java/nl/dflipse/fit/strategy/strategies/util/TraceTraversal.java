package nl.dflipse.fit.strategy.strategies.util;

import java.util.LinkedList;
import java.util.List;

import nl.dflipse.fit.trace.data.TraceData;
import nl.dflipse.fit.trace.data.TraceTreeSpan;

public class TraceTraversal {

    public static void traverseTrace(TraceData trace,
            java.util.function.Consumer<TraceTreeSpan> action) {
        if (trace == null || trace.trees.isEmpty()) {
            return;
        }

        TraceTreeSpan root = trace.trees.get(0);
        traverseDepthFirst(root, action);
    }

    private static void traverseDepthFirst(TraceTreeSpan spanNode, java.util.function.Consumer<TraceTreeSpan> action) {
        if (spanNode == null) {
            return;
        }

        action.accept(spanNode);

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                traverseDepthFirst(child, action);
            }
        }
    }

    public static List<String> depthFirstFaultpoints(TraceData trace) {
        if (trace == null || trace.trees.isEmpty()) {
            return new LinkedList<>();
        }

        TraceTreeSpan root = trace.trees.get(0);
        return visitDepthFirst(root, null);
    }

    private static boolean isFaultPoint(TraceTreeSpan parent, TraceTreeSpan child) {
        boolean isRoot = parent == null;
        // injecting faults at the root is not useful
        if (isRoot) {
            return false;
        }

        // only inject faults at service boundaries
        boolean sameName = parent.span.name.equals(child.span.name);
        boolean differentService = !parent.span.serviceName.equals(child.span.serviceName);
        return sameName && differentService;
    }

    private static List<String> visitDepthFirst(TraceTreeSpan spanNode, TraceTreeSpan parent) {
        List<String> faults = new LinkedList<>();

        if (spanNode == null) {
            return faults;
        }

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                faults.addAll(visitDepthFirst(child, spanNode));
            }
        }

        boolean isFaultPoint = isFaultPoint(parent, spanNode);

        if (isFaultPoint && parent.report != null) {
            faults.add(parent.report.spanUid);
        }

        return faults;
    }
}
