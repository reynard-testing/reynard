package dev.reynard.junit.strategy.util.traversal;

public enum TraversalOrder {
    DEPTH_FIRST_PRE_ORDER(NodeOrder.PRE_ORDER),
    DEPTH_FIRST_REVERSE_PRE_ORDER(NodeOrder.REVERSE_PRE_ORDER),
    DEPTH_FIRST_POST_ORDER(NodeOrder.POST_ORDER),
    DEPTH_FIRST_REVERSE_POST_ORDER(NodeOrder.REVERSE_POST_ORDER),

    BREADTH_FIRST(null),
    BREADTH_FIRST_REVERSE(null),
    RANDOM(null);

    private final NodeOrder nodeOrder;

    TraversalOrder(NodeOrder nodeOrder) {
        this.nodeOrder = nodeOrder;
    }

    public NodeOrder getNodeOrder() {
        return nodeOrder;
    }
}
