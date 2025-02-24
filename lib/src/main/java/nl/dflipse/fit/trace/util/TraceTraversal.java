package nl.dflipse.fit.trace.util;

import java.util.LinkedList;
import java.util.List;

import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.trace.tree.TraceTreeSpan;

public class TraceTraversal {

    public static void traverseTrace(TraceTreeSpan traceTreeRoot,
            java.util.function.Consumer<TraceTreeSpan> action) {
        if (traceTreeRoot == null) {
            return;
        }

        traverseDepthFirst(traceTreeRoot, action);
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

    public static boolean any(TraceTreeSpan traceTreeRoot, java.util.function.Predicate<TraceTreeSpan> predicate) {
        if (traceTreeRoot == null) {
            return false;
        }

        return containsDepthFirst(traceTreeRoot, predicate);
    }

    private static boolean containsDepthFirst(TraceTreeSpan spanNode,
            java.util.function.Predicate<TraceTreeSpan> predicate) {
        if (spanNode == null) {
            return false;
        }

        if (predicate.test(spanNode)) {
            return true;
        }

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                if (containsDepthFirst(child, predicate)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static TraceTreeSpan find(TraceTreeSpan traceTreeRoot,
            java.util.function.Predicate<TraceTreeSpan> predicate) {
        if (traceTreeRoot == null) {
            return null;
        }

        return findDepthFirst(traceTreeRoot, predicate);
    }

    private static TraceTreeSpan findDepthFirst(TraceTreeSpan spanNode,
            java.util.function.Predicate<TraceTreeSpan> predicate) {
        if (spanNode == null) {
            return null;
        }

        if (predicate.test(spanNode)) {
            return spanNode;
        }

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                var foundNode = findDepthFirst(child, predicate);
                if (foundNode != null) {
                    return foundNode;
                }
            }
        }

        return null;
    }

    public static List<FaultUid> depthFirstFaultpoints(TraceTreeSpan traceTreeRoot) {
        if (traceTreeRoot == null) {
            return new LinkedList<>();
        }

        return visitDepthFirst(traceTreeRoot);
    }

    private static List<FaultUid> visitDepthFirst(TraceTreeSpan spanNode) {
        List<FaultUid> faults = new LinkedList<>();

        if (spanNode == null) {
            return faults;
        }

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                faults.addAll(visitDepthFirst(child));
            }
        }

        if (spanNode.report != null) {
            faults.add(spanNode.report.faultUid);
        }

        return faults;
    }
}
