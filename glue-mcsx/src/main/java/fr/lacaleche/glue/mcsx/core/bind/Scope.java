package fr.lacaleche.glue.mcsx.core.bind;

/**
 * One name → value binding in the build-time scope chain: component props, {@code <state>}
 * signals, {@code select=} selection flags and {@code <for>} loop items all live here, innermost
 * first. Loop items are tagged explicitly ({@link #item}) because they are the one kind with
 * special semantics — a one-arg handler receives the <em>nearest loop item</em>, never whatever
 * scope happens to be innermost (a {@code select=}/{@code <state>} scope pushed later must not
 * shadow the row).
 */
public record Scope(String name, Object value, Scope parent, boolean loopItem) {

    public static Scope of(String name, Object value, Scope parent) {
        return new Scope(name, value, parent, false);
    }

    /** A {@code <for as="…">} row binding — the scope kind {@link #nearestLoopItem()} finds. */
    public static Scope item(String name, Object value, Scope parent) {
        return new Scope(name, value, parent, true);
    }

    /** The innermost enclosing loop-item scope, or null when not inside a {@code <for>}. */
    public Scope nearestLoopItem() {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.loopItem) {
                return s;
            }
        }
        return null;
    }
}
