package fr.lacaleche.glue.mcsx.view.debug;

import fr.lacaleche.glue.mcsx.view.FlexLayout;
import fr.lacaleche.glue.mcsx.view.IconView;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a live View tree into an inspectable snapshot: geometry, box model, container config and
 * per-widget detail, with every position resolved into the probed root's coordinates.
 *
 * <p>Deliberately free of any drawing so it can run under JUnit — the same facts the in-game
 * {@link InspectorView} shows can be asserted in a test, or dumped as text to compare two subtrees
 * that ought to lay out the same way but don't.
 */
public final class ViewProbe {

    private ViewProbe() {
    }

    /**
     * One view. {@code x}/{@code y} are relative to the probed root, not the parent, so two nodes
     * from different branches can be compared directly.
     *
     * @param measuredWidth the size the view asked for; differs from {@code width} exactly when the
     *     parent overrode it, which is the signature of a clipped or stretched child
     * @param detail        widget-specific facts: flex config, text and font, glyph codepoint
     */
    public record Node(String type, int x, int y, int width, int height,
                       int measuredWidth, int measuredHeight,
                       int[] padding, String layoutParams, String detail,
                       boolean visible, List<Node> children) {

        /** True when the parent handed this view a different size than it measured to. */
        public boolean overridden() {
            return width != measuredWidth || height != measuredHeight;
        }
    }

    public static Node probe(View root) {
        return probe(root, 0, 0);
    }

    private static Node probe(View view, int offsetX, int offsetY) {
        int x = offsetX + view.getLeft();
        int y = offsetY + view.getTop();
        List<Node> children = new ArrayList<>();
        if (view instanceof ViewGroup group) {
            // children of a scrolled container are drawn shifted by its scroll offset, so their
            // reported position has to follow — otherwise a scrolled subtree reports where it
            // would have been at scroll 0
            int childX = x - group.getScrollX();
            int childY = y - group.getScrollY();
            for (int i = 0; i < group.getChildCount(); i++) {
                children.add(probe(group.getChildAt(i), childX, childY));
            }
        }
        return new Node(view.getClass().getSimpleName(), x, y,
                view.getWidth(), view.getHeight(),
                view.getMeasuredWidth(), view.getMeasuredHeight(),
                paddingOf(view), describeLayoutParams(view), describeDetail(view),
                view.getVisibility() == View.VISIBLE, List.copyOf(children));
    }

