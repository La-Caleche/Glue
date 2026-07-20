package fr.lacaleche.glue.mcsx.core.layout;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;

import java.util.ArrayList;
import java.util.List;

/**
 * The main-axis solver for a single flex line — pure arithmetic over plain ints, so the subtle part
 * of flexbox is unit-testable without a window. Given each item's base size and its grow/shrink
 * factors, it resolves the final sizes (distributing or reclaiming the free space, clamped to each
 * item's bounds) and then the offsets along the line per {@code justify-content}.
 *
 * <p>Cross-axis sizing and alignment are deliberately not modelled here: they need no distribution
 * pass, so the view layer handles them directly. {@link #breakLines} splits items across lines for
 * {@code flex-wrap}; each resulting line is then solved independently by {@link #solve}.
 */
public final class FlexEngine {

    /** Guards the freeze loop; each pass freezes at least one item, so this can never bite. */
    private static final int MAX_PASSES = 8;

    private FlexEngine() {
    }

    /**
     * One item as the solver sees it.
     *
     * @param basis  the item's size before flexing (its {@code flex-basis}), already >= 0
     * @param grow   share of positive free space ({@code flex-grow}); 0 = never grows
     * @param shrink share of negative free space ({@code flex-shrink}); 0 = never shrinks
     * @param min    lower clamp, >= 0
     * @param max    upper clamp, >= {@code min}
     */
    public record Item(int basis, float grow, float shrink, int min, int max) {

        public Item {
            if (basis < 0) {
                throw new IllegalArgumentException("basis must be >= 0, got " + basis);
            }
            if (grow < 0 || shrink < 0) {
                throw new IllegalArgumentException("grow/shrink must be >= 0");
            }
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("require 0 <= min <= max, got " + min + ".." + max);
            }
        }

        /** A rigid item: it neither grows nor shrinks. */
        public static Item fixed(int basis) {
            return new Item(basis, 0f, 0f, 0, Integer.MAX_VALUE);
        }

