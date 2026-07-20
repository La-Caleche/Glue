package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockGeometry;
import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.dock.DockNode;
import fr.lacaleche.glue.mcsx.core.dock.DockRect;
import fr.lacaleche.glue.mcsx.core.dock.DockSplit;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.ViewGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders one dock node tree — leaf cards interleaved with splitters — from a single
 * {@link DockGeometry} pass per measure. It appears twice in the workspace: as the docked stage
 * and as the content of every floating window. Structural changes rebuild the child list (pane
 * content reparents through the host); size-only changes just re-solve on the next measure.
 */
final class DockTreeView extends ViewGroup {

    static final int SPLITTER_PX = 8;

    private final DockHostView host;
    private final String floatId;
    private DockNode node;
    private DockGeometry.Solved solved = DockGeometry.solve(null, new DockRect(0, 0, 0, 0), SPLITTER_PX);
    private final Map<String, LeafPaneView> leafViews = new LinkedHashMap<>();
    private final List<SplitterView> splitterViews = new ArrayList<>();

    DockTreeView(Context context, DockHostView host, String floatId) {
        super(context);
        this.host = host;
        this.floatId = floatId;
    }

    void setNode(DockNode newNode, boolean structural) {
        // DockOps shares structure, so an unchanged subtree comes back identical: a float move or
        // resize leaves the docked tree untouched, and re-laying it out on every pointer move would
        // force the whole workspace — pane content included — through measure again for nothing.
        if (!structural && newNode == node) {
            return;
        }
        node = newNode;
        // one walk feeds both the updatability check and the refresh/rebuild — this runs on
        // every pointer move of a splitter or float drag
        List<DockLeaf> leaves = new ArrayList<>();
        collectLeaves(newNode, leaves);
        if (structural || !updatable(leaves)) {
            rebuild(leaves);
        } else {
            for (DockLeaf leaf : leaves) {
                leafViews.get(leaf.id()).update(leaf);
            }
        }
        requestLayout();
        invalidate();
    }

    /** The rects of this tree's leaves, in this view's coordinates, from the last measure. */
    Map<String, DockRect> leafRects() {
        return solved.leaves();
    }

    /** The rects of this tree's splits, for turning a splitter drag's pixels into a share delta. */
    Map<String, DockRect> splitRects() {
        return solved.splits();
    }

    /** A non-structural update is only safe when every leaf id already has a view. */
    private boolean updatable(List<DockLeaf> leaves) {
        if (leaves.size() != leafViews.size()) {
            return false;
        }
        for (DockLeaf leaf : leaves) {
            if (!leafViews.containsKey(leaf.id())) {
                return false;
            }
        }
        return true;
    }

    private void rebuild(List<DockLeaf> leaves) {
        removeAllViews();
        leafViews.clear();
        splitterViews.clear();
        for (DockLeaf leaf : leaves) {
            LeafPaneView view = new LeafPaneView(getContext(), host, leaf, floatId, floatId == null);
            leafViews.put(leaf.id(), view);
            addView(view);
        }
        int gaps = countGaps(node);
        for (int i = 0; i < gaps; i++) {
            SplitterView view = new SplitterView(getContext(), host);
            splitterViews.add(view);
            addView(view);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        solved = DockGeometry.solve(node, new DockRect(0, 0, width, height), SPLITTER_PX);
        solved.leaves().forEach((id, rect) -> {
            LeafPaneView view = leafViews.get(id);
            if (view != null) {
                view.measure(MeasureSpec.makeMeasureSpec(rect.w(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rect.h(), MeasureSpec.EXACTLY));
            }
        });
        List<DockGeometry.Splitter> gaps = solved.splitters();
        for (int i = 0; i < splitterViews.size(); i++) {
            SplitterView view = splitterViews.get(i);
            if (i < gaps.size()) {
                DockGeometry.Splitter gap = gaps.get(i);
                view.setSplitter(gap);
                view.measure(MeasureSpec.makeMeasureSpec(gap.rect().w(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(gap.rect().h(), MeasureSpec.EXACTLY));
            } else {
                view.setSplitter(null);
                view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        solved.leaves().forEach((id, rect) -> {
            LeafPaneView view = leafViews.get(id);
            if (view != null) {
                view.layout(rect.x(), rect.y(), rect.right(), rect.bottom());
            }
        });
        List<DockGeometry.Splitter> gaps = solved.splitters();
        for (int i = 0; i < splitterViews.size(); i++) {
            if (i < gaps.size()) {
                DockRect rect = gaps.get(i).rect();
                splitterViews.get(i).layout(rect.x(), rect.y(), rect.right(), rect.bottom());
            } else {
                splitterViews.get(i).layout(0, 0, 0, 0);
            }
        }
    }

    private static void collectLeaves(DockNode tree, List<DockLeaf> into) {
        if (tree instanceof DockLeaf leaf) {
            into.add(leaf);
        } else if (tree instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                collectLeaves(child, into);
            }
        }
    }

    private static int countGaps(DockNode tree) {
        if (!(tree instanceof DockSplit split)) {
            return 0;
        }
        int gaps = split.children().size() - 1;
        for (DockNode child : split.children()) {
            gaps += countGaps(child);
        }
        return gaps;
    }
}