    /**
     * {@code getPaddingLeft()} runs {@code resolvePadding()}, which needs a live {@code ModernUI}
     * for its RTL lookup and throws without one. A debug tool must never take down the UI it is
     * inspecting, so an unresolvable box reports as {@link #UNRESOLVED} instead of propagating.
     */
    private static int[] paddingOf(View view) {
        try {
            return new int[]{view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), view.getPaddingBottom()};
        } catch (RuntimeException e) {
            return new int[]{UNRESOLVED, UNRESOLVED, UNRESOLVED, UNRESOLVED};
        }
    }

    /** A box value that could not be read because layout direction was never resolved. */
    public static final int UNRESOLVED = -1;

    private static String describeLayoutParams(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            return "none";
        }
        StringBuilder text = new StringBuilder(params.getClass().getSimpleName())
                .append('(').append(spec(params.width)).append('×').append(spec(params.height)).append(')');
        if (params instanceof FlexLayout.LayoutParams flex) {
            if (flex.grow != 0f) {
                text.append(" grow=").append(flex.grow);
            }
            if (flex.shrink != 0f) {
                text.append(" shrink=").append(flex.shrink);
            }
            if (flex.alignSelf != null) {
                text.append(" self=").append(flex.alignSelf);
            }
        } else if (params instanceof ViewGroup.MarginLayoutParams margins) {
            // the resolved left/right, not the start/end that may still be awaiting resolution
            text.append(" margins=[").append(margins.leftMargin).append(',').append(margins.topMargin)
                    .append(',').append(margins.rightMargin).append(',').append(margins.bottomMargin)
                    .append(']');
        }
        return text.toString();
    }

    private static String spec(int value) {
        return switch (value) {
            case ViewGroup.LayoutParams.MATCH_PARENT -> "match";
            case ViewGroup.LayoutParams.WRAP_CONTENT -> "wrap";
            default -> String.valueOf(value);
        };
    }

    private static String describeDetail(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof FlexLayout flex) {
            text.append(flex.orientation()).append(" gap=").append(flex.gap())
                    .append(" items=").append(flex.alignItems())
                    .append(" justify=").append(flex.justify());
            if (flex.wrap()) {
                text.append(" wrap");
            }
        }
        if (view instanceof TextView label) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            CharSequence content = label.getText();
            text.append("text=\"").append(content)
                    .append("\" size=").append(label.getTextSize())
                    .append(" color=#").append(Long.toHexString(label.getCurrentTextColor() & 0xFFFFFFFFL));
            if (view instanceof IconView icon) {
                text.append(" glyph=").append(codePoints(icon.getText()));
            }
            label.getTypeface();
            text.append(" font=").append(label.getTypeface());
        }
        return text.toString();
    }

    private static String codePoints(CharSequence value) {
        if (value == null || value.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        value.codePoints().forEach(point -> text.append(text.isEmpty() ? "" : ",")
                .append("U+").append(Integer.toHexString(point).toUpperCase()));
        return text.toString();
    }

    /**
     * The deepest visible view under {@code x, y}, expressed in {@code root}'s own coordinates.
     * Searched last-child-first so the topmost sibling wins, as a real dispatch would resolve it.
     *
     * @return the hit view, or null when the point misses {@code root} entirely
     */
    public static View deepestAt(View root, int x, int y) {
        return deepestAt(root, x, y, 0, 0);
    }

    private static View deepestAt(View view, int x, int y, int offsetX, int offsetY) {
        if (view.getVisibility() != View.VISIBLE) {
            return null;
        }
        int left = offsetX + view.getLeft();
        int top = offsetY + view.getTop();
        if (x < left || y < top || x >= left + view.getWidth() || y >= top + view.getHeight()) {
            return null;
        }
        if (view instanceof ViewGroup group) {
            int childX = left - group.getScrollX();
            int childY = top - group.getScrollY();
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View hit = deepestAt(group.getChildAt(i), x, y, childX, childY);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return view;
    }

    /**
     * {@code x, y, w, h} of {@code view} in {@code root}'s coordinates — where it is actually drawn,
     * with every enclosing scroll offset applied.
     */
    public static int[] boundsIn(View root, View view) {
        int x = 0;
        int y = 0;
        View current = view;
        while (current != null && current != root) {
            x += current.getLeft();
            y += current.getTop();
            View parent = current.getParent() instanceof View above ? above : null;
            if (parent != null) {
                x -= parent.getScrollX();
                y -= parent.getScrollY();
            }
            current = parent;
        }
        return new int[]{x, y, view.getWidth(), view.getHeight()};
    }

    /** The snapshot as an indented tree, for logging or diffing two subtrees against each other. */
    public static String dump(Node node) {
        StringBuilder text = new StringBuilder();
        append(text, node, 0);
        return text.toString();
    }

    private static void append(StringBuilder text, Node node, int depth) {
        text.repeat("  ", depth).append(node.type())
                .append(" [").append(node.x()).append(',').append(node.y())
                .append(' ').append(node.width()).append('×').append(node.height()).append(']');
        if (node.overridden()) {
            text.append(" measured=").append(node.measuredWidth()).append('×').append(node.measuredHeight());
        }
        int[] padding = node.padding();
        if (padding[0] == UNRESOLVED) {
            text.append(" pad=unresolved");
        } else if (padding[0] != 0 || padding[1] != 0 || padding[2] != 0 || padding[3] != 0) {
            text.append(" pad=[").append(padding[0]).append(',').append(padding[1])
                    .append(',').append(padding[2]).append(',').append(padding[3]).append(']');
        }
        if (!node.visible()) {
            text.append(" HIDDEN");
        }
        text.append(' ').append(node.layoutParams());
        if (!node.detail().isEmpty()) {
            text.append("\n").append("  ".repeat(depth + 1)).append("· ").append(node.detail());
        }
        text.append('\n');
        for (Node child : node.children()) {
            append(text, child, depth + 1);
        }
    }
}
