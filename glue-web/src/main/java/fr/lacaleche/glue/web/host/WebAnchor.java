package fr.lacaleche.glue.web.host;

/**
 * Where a sized HUD or overlay sits in the GUI. Offsets move edge-anchored layers inward and
 * centered layers by the given amount.
 */
public enum WebAnchor {
    TOP_LEFT(Edge.START, Edge.START),
    TOP(Edge.CENTER, Edge.START),
    TOP_RIGHT(Edge.END, Edge.START),
    LEFT(Edge.START, Edge.CENTER),
    CENTER(Edge.CENTER, Edge.CENTER),
    RIGHT(Edge.END, Edge.CENTER),
    BOTTOM_LEFT(Edge.START, Edge.END),
    BOTTOM(Edge.CENTER, Edge.END),
    BOTTOM_RIGHT(Edge.END, Edge.END);

    private final Edge horizontal;
    private final Edge vertical;

    WebAnchor(Edge horizontal, Edge vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    int x(int available, int size, int offset) {
        return this.horizontal.place(available, size, offset);
    }

    int y(int available, int size, int offset) {
        return this.vertical.place(available, size, offset);
    }

    private enum Edge {
        START,
        CENTER,
        END;

        int place(int available, int size, int offset) {
            return switch (this) {
                case START -> offset;
                case CENTER -> (available - size) / 2 + offset;
                case END -> available - size - offset;
            };
        }
    }
}
