package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockFloat;
import fr.lacaleche.glue.mcsx.core.dock.DockGeometry;
import fr.lacaleche.glue.mcsx.core.dock.DockIds;
import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.dock.DockNode;
import fr.lacaleche.glue.mcsx.core.dock.DockOps;
import fr.lacaleche.glue.mcsx.core.dock.DockRect;
import fr.lacaleche.glue.mcsx.core.dock.DockSplit;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import fr.lacaleche.glue.mcsx.view.FontRegistry;
import fr.lacaleche.glue.mcsx.view.IconView;
import fr.lacaleche.glue.mcsx.view.OverlayHost;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The dockspace workspace, filling the window: the docked {@link DockTreeView}, the floating
 * windows, the drag ghost and the {@link DropOverlayView}, in that z order.
 *
 * <p>All cross-pane gestures — tab drags, window moves/resizes, splitter drags — live here rather
 * than in the views that start them: a child arms a drag on its own DOWN, the host intercepts the
 * pointer once it moves (4px threshold for tabs, immediately otherwise) and runs the whole gesture
 * against {@link DockGeometry} rects, so a drag never depends on event dispatch through views that
 * the drag itself is about to rearrange. Every mutation goes through {@link DockOps} and swaps in
 * a whole new immutable layout.
 */
public final class DockHostView extends ViewGroup {

    private static final int GUTTER = 10;
    private static final float TAB_DRAG_THRESHOLD = 4f;

    /** The smallest visible strip a clamped float keeps on the stage's x axis. */
    private static final int FLOAT_MIN_VISIBLE_X = 40;

    /**
     * One armed gesture. Each kind carries exactly its own drag-start snapshot, constructed
     * atomically when the child arms it — a switch over this can't read another gesture's
     * leftover state, which loose per-kind fields made possible.
     */
    private sealed interface Drag {
        record Tab(String paneId, String fromLeafId, String floatId) implements Drag {
        }

        record FloatMove(String floatId, int startX, int startY) implements Drag {
        }

        record FloatResize(String floatId, int edges,
                           int startX, int startY, int startW, int startH) implements Drag {
        }

        record Split(String splitId, int index, boolean row,
                     double startShare, int extent) implements Drag {
        }
    }

    private final Map<String, DockPane> panes;
    private final DockIds ids;
    private final Consumer<DockLayout> onMutated;
    private final OverlayHost overlays;
    private final Map<String, View> contentViews = new HashMap<>();

    private final DockTreeView treeView;
    private final Map<String, FloatWindowView> floatWindows = new LinkedHashMap<>();
    private final FlexLayout ghost;
    private final IconView ghostIcon;
    private final TextView ghostTitle;
    private final DropOverlayView dropOverlay;

    /** The layout the user arranged, in the coordinates they arranged it in; the only one persisted. */
    private DockLayout layout;
    /**
     * {@link #layout} with every float pulled back onto the current stage — what the view renders
     * and what a drag starts from. Keeping the clamp out of the model is what lets a workspace
     * arranged on a large monitor present inside a small window without its stored frames being
     * rewritten to that window's boundary, which is what gets saved.
     *
     * <p>The invariant: {@link #layout} is assigned only in {@link #apply}, {@link #restage} runs in
     * {@link #apply} and {@link #onSizeChanged}, and nothing else writes either field.
     */
    private DockLayout staged;

    /** Toggles that arrived before the workspace had a stage to place a window on. */
    private final List<String> pendingToggles = new ArrayList<>();
    private boolean flushScheduled;

    private float lastDownX;
    private float lastDownY;
    /** The armed gesture, or null; {@link #dragActive} tells armed from crossed-threshold. */
    private Drag drag;
    private boolean dragActive;
    /** Drop preview, shared by tab drags and float moves. */
    private DropTarget drop;
    private DockRect dropRect;