        /** An item that flexes but is otherwise unbounded. */
        public static Item flexible(int basis, float grow, float shrink) {
            return new Item(basis, grow, shrink, 0, Integer.MAX_VALUE);
        }
    }

    /**
     * The solved line. {@code sizes[i]} and {@code offsets[i]} correspond to the input items;
     * {@code contentSize} is the natural extent of the line (the sum of the sizes plus the gaps),
     * which is what a wrap-content container should measure itself to.
     *
     * <p>The arrays are the caller's to read, not to share: this record's {@code equals} is identity
     * over them, as records always are with array components.
     */
    public record Line(int[] sizes, int[] offsets, int contentSize) {
    }

    /**
     * Solves one flex line.
     *
     * @param available the main-axis space the container offers; ignored when {@code !bounded}
     * @param bounded   false when the container is sizing to its content, so there is no free space
     *                  to distribute and every item keeps its basis (CSS shrink-to-fit)
     * @param gap       fixed spacing inserted between adjacent items
     */
    public static Line solve(List<Item> items, int available, boolean bounded, int gap, Justify justify) {
        return solve(items.toArray(new Item[0]), available, bounded, gap, justify);
    }

    /**
     * Solves the line made of {@code items[indices[0..n]]} — the wrap path's per-line entry point,
     * which avoids materialising a sublist for every line on every measure pass.
     */
    public static Line solve(List<Item> items, int[] indices, int available, boolean bounded,
                             int gap, Justify justify) {
        Item[] line = new Item[indices.length];
        for (int i = 0; i < indices.length; i++) {
            line[i] = items.get(indices[i]);
        }
        return solve(line, available, bounded, gap, justify);
    }

    private static Line solve(Item[] items, int available, boolean bounded, int gap, Justify justify) {
        int count = items.length;
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) {
            Item item = items[i];
            sizes[i] = clamp(item.basis(), item.min(), item.max());
        }
        int totalGap = count > 1 ? gap * (count - 1) : 0;

        if (bounded) {
            int free = available - (sum(sizes) + totalGap);
            if (free != 0) {
                flex(sizes, items, free);
            }
        }

        int contentSize = sum(sizes) + totalGap;
        int free = bounded ? Math.max(0, available - contentSize) : 0;
        return new Line(sizes, offsets(sizes, count, gap, free, justify), contentSize);
    }

    /**
     * Splits {@code items} into the index groups that each flex line holds, greedily: an item starts a
     * new line when it no longer fits beside the ones already on the current one. An item wider than
     * the whole container still gets a line of its own rather than an empty one before it.
     *
     * <p>Breaking uses each item's clamped basis, before any grow or shrink — CSS decides where the
     * lines fall first, and only then flexes each line to fill its container.
     *
     * @param available the main-axis space per line; a non-positive value puts everything on one line,
     *                  since a container that is sizing to its content has no width to break against
     */
    public static List<int[]> breakLines(List<Item> items, int available, int gap) {
        int count = items.size();
        if (available <= 0 || count == 0) {
            return List.of(allIndices(count));
        }
        List<int[]> lines = new ArrayList<>();
        int lineStart = 0;
        int used = 0;
        for (int i = 0; i < count; i++) {
            Item item = items.get(i);
            int hypothetical = clamp(item.basis(), item.min(), item.max());
            if (i > lineStart && used + gap + hypothetical > available) {
                lines.add(rangeIndices(lineStart, i));
                lineStart = i;
                used = hypothetical;
            } else {
                used = i == lineStart ? hypothetical : used + gap + hypothetical;
            }
        }
        lines.add(rangeIndices(lineStart, count));
        return lines;
    }

    /** The indices {@code 0..count-1} as one array — a single line holding every item. */
    public static int[] allIndices(int count) {
        return rangeIndices(0, count);
    }

    private static int[] rangeIndices(int start, int end) {
        int[] indices = new int[end - start];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = start + i;
        }
        return indices;
    }

    /**
     * Distributes {@code free} across the items in proportion to their grow (when positive) or their
     * scaled shrink (when negative), re-running whenever an item hits a bound. Freezing the clamped
     * item and redistributing the remainder is what keeps a min-width from silently eating another
     * item's share — the single place a naive one-pass implementation goes wrong.
     */
    private static void flex(int[] sizes, Item[] items, int free) {
        int count = sizes.length;
        boolean growing = free > 0;
        boolean[] frozen = new boolean[count];
        for (int i = 0; i < count; i++) {
            frozen[i] = factorOf(items[i], growing) <= 0f;
        }

        int remaining = free;
        for (int pass = 0; pass < MAX_PASSES && remaining != 0; pass++) {
            double totalFactor = 0d;
            for (int i = 0; i < count; i++) {
                if (!frozen[i]) {
                    totalFactor += factorOf(items[i], growing);
                }
            }
            if (totalFactor <= 0d) {
                return;
            }

            boolean clampedAny = false;
            int applied = 0;
            for (int i = 0; i < count; i++) {
                if (frozen[i]) {
                    continue;
                }
                Item item = items[i];
                int delta = (int) Math.round(remaining * (factorOf(item, growing) / totalFactor));
                int target = sizes[i] + delta;
                int clamped = clamp(target, item.min(), item.max());
                if (clamped != target) {
                    frozen[i] = true;
                    clampedAny = true;
                }
                applied += clamped - sizes[i];
                sizes[i] = clamped;
            }
            remaining -= applied;
            if (!clampedAny) {
                break;
            }
        }

        // Integer rounding can leave a pixel or two unassigned; hand it to the first item that can
        // still absorb it, so the line exactly fills its container rather than drifting.
        if (remaining != 0) {
            for (int i = 0; i < count; i++) {
                Item item = items[i];
                if (factorOf(item, growing) <= 0f) {
                    continue;
                }
                int clamped = clamp(sizes[i] + remaining, item.min(), item.max());
                remaining -= clamped - sizes[i];
                sizes[i] = clamped;
                if (remaining == 0) {
                    return;
                }
            }
        }
    }

    /**
     * Shrinking weights the factor by the basis, per CSS: a wide item gives up more of its width
     * than a narrow one at the same {@code flex-shrink}, so they run out of room together.
     */
    private static float factorOf(Item item, boolean growing) {
        return growing ? item.grow() : item.shrink() * item.basis();
    }

    private static int[] offsets(int[] sizes, int count, int gap, int free, Justify justify) {
        int leading;
        int between;
        switch (justify) {
            case CENTER -> {
                leading = free / 2;
                between = gap;
            }
            case END -> {
                leading = free;
                between = gap;
            }
            case BETWEEN -> {
                leading = 0;
                between = gap + (count > 1 ? free / (count - 1) : 0);
            }
            case AROUND -> {
                int unit = count > 0 ? free / count : 0;
                leading = unit / 2;
                between = gap + unit;
            }
            case EVENLY -> {
                int unit = free / (count + 1);
                leading = unit;
                between = gap + unit;
            }
            default -> {
                leading = 0;
                between = gap;
            }
        }

        int[] offsets = new int[count];
        int cursor = leading;
        for (int i = 0; i < count; i++) {
            offsets[i] = cursor;
            cursor += sizes[i] + between;
        }
        return offsets;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
