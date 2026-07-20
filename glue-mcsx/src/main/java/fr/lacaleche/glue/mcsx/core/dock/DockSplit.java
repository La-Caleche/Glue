package fr.lacaleche.glue.mcsx.core.dock;

import java.util.List;

/**
 * An interior node dividing its area among {@code children} along {@code dir}.
 *
 * @param sizes one fraction per child, summing to ~1; the geometry solver treats them as shares of
 *              the extent left after splitter gaps
 */
public record DockSplit(String id, Dir dir, List<DockNode> children, List<Double> sizes) implements DockNode {

    /** Split direction: {@code ROW} lays children left-to-right, {@code COL} top-to-bottom. */
    public enum Dir { ROW, COL }

    public DockSplit {
        children = List.copyOf(children);
        sizes = List.copyOf(sizes);
        if (children.size() != sizes.size()) {
            throw new IllegalArgumentException(
                    children.size() + " children but " + sizes.size() + " sizes");
        }
        if (children.size() < 2) {
            throw new IllegalArgumentException("a split needs at least 2 children");
        }
        double total = 0;
        for (Double size : sizes) {
            if (size == null || !Double.isFinite(size) || size < 0) {
                throw new IllegalArgumentException("split sizes must be finite and non-negative");
            }
            total += size;
        }
        if (!Double.isFinite(total) || total <= 0) {
            throw new IllegalArgumentException("split sizes must have a positive finite sum");
        }
    }

    public static DockSplit of(DockIds ids, Dir dir, List<DockNode> children, List<Double> sizes) {
        return new DockSplit(ids.next("split"), dir, children, sizes);
    }

    /** A split sharing its area evenly. */
    public static DockSplit even(DockIds ids, Dir dir, List<DockNode> children) {
        double share = 1d / children.size();
        return new DockSplit(ids.next("split"), dir, children,
                children.stream().map(c -> share).toList());
    }
}