    public DockHostView(Context context, List<DockPane> paneList, DockLayout initial,
                        DockIds ids, Consumer<DockLayout> onMutated, OverlayHost overlays) {
        super(context);
        this.ids = ids;
        this.onMutated = onMutated;
        this.overlays = overlays;
        this.panes = new LinkedHashMap<>();
        for (DockPane pane : paneList) {
            panes.put(pane.id(), pane);
        }
        ColorDrawable backdrop = new ColorDrawable();
        setBackground(backdrop);

        treeView = new DockTreeView(context, this, null);
        dropOverlay = new DropOverlayView(context);

        ghost = new FlexLayout(context);
        ghost.setOrientation(Orientation.ROW);
        ghost.setAlignItems(Align.CENTER);
        ghost.setGap(7);
        ghost.setPadding(11, 6, 11, 6);
        ShapeDrawable chip = new ShapeDrawable();
        chip.setShape(ShapeDrawable.RECTANGLE);
        chip.setCornerRadius(9);
        ghost.setBackground(chip);
        ghostIcon = new IconView(context, 13);
        ghost.addView(ghostIcon, new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        ghostTitle = new TextView(context);
        ghostTitle.setTextSize(12f);
        ghost.addView(ghostTitle, new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        ghost.setVisibility(GONE);
        Themed.onTheme(this, theme -> {
            backdrop.setColor(theme.color(Tokens.SURFACE_BASE));
            chip.setColor(theme.color(Tokens.SURFACE_2));
            chip.setStroke(1, theme.color(Tokens.BORDER_STRONG));
            ghostTitle.setTextColor(theme.color(Tokens.TEXT_PRIMARY));
            ghostIcon.setColor(theme.color(Tokens.ACCENT));
        });

        this.layout = initial;
        this.staged = initial;
        rebuild();
    }

    public DockLayout layout() {
        return layout;
    }

    DockPane pane(String paneId) {
        return panes.get(paneId);
    }

    /** The pane's content view, created on first use and reparented ever after. */
    View contentView(String paneId) {
        return contentViews.computeIfAbsent(paneId, id -> {
            DockPane pane = panes.get(id);
            if (pane == null) {
                TextView missing = new TextView(getContext());
                missing.setText("missing pane: " + id);
                missing.setTextSize(12f);
                missing.setGravity(Gravity.CENTER);
                Themed.onTheme(missing,
                        theme -> missing.setTextColor(theme.color(Tokens.STATUS_DANGER)));
                return missing;
            }
            return pane.content().create(getContext(), overlays);
        });
    }

    /** Swaps in a new layout; {@code structural} when the leaf/tab/window sets changed. */
    public void apply(DockLayout next, boolean structural) {
        layout = next;
        restage();
        if (structural) {
            rebuild();
        } else {
            treeView.setNode(next.tree(), false);
            for (DockFloat f : next.floats()) {
                FloatWindowView window = floatWindows.get(f.id());
                if (window != null) {
                    window.setData(f, false);
                }
            }
        }
        // only a structural change can open or close a pane; moving, resizing, raising and
        // re-splitting all leave the tab set alone, and openSet walks every tree to build a set
        if (structural && !DockOps.openSet(next).contains(DockPane.VIEWPORT_ID)) {
            ViewportEmbedding.clearPaneBounds();
        }
        requestLayout();
        invalidate();
    }

    /** Re-derives {@link #staged} from {@link #layout} against the current stage. */
    private void restage() {
        // with no stage to clamp against, clamping would pile every float at (0,0): present as stored
        staged = getWidth() > 0 && getHeight() > 0
                ? DockOps.clampFloats(layout, getWidth(), getHeight(),
                        FLOAT_MIN_VISIBLE_X, FloatWindowView.HEADER_H)
                : layout;
    }

    /**
     * The panels-menu toggle. A toggle can arrive before the first layout pass — the open-time
     * {@code onOpenPanesChanged} runs on the UI looper, which drains it ahead of the overlay's
     * first traversal — and a window cascaded from a 0x0 stage lands on top of every other one at
     * the origin. Such toggles queue and replay against the real stage.
     */
    public void toggleFloatPane(String paneId) {
        // queue while a flush is pending too, so a later toggle can never overtake an earlier one
        if (getWidth() <= 0 || getHeight() <= 0 || !pendingToggles.isEmpty()) {
            pendingToggles.add(paneId);
            scheduleFlush();
            return;
        }
        apply(DockOps.toggleFloat(layout, ids, paneId, getWidth(), getHeight()), true);
        onMutated.accept(layout);
    }

    /** Arms the replay; a no-op until there is a stage, when {@link #onSizeChanged} re-arms it. */
    private void scheduleFlush() {
        if (flushScheduled || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        flushScheduled = true;
        post(this::flushToggles);
    }

    /** Replays the queued toggles against the real stage; posted, so never inside a layout pass. */
    private void flushToggles() {
        flushScheduled = false;
        if (pendingToggles.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        List<String> queued = List.copyOf(pendingToggles);
        pendingToggles.clear();
        DockLayout next = layout;
        for (String paneId : queued) {
            // folded rather than applied one by one: each step sees the previous step's float list,
            // so the cascade advances and an open/close pair of the same pane still cancels out
            next = DockOps.toggleFloat(next, ids, paneId, getWidth(), getHeight());
        }
        apply(next, true);
        onMutated.accept(layout);
    }

    void closeTab(String paneId) {
        apply(DockOps.detach(layout, paneId), true);
        onMutated.accept(layout);
    }

    void closeFloat(String floatId) {
        apply(DockOps.removeFloat(layout, floatId), true);
        onMutated.accept(layout);
    }

    void tabPressed(DockLeaf leaf, String paneId, String floatId) {
        apply(DockOps.setActive(layout, leaf.id(), paneId), false);
        if (floatId != null) {
            floatTouched(floatId);
        }
        drag = new Drag.Tab(paneId, leaf.id(), floatId);
        dragActive = false;
    }

    void splitterPressed(DockGeometry.Splitter splitter) {
        DockNode node = DockOps.find(layout, splitter.splitId());
        DockRect rect = findSplitRect(splitter.splitId());
        if (!(node instanceof DockSplit split) || rect == null) {
            return;
        }
        boolean row = splitter.dir() == DockSplit.Dir.ROW;
        int gaps = (split.children().size() - 1) * DockTreeView.SPLITTER_PX;
        drag = new Drag.Split(splitter.splitId(), splitter.index(), row,
                split.sizes().get(splitter.index()),
                Math.max(1, (row ? rect.w() : rect.h()) - gaps));
        dragActive = false;
    }

    void floatHeaderPressed(String floatId) {
        // the drag starts from the frame the user can see, not the one on file
        DockFloat window = DockOps.findFloat(staged, floatId);
        if (window == null) {
            return;
        }
        floatTouched(floatId);
        drag = new Drag.FloatMove(floatId, window.x(), window.y());
        dragActive = false;
    }

    void floatResizePressed(String floatId, int edges) {
        // the drag starts from the frame the user can see, not the one on file
        DockFloat window = DockOps.findFloat(staged, floatId);
        if (window == null) {
            return;
        }
        floatTouched(floatId);
        drag = new Drag.FloatResize(floatId, edges,
                window.x(), window.y(), window.w(), window.h());
        dragActive = false;
    }

    /** Any press on a floating window raises it; deferred so children never move mid-dispatch. */
    void floatTouched(String floatId) {
        DockFloat window = DockOps.findFloat(layout, floatId);
        if (window == null) {
            return;
        }
        post(() -> {
            apply(DockOps.bringToFront(layout, floatId), false);
            FloatWindowView view = floatWindows.get(floatId);
            if (view != null) {
                bringChildToFront(view);
                bringChildToFront(ghost);
                bringChildToFront(dropOverlay);
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                lastDownX = event.getX();
                lastDownY = event.getY();
                drag = null;
                dragActive = false;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (drag == null || dragActive) {
                    return dragActive;
                }
                float dist = Math.max(Math.abs(event.getX() - lastDownX),
                        Math.abs(event.getY() - lastDownY));
                if (!(drag instanceof Drag.Tab) || dist > TAB_DRAG_THRESHOLD) {
                    beginDrag();
                    return true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragActive) {
                    drag = null;
                }
            }
            default -> {
            }
        }
        return false;
    }

    private void beginDrag() {
        dragActive = true;
        if (drag instanceof Drag.Tab tab) {
            showGhost(tab.paneId());
        } else if (drag instanceof Drag.FloatMove move) {
            DockFloat window = DockOps.findFloat(staged, move.floatId());
            if (window != null && window.node() instanceof DockLeaf leaf && leaf.active() != null) {
                showGhost(leaf.active());
            }
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!dragActive) {
            return drag != null;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE -> onDragMove(x, y);
            case MotionEvent.ACTION_UP -> finishDrag(x, y, true);
            case MotionEvent.ACTION_CANCEL -> finishDrag(x, y, false);
            default -> {
            }
        }
        return true;
    }

    private void onDragMove(float x, float y) {
        switch (drag) {
            case Drag.Tab tab -> updateDrop(x, y, null);
            case Drag.FloatMove move -> {
                apply(DockOps.moveFloat(layout, move.floatId(),
                        move.startX() + Math.round(x - lastDownX),
                        move.startY() + Math.round(y - lastDownY)), false);
                DockFloat window = DockOps.findFloat(layout, move.floatId());
                if (window != null && window.node() instanceof DockLeaf) {
                    updateDrop(x, y, move.floatId());
                } else {
                    clearDrop();
                }
            }
            case Drag.FloatResize resize -> {
                int dx = Math.round(x - lastDownX);
                int dy = Math.round(y - lastDownY);
                int nx = resize.startX();
                int ny = resize.startY();
                int nw = resize.startW();
                int nh = resize.startH();
                if ((resize.edges() & FloatWindowView.EDGE_E) != 0) {
                    nw = Math.max(FloatWindowView.MIN_W, resize.startW() + dx);
                }
                if ((resize.edges() & FloatWindowView.EDGE_S) != 0) {
                    nh = Math.max(FloatWindowView.MIN_H, resize.startH() + dy);
                }
                if ((resize.edges() & FloatWindowView.EDGE_W) != 0) {
                    nw = Math.max(FloatWindowView.MIN_W, resize.startW() - dx);
                    nx = resize.startX() + (resize.startW() - nw);
                }
                if ((resize.edges() & FloatWindowView.EDGE_N) != 0) {
                    nh = Math.max(FloatWindowView.MIN_H, resize.startH() - dy);
                    ny = resize.startY() + (resize.startH() - nh);
                }
                apply(DockOps.resizeFloat(layout, resize.floatId(), nx, ny, nw, nh), false);
            }
            case Drag.Split split -> {
                double delta = (split.row() ? x - lastDownX : y - lastDownY) / split.extent();
                apply(DockOps.adjustSplit(layout, split.splitId(), split.index(),
                        split.startShare() + delta), false);
            }
            case null -> {
            }
        }
        if (ghost.getVisibility() == VISIBLE) {
            ghost.setTranslationX(x + 12);
            ghost.setTranslationY(y + 12);
        }
    }

    private void finishDrag(float x, float y, boolean completed) {
        if (completed) {
            if (drag instanceof Drag.Tab tab) {
                apply(DockOps.dropTab(layout, ids, tab.paneId(), tab.fromLeafId(), drop,
                        Math.round(x), Math.round(y)), true);
            } else if (drag instanceof Drag.FloatMove move && drop != null) {
                apply(DockOps.dropFloat(layout, ids, move.floatId(), drop), true);
            }
        }
        if (drag != null) {
            onMutated.accept(layout);
        }
        drag = null;
        dragActive = false;
        ghost.setVisibility(GONE);
        clearDrop();
    }

    private void updateDrop(float x, float y, String ignoreFloat) {
        List<DropTarget.LeafHit> hits = new ArrayList<>();
        List<FloatWindowView> windows = new ArrayList<>(floatWindows.values());
        windows.sort(Comparator.comparingInt(w -> -zOf(w.floatId())));
        for (FloatWindowView window : windows) {
            collectHits(window.tree(), window.floatId(), hits);
        }
        collectHits(treeView, null, hits);
        DockRect stage = stageRect();
        drop = DropTarget.hitTest(Math.round(x), Math.round(y), stage,
                layout.tree() == null, hits, ignoreFloat);
        dropRect = null;
        if (drop != null && drop.kind() == DropTarget.Kind.LEAF) {
            for (DropTarget.LeafHit hit : hits) {
                if (hit.leafId().equals(drop.leafId())) {
                    dropRect = hit.rect();
                    break;
                }
            }
        }
        dropOverlay.update(drop, dropRect, stage, layout.tree() == null);
    }

    private void clearDrop() {
        drop = null;
        dropRect = null;
        dropOverlay.hide();
    }

    private void collectHits(DockTreeView tree, String floatId, List<DropTarget.LeafHit> into) {
        int[] mine = new int[2];
        int[] theirs = new int[2];
        getLocationInWindow(mine);
        tree.getLocationInWindow(theirs);
        int dx = theirs[0] - mine[0];
        int dy = theirs[1] - mine[1];
        tree.leafRects().forEach((leafId, rect) -> into.add(new DropTarget.LeafHit(
                leafId, floatId, new DockRect(rect.x() + dx, rect.y() + dy, rect.w(), rect.h()))));
    }

    private DockRect findSplitRect(String splitId) {
        DockRect local = treeView.splitRects().get(splitId);
        if (local != null) {
            return local;
        }
        for (FloatWindowView window : floatWindows.values()) {
            local = window.tree().splitRects().get(splitId);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    private int zOf(String floatId) {
        DockFloat window = DockOps.findFloat(layout, floatId);
        return window != null ? window.z() : 0;
    }

    private DockRect stageRect() {
        return new DockRect(GUTTER, GUTTER,
                Math.max(0, getWidth() - 2 * GUTTER), Math.max(0, getHeight() - 2 * GUTTER));
    }

    private void showGhost(String paneId) {
        DockPane pane = panes.get(paneId);
        ghostTitle.setText(pane != null ? pane.title() : paneId);
        String iconName = pane != null ? pane.icon() : null;
        if (iconName != null
                && FontRegistry.getInstance().hasGlyph(FontRegistry.DEFAULT_ICONS, iconName)) {
            ghostIcon.setIcon(FontRegistry.DEFAULT_ICONS, iconName, null);
            ghostIcon.setVisibility(VISIBLE);
        } else {
            ghostIcon.setVisibility(GONE);
        }
        ghost.setVisibility(VISIBLE);
    }

    private void rebuild() {
        removeAllViews();
        treeView.setNode(layout.tree(), true);
        addView(treeView);
        List<DockFloat> floats = new ArrayList<>(layout.floats());
        floats.sort(Comparator.comparingInt(DockFloat::z));
        floatWindows.keySet().removeIf(id -> DockOps.findFloat(layout, id) == null);
        for (DockFloat f : floats) {
            FloatWindowView window = floatWindows.computeIfAbsent(f.id(),
                    id -> new FloatWindowView(getContext(), this, id));
            window.setData(f, true);
            addView(window);
        }
        addView(ghost);
        addView(dropOverlay);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        treeView.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - 2 * GUTTER), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - 2 * GUTTER), MeasureSpec.EXACTLY));
        for (DockFloat f : staged.floats()) {
            FloatWindowView window = floatWindows.get(f.id());
            if (window != null) {
                window.measure(MeasureSpec.makeMeasureSpec(f.w(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(f.h(), MeasureSpec.EXACTLY));
            }
        }
        ghost.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        dropOverlay.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int prevWidth, int prevHeight) {
        super.onSizeChanged(width, height, prevWidth, prevHeight);
        // View.layout() runs setFrame() -> onSizeChanged() -> onLayout(), so restaging here is
        // picked up by this very traversal: no requestLayout() (a request raised during a layout
        // pass may be dropped) and no post(). Mutating the layout here would rebuild children
        // mid-traversal, so a queued toggle is replayed from the handler instead.
        restage();
        scheduleFlush();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        treeView.layout(GUTTER, GUTTER, getWidth() - GUTTER, getHeight() - GUTTER);
        // the staged frames, so a window stored off this stage still presents on it
        for (DockFloat f : staged.floats()) {
            FloatWindowView window = floatWindows.get(f.id());
            if (window != null) {
                window.layout(f.x(), f.y(), f.x() + f.w(), f.y() + f.h());
            }
        }
        ghost.layout(0, 0, ghost.getMeasuredWidth(), ghost.getMeasuredHeight());
        dropOverlay.layout(0, 0, getWidth(), getHeight());
    }
}
