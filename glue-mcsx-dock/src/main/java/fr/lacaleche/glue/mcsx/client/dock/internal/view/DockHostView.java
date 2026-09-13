package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.DockTags;
import fr.lacaleche.glue.mcsx.client.dock.internal.DockContentCache;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGeometry;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGuides;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockOperations;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DropTarget;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.Cursors;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Retained native workspace host and transaction boundary for all dock gestures. */
@Environment(EnvType.CLIENT)
public final class DockHostView extends ViewGroup {

    private static final float TAB_DRAG_THRESHOLD = 4;
    private static final int MINIMUM_VISIBLE_FLOAT_WIDTH = 40;

    private sealed interface Drag {
        DockLayout start();

        record Tab(DockLayout start, String paneId) implements Drag {
        }

        record Split(DockLayout start, DockSplit split, int index, boolean horizontal,
                     double share, double shareTotal, int extent) implements Drag {
        }

        record Move(DockLayout start, DockNode windowNode, int x, int y) implements Drag {
        }

        record Resize(DockLayout start, DockNode windowNode, int edges,
                      int x, int y, int width, int height) implements Drag {
        }
    }

    private final Map<String, DockPane> panes;
    private final DockContentCache content;
    private final Value<? extends Theme> theme;
    private final Consumer<DockLayout> onCompletedMutation;
    private final Consumer<Optional<String>> onMaximizedPaneChanged;
    private final ColorDrawable background = new ColorDrawable();
    private final DockThemeBinding themeBinding;
    private final DockTreeView tree;
    private final Map<DockNode, DockWindowView> windows = new IdentityHashMap<>();
    private final DockDragGhostView ghost;
    private final DockDropOverlayView dropOverlay;
    private final DockMaximizedPaneView maximizedOverlay;
    private final List<String> pendingToggles = new ArrayList<>();
    private DockLayout layout;
    private DockLayout staged;
    private Drag drag;
    private boolean dragActive;
    private boolean disposed;
    private float downX;
    private float downY;
    private DropTarget drop;
    private int gutter;
    private int splitterSize;
    private int headerHeight;
    private String maximizedPane;

    public DockHostView(Context context, Map<String, DockPane> panes, DockLayout layout,
                        Value<? extends Theme> theme, Consumer<DockLayout> onCompletedMutation,
                        Consumer<Optional<String>> onMaximizedPaneChanged) {
        super(context);
        this.panes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(panes, "panes")));
        this.content = new DockContentCache(context, panes);
        this.layout = Objects.requireNonNull(layout, "layout");
        this.staged = layout;
        this.theme = Objects.requireNonNull(theme, "theme");
        this.onCompletedMutation = Objects.requireNonNull(onCompletedMutation, "onCompletedMutation");
        this.onMaximizedPaneChanged = Objects.requireNonNull(onMaximizedPaneChanged, "onMaximizedPaneChanged");
        this.setTag(DockTags.HOST);
        this.setBackground(this.background);
        this.themeBinding = new DockThemeBinding(this, theme, value -> {
            DockMetrics metrics = value.get(ThemeTokens.DOCK_METRICS);
            this.background.setColor(value.get(ThemeTokens.DOCK_BACKGROUND));
            this.gutter = metrics.gutter();
            this.splitterSize = metrics.splitterSize();
            this.headerHeight = metrics.headerHeight();
            this.restage();
            this.requestLayout();
        });
        this.tree = new DockTreeView(context, this, null);
        this.ghost = new DockDragGhostView(context, this);
        this.dropOverlay = new DockDropOverlayView(context, this);
        this.maximizedOverlay = new DockMaximizedPaneView(context, this);
        try {
            this.rebuild();
        } catch (RuntimeException | Error failure) {
            try {
                this.content.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public DockLayout layout() {
        return this.layout;
    }

    public void setLayout(DockLayout layout) {
        Objects.requireNonNull(layout, "layout");
        this.cancelDrag();
        this.restoreMaximizedPane(false);
        this.apply(layout, true);
    }

    public void disposeContent() {
        if (this.disposed) return;

        this.disposed = true;
        this.pendingToggles.clear();
        this.cancelDrag();
        this.restoreMaximizedPane(false);
        this.content.close();
    }

    public void togglePane(String paneId) {
        this.requirePane(paneId);
        this.cancelDrag();
        if (Objects.equals(this.maximizedPane, paneId)) this.restoreMaximizedPane(false);
        if (this.getWidth() <= 0 || this.getHeight() <= 0) {
            this.pendingToggles.add(paneId);
            return;
        }
        DockLayout next = DockOperations.toggleFloat(this.layout, paneId, this.getWidth(), this.getHeight());
        this.complete(next, true);
    }

    public void focusPane(String paneId) {
        this.requirePane(paneId);
        this.cancelDrag();
        DockTabs tabs = findTabs(this.layout, paneId);
        if (tabs == null) return;

        if (this.maximizedPane != null && !Objects.equals(this.maximizedPane, paneId)) {
            this.restoreMaximizedPane(false);
        }

        DockLayout next = DockOperations.activate(this.layout, tabs, paneId);
        this.complete(next, false);
        this.content.getContent(paneId).post(() -> this.content.getContent(paneId).requestFocus());
    }

    public void maximizePane(String paneId) {
        this.requirePane(paneId);
        DockTabs tabs = findTabs(this.layout, paneId);
        if (tabs == null || Objects.equals(this.maximizedPane, paneId)) return;

        this.cancelDrag();
        View focused = this.findFocus();
        if (this.maximizedPane != null) this.restoreMaximizedPane(false);
        DockLayout activated = DockOperations.activate(this.layout, tabs, paneId);
        if (activated != this.layout) this.complete(activated, false);
        this.maximizedPane = paneId;
        this.presentStage();
        this.onMaximizedPaneChanged.accept(Optional.of(paneId));
        View paneContent = this.content(paneId);
        if (focused != null && isDescendant(paneContent, focused) && focused.isFocusable()) {
            focused.post(focused::requestFocus);
        } else {
            paneContent.post(() -> {
                if (!paneContent.requestFocus()) this.maximizedOverlay.requestFocus();
            });
        }
    }

    public void restoreMaximizedPane() {
        this.restoreMaximizedPane(true);
    }

    /**
     * The workspace's own Escape layer: an in-flight drag is abandoned and a maximized pane restored
     * before an otherwise unused Escape reaches Minecraft.
     *
     * @return whether the key was consumed
     */
    public boolean escapePressed() {
        if (this.dragActive) {
            this.cancelDrag();
            return true;
        }
        if (this.maximizedPane != null) {
            this.restoreMaximizedPane();
            return true;
        }
        return false;
    }

    private void restoreMaximizedPane(boolean restoreFocus) {
        if (this.maximizedPane == null) return;

        String paneId = this.maximizedPane;
        View focused = this.findFocus();
        View paneContent = this.content(paneId);
        this.maximizedOverlay.hide();
        this.maximizedPane = null;
        this.presentStage();
        this.remountPane(paneId);
        this.onMaximizedPaneChanged.accept(Optional.empty());
        if (restoreFocus && focused != null && isDescendant(paneContent, focused) && focused.isFocusable()) {
            focused.post(focused::requestFocus);
        }
    }

    Value<? extends Theme> theme() {
        return this.theme;
    }

    DockPane pane(String paneId) {
        return this.requirePane(paneId);
    }

    View content(String paneId) {
        return this.content.getContent(paneId);
    }

    View trailingHeader(String paneId) {
        return this.content.getTrailingHeader(paneId);
    }

    void tabPressed(DockTabs source, String paneId) {
        DockLayout activated = DockOperations.activate(this.layout, source, paneId);
        if (activated != this.layout) this.complete(activated, false);
        this.drag = new Drag.Tab(this.layout, paneId);
        this.dragActive = false;
    }

    void closePane(String paneId) {
        this.requirePane(paneId);
        this.cancelDrag();
        if (Objects.equals(this.maximizedPane, paneId)) this.restoreMaximizedPane(false);
        this.complete(DockOperations.detach(this.layout, paneId), true);
    }

    void splitterPressed(DockGeometry.Splitter splitter) {
        DockRect rect = this.splitRect(splitter.split());
        if (rect == null) return;

        DockSplit split = splitter.split();
        boolean horizontal = split.axis() == DockAxis.HORIZONTAL;
        int gaps = (split.children().size() - 1) * this.splitterSize;
        int extent = Math.max(1, (horizontal ? rect.width() : rect.height()) - gaps);
        this.drag = new Drag.Split(this.layout, split, splitter.index(), horizontal,
                split.shares().get(splitter.index()), DockOperations.shareTotal(split), extent);
        this.dragActive = false;
    }

    void floatTouched(DockWindow window) {
        DockWindow current = this.currentWindow(window.node());
        DockLayout raised = DockOperations.raiseFloat(this.layout, current);
        if (raised != this.layout) this.complete(raised, false);
    }

    void floatMovePressed(DockWindow window) {
        DockWindow current = this.currentWindow(window.node());
        this.floatTouched(current);
        current = this.currentWindow(window.node());
        DockWindow presented = this.stagedWindow(window.node());
        this.drag = new Drag.Move(this.layout, current.node(), presented.x(), presented.y());
        this.dragActive = false;
    }

    void floatResizePressed(DockWindow window, int edges) {
        this.floatTouched(window);
        DockWindow presented = this.stagedWindow(window.node());
        this.drag = new Drag.Resize(this.layout, window.node(), edges, presented.x(), presented.y(),
                presented.width(), presented.height());
        this.dragActive = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.themeBinding.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.themeBinding.detach();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                this.downX = event.getX();
                this.downY = event.getY();
                this.drag = null;
                this.dragActive = false;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (this.drag == null || this.dragActive) return this.dragActive;

                float distance = Math.max(Math.abs(event.getX() - this.downX), Math.abs(event.getY() - this.downY));
                if (!(this.drag instanceof Drag.Tab) || distance > TAB_DRAG_THRESHOLD) {
                    this.beginDrag(event.getX(), event.getY());
                    return true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!this.dragActive) this.drag = null;
            }
            default -> {
            }
        }
        return false;
    }

    /**
     * While a gesture is in flight the pointer keeps the gesture's cursor. Resolution runs on every
     * event against whatever sits under the pointer, and mid-drag that is arbitrary content the drag
     * is moving across, not the splitter or header it started on.
     */
    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        if (!this.dragActive) return super.onResolvePointerIcon(event);

        return switch (this.drag) {
            case Drag.Split split -> split.horizontal() ? Cursors.resizeHorizontal() : Cursors.resizeVertical();
            case Drag.Move ignored -> Cursors.move();
            case Drag.Resize resize -> DockWindowView.resizeCursor(resize.edges());
            case Drag.Tab ignored -> Cursors.pointer();
            case null -> super.onResolvePointerIcon(event);
        };
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!this.dragActive) return this.drag != null;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE -> this.moveDrag(event.getX(), event.getY());
            case MotionEvent.ACTION_UP -> {
                this.moveDrag(event.getX(), event.getY());
                this.finishDrag(event.getX(), event.getY(), true);
            }
            case MotionEvent.ACTION_CANCEL -> this.finishDrag(event.getX(), event.getY(), false);
            default -> {
            }
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        this.tree.measure(exact(Math.max(0, width - this.gutter * 2)),
                exact(Math.max(0, height - this.gutter * 2)));
        for (DockWindow window : this.staged.windows()) {
            DockWindowView view = this.windows.get(window.node());
            if (view != null) view.measure(exact(window.width()), exact(window.height()));
        }
        this.ghost.measure(atMost(width), atMost(height));
        this.dropOverlay.measure(exact(width), exact(height));
        this.maximizedOverlay.measure(exact(width), exact(height));
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        this.restage();
        if (!this.pendingToggles.isEmpty() && width > 0 && height > 0) {
            List<String> queued = List.copyOf(this.pendingToggles);
            this.pendingToggles.clear();
            this.post(() -> {
                if (this.disposed) return;

                DockLayout next = this.layout;
                for (String paneId : queued) next = DockOperations.toggleFloat(next, paneId, width, height);
                this.complete(next, true);
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        this.tree.layout(this.gutter, this.gutter, this.getWidth() - this.gutter, this.getHeight() - this.gutter);
        for (DockWindow window : this.staged.windows()) {
            DockWindowView view = this.windows.get(window.node());
            if (view != null) view.layout(window.x(), window.y(), window.x() + window.width(),
                    window.y() + window.height());
        }
        this.ghost.layoutAtDragPosition();
        this.dropOverlay.layout(0, 0, this.getWidth(), this.getHeight());
        this.maximizedOverlay.layout(0, 0, this.getWidth(), this.getHeight());
    }

    private void beginDrag(float x, float y) {
        this.dragActive = true;
        if (this.drag instanceof Drag.Tab tab) this.ghost.show(this.requirePane(tab.paneId()), x, y);
        else if (this.drag instanceof Drag.Move move) {
            DockWindow window = this.currentWindow(move.windowNode());
            if (window.node() instanceof DockTabs tabs) this.ghost.show(this.requirePane(tabs.active()), x, y);
        }
    }

    private void cancelDrag() {
        if (this.dragActive && this.drag != null) this.apply(this.drag.start(), true);
        this.drag = null;
        this.dragActive = false;
        this.drop = null;
        this.ghost.hide();
        this.dropOverlay.hide();
    }

    private void moveDrag(float x, float y) {
        switch (this.drag) {
            case Drag.Tab tab -> this.updateDrop(x, y, tab.paneId(), null);
            case Drag.Split split -> {
                double travelled = (split.horizontal() ? x - this.downX : y - this.downY) / split.extent();
                this.apply(DockOperations.adjustSplit(split.start(), split.split(), split.index(),
                        split.share() + travelled * split.shareTotal()), false);
            }
            case Drag.Move move -> {
                DockWindow window = windowWithNode(move.start(), move.windowNode());
                DockLayout next = DockOperations.moveFloat(move.start(), window,
                        move.x() + Math.round(x - this.downX), move.y() + Math.round(y - this.downY));
                this.apply(next, false);
                this.updateDrop(x, y, null, move.windowNode());
            }
            case Drag.Resize resize -> this.resize(resize, x, y);
            case null -> {
            }
        }
        if (this.ghost.getVisibility() == VISIBLE) this.ghost.move(x, y);
    }

    private void resize(Drag.Resize resize, float x, float y) {
        int deltaX = Math.round(x - this.downX);
        int deltaY = Math.round(y - this.downY);
        int nextX = resize.x();
        int nextY = resize.y();
        int nextWidth = resize.width();
        int nextHeight = resize.height();
        if ((resize.edges() & DockWindowView.EDGE_EAST) != 0) {
            nextWidth = Math.max(DockWindowView.MIN_WIDTH, resize.width() + deltaX);
        }
        if ((resize.edges() & DockWindowView.EDGE_SOUTH) != 0) {
            nextHeight = Math.max(DockWindowView.MIN_HEIGHT, resize.height() + deltaY);
        }
        if ((resize.edges() & DockWindowView.EDGE_WEST) != 0) {
            nextWidth = Math.max(DockWindowView.MIN_WIDTH, resize.width() - deltaX);
            nextX = resize.x() + resize.width() - nextWidth;
        }
        if ((resize.edges() & DockWindowView.EDGE_NORTH) != 0) {
            nextHeight = Math.max(DockWindowView.MIN_HEIGHT, resize.height() - deltaY);
            nextY = resize.y() + resize.height() - nextHeight;
        }
        DockWindow window = windowWithNode(resize.start(), resize.windowNode());
        this.apply(DockOperations.resizeFloat(resize.start(), window, nextX, nextY, nextWidth, nextHeight), false);
    }

    private void finishDrag(float x, float y, boolean completed) {
        DockLayout start = this.drag.start();
        try {
            if (!completed) {
                this.apply(start, true);
            } else if (this.drag instanceof Drag.Tab tab) {
                DockTabs source = findTabs(this.layout, tab.paneId());
                this.apply(DockOperations.dropTab(this.layout, tab.paneId(), source, this.drop,
                        Math.round(x), Math.round(y)), true);
            } else if (this.drag instanceof Drag.Move move && this.drop != null) {
                this.apply(DockOperations.dropFloat(
                        this.layout,
                        this.currentWindow(move.windowNode()),
                        this.drop
                ), true);
            }
            if (completed && this.layout != start) this.onCompletedMutation.accept(this.layout);
        } finally {
            this.drag = null;
            this.dragActive = false;
            this.drop = null;
            this.ghost.hide();
            this.dropOverlay.hide();
        }
    }

    /**
     * Resolves the pointer against the shared guide geometry: root and hovered-leaf guide controls
     * first, then the hovered leaf's tab strip as a positional insertion, then — for a dragged tab
     * over an empty tree — the whole stage. Guides are only built for operations the payload
     * supports, so nothing incompatible is ever previewed, and the overlay paints the same
     * rectangles this method resolves against.
     */
    private void updateDrop(float x, float y, String movedPaneId, DockNode movedWindowNode) {
        List<DropTarget.TabsHit> hits = new ArrayList<>();
        List<DockWindowView> ordered = new ArrayList<>(this.windows.values());
        ordered.sort(Comparator.comparingInt(view -> -view.window().stackingOrder()));
        for (DockWindowView window : ordered) this.collectHits(window.tree(), window.window().node(), hits);
        this.collectHits(this.tree, this.layout.tree(), hits);
        DockRect stage = this.stageRect();
        double pointerX = x;
        double pointerY = y;
        boolean treeEmpty = this.layout.tree() == null;
        boolean tabDrag = movedWindowNode == null;
        boolean mergeable = tabDrag || movedWindowNode instanceof DockTabs;
        boolean onStage = stage.contains(pointerX, pointerY);
        DropTarget.TabsHit hovered = onStage
                ? DockGuides.topHit(hits, pointerX, pointerY, stage,
                        tabDrag ? null : this.currentWindow(movedWindowNode))
                : null;

        boolean rootSplitAllowed = !tabDrag || treeEmpty
                || DockOperations.detach(this.layout, movedPaneId).tree() != null;
        List<DockGuides.Guide> rootGuides = !treeEmpty || !tabDrag
                ? DockGuides.rootGuides(stage, treeEmpty)
                : List.of();
        if (!rootSplitAllowed) rootGuides = List.of();
        boolean leafEdgesAllowed = hovered != null && (!tabDrag
                || !hovered.tabs().tabs().contains(movedPaneId)
                || hovered.tabs().tabs().size() > 1);
        List<DockGuides.Guide> leafGuides = hovered == null
                ? List.of()
                : DockGuides.leafGuides(hovered.tabs(), hovered.rect(), mergeable, leafEdgesAllowed);
        List<DockGuides.Guide> guides = DockGuides.combine(rootGuides, leafGuides);

        DropTarget target = null;
        DockRect caret = null;
        if (onStage) {
            DockGuides.Guide hot = DockGuides.hit(guides, pointerX, pointerY);
            if (hot != null) {
                target = hot.target();
            } else if (hovered != null && mergeable) {
                TabStrip strip = this.tabStrip(hovered);
                if (strip == null || !strip.bounds().contains(pointerX, pointerY)) {
                    this.showDrop(guides, null, null, null);
                    return;
                }
                List<DockRect> tabRects = strip.tabs();
                int index = DockGuides.insertionIndex(tabRects, pointerX);
                target = new DropTarget(DropTarget.Kind.TABS, hovered.tabs(), DropTarget.Zone.CENTER, index);
                caret = new DockRect(DockGuides.insertionX(tabRects, index, strip.bounds().x()) - 1,
                        strip.bounds().y(), 2, strip.bounds().height());
            } else if (treeEmpty && tabDrag) {
                target = new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.CENTER);
            }
        }

        DockRect preview = target == null
                ? null
                : DockGuides.previewRect(
                        target,
                        hovered == null ? null : hovered.rect(),
                        stage,
                        this.splitterSize,
                        target.kind() == DropTarget.Kind.ROOT ? this.layout.tree() : target.tabs(),
                        tabDrag ? DockLayouts.tabs(movedPaneId) : movedWindowNode
                );
        this.showDrop(guides, target, preview, caret);
    }

    private void showDrop(List<DockGuides.Guide> guides, DropTarget target, DockRect preview, DockRect caret) {
        this.drop = target;
        this.dropOverlay.show(guides, target, preview, caret);
    }

    /** The hovered leaf's live strip and tab rectangles in host coordinates. */
    private TabStrip tabStrip(DropTarget.TabsHit hit) {
        DockTreeView owner = this.tree;
        if (hit.window() != null) {
            DockWindowView windowView = this.windows.get(hit.window().node());
            if (windowView == null) return null;
            owner = windowView.tree();
        }
        DockLeafView leaf = owner.leaf(hit.tabs());
        if (leaf == null) return null;

        int[] hostLocation = new int[2];
        this.getLocationInWindow(hostLocation);
        DockRect strip = leaf.tabStripRectInWindow();
        DockRect bounds = new DockRect(strip.x() - hostLocation[0], strip.y() - hostLocation[1],
                strip.width(), strip.height());
        List<DockRect> rects = new ArrayList<>();
        for (DockRect rect : leaf.tabRectsInWindow()) {
            rects.add(new DockRect(rect.x() - hostLocation[0], rect.y() - hostLocation[1],
                    rect.width(), rect.height()));
        }
        return new TabStrip(bounds, rects);
    }

    private record TabStrip(DockRect bounds, List<DockRect> tabs) {
    }

    /**
     * Solves the hit rectangles from the live layout node rather than reading the view's cached
     * solve: a press-time mutation — activating the pressed tab replaces its group node — happens in
     * the same event batch as the first drag move, before any measure pass refreshes the cache, and
     * hits carrying the replaced node identity would fail every identity check at the drop.
     */
    private void collectHits(DockTreeView tree, DockNode liveNode, List<DropTarget.TabsHit> hits) {
        int[] hostLocation = new int[2];
        int[] treeLocation = new int[2];
        this.getLocationInWindow(hostLocation);
        tree.getLocationInWindow(treeLocation);
        int offsetX = treeLocation[0] - hostLocation[0];
        int offsetY = treeLocation[1] - hostLocation[1];
        DockWindow owner = tree.ownerWindow() == null ? null : tree.ownerWindow().window();
        DockGeometry.Solved solved = DockGeometry.solve(liveNode,
                new DockRect(0, 0, tree.getWidth(), tree.getHeight()), this.splitterSize);
        solved.tabs().forEach((tabs, rect) -> hits.add(new DropTarget.TabsHit(tabs, owner,
                new DockRect(rect.x() + offsetX, rect.y() + offsetY, rect.width(), rect.height()))));
    }

    private void complete(DockLayout next, boolean structural) {
        if (next == this.layout) return;

        this.apply(next, structural);
        this.onCompletedMutation.accept(this.layout);
    }

    private void apply(DockLayout next, boolean structural) {
        if (next == this.layout) return;

        DockLayout previous = this.layout;
        boolean rebuild = structural || !this.canRetainWindows(previous, next);
        View focused = rebuild ? this.findFocus() : null;
        this.layout = next;
        this.restage();
        if (rebuild) this.rebuild();
        else {
            this.tree.setNode(next.tree(), false);
            this.updateRetainedWindows(previous, next);
        }
        this.presentStage();
        this.requestLayout();
        this.invalidate();
        if (focused != null && focused.isFocusable() && isDescendant(this, focused)) {
            focused.post(focused::requestFocus);
        }
    }

    private boolean canRetainWindows(DockLayout previous, DockLayout next) {
        if (previous.windows().size() != next.windows().size()) return false;

        for (int index = 0; index < next.windows().size(); index++) {
            DockWindow window = next.windows().get(index);
            DockWindow prior = previous.windows().get(index);
            if (!this.windows.containsKey(window.node()) && !this.windows.containsKey(prior.node())) return false;
        }
        return true;
    }

    private void updateRetainedWindows(DockLayout previous, DockLayout next) {
        Map<DockNode, DockWindowView> retained = new IdentityHashMap<>();
        for (int index = 0; index < next.windows().size(); index++) {
            DockWindow window = next.windows().get(index);
            DockWindowView view = this.windows.get(window.node());
            if (view == null) view = this.windows.get(previous.windows().get(index).node());
            view.setWindow(window, false);
            retained.put(window.node(), view);
        }
        this.windows.clear();
        this.windows.putAll(retained);

        List<DockWindow> ordered = new ArrayList<>(next.windows());
        ordered.sort(Comparator.comparingInt(DockWindow::stackingOrder));
        for (DockWindow window : ordered) this.bringChildToFront(this.windows.get(window.node()));
        this.bringChildToFront(this.ghost);
        this.bringChildToFront(this.dropOverlay);
        this.bringChildToFront(this.maximizedOverlay);
    }

    private void rebuild() {
        Map<DockNode, DockWindowView> previous = new IdentityHashMap<>(this.windows);
        this.removeAllViews();
        this.tree.setNode(this.layout.tree(), true);
        this.addView(this.tree);
        this.windows.clear();
        List<DockWindow> ordered = new ArrayList<>(this.layout.windows());
        ordered.sort(Comparator.comparingInt(DockWindow::stackingOrder));
        for (DockWindow window : ordered) {
            DockWindowView view = previous.remove(window.node());
            if (view == null) view = new DockWindowView(this.getContext(), this);
            view.setWindow(window, true);
            this.windows.put(window.node(), view);
            this.addView(view);
        }
        this.addView(this.ghost);
        this.addView(this.dropOverlay);
        this.addView(this.maximizedOverlay);
    }

    private void remountPane(String paneId) {
        if (this.tree.remountPane(paneId)) return;
        for (DockWindowView window : this.windows.values()) {
            if (window.tree().remountPane(paneId)) return;
        }
        throw new IllegalStateException("Open dock pane has no presentation: " + paneId);
    }

    /**
     * Presents whatever the stage should currently show. A maximized pane owns the whole stage: its
     * overlay is (re)mounted on top, and the docked tree and floating windows underneath go
     * {@code INVISIBLE} — an opaque overlay covered them anyway, and a transparent one is a viewport
     * whose hole must reach the world rather than the panes behind it. The visibility change is also
     * what makes a covered viewport pane release its game-viewport claim, through
     * {@code onVisibilityChanged}.
     */
    private void presentStage() {
        String paneId = this.maximizedPane;
        if (paneId != null) {
            this.maximizedOverlay.show(this.requirePane(paneId), this.content(paneId), this.trailingHeader(paneId));
            this.bringChildToFront(this.maximizedOverlay);
        }
        int visibility = paneId == null ? VISIBLE : INVISIBLE;
        this.tree.setVisibility(visibility);
        for (DockWindowView window : this.windows.values()) window.setVisibility(visibility);
    }

    private void restage() {
        this.staged = this.getWidth() > 0 && this.getHeight() > 0
                ? DockOperations.presentOnStage(this.layout, this.getWidth(), this.getHeight(),
                        MINIMUM_VISIBLE_FLOAT_WIDTH, this.headerHeight)
                : this.layout;
    }

    private DockPane requirePane(String paneId) {
        DockPane pane = this.panes.get(Objects.requireNonNull(paneId, "paneId"));
        if (pane == null) throw new IllegalArgumentException("Unknown dock pane: " + paneId);
        return pane;
    }

    private DockWindow currentWindow(DockNode node) {
        DockWindow window = windowWithNode(this.layout, node);
        if (window == null) throw new IllegalArgumentException("Unknown floating dock window");
        return window;
    }

    private DockWindow stagedWindow(DockNode node) {
        DockWindow window = windowWithNode(this.staged, node);
        if (window == null) throw new IllegalArgumentException("Unknown floating dock window");
        return window;
    }

    private DockRect splitRect(DockSplit split) {
        DockRect rect = this.tree.solved().splits().get(split);
        if (rect != null) return rect;
        for (DockWindowView window : this.windows.values()) {
            rect = window.tree().solved().splits().get(split);
            if (rect != null) return rect;
        }
        return null;
    }

    private DockRect stageRect() {
        return new DockRect(this.gutter, this.gutter, Math.max(0, this.getWidth() - this.gutter * 2),
                Math.max(0, this.getHeight() - this.gutter * 2));
    }

    private static DockWindow windowWithNode(DockLayout layout, DockNode node) {
        for (DockWindow window : layout.windows()) {
            if (window.node() == node) return window;
        }
        return null;
    }

    private static DockTabs findTabs(DockLayout layout, String paneId) {
        DockTabs tabs = findTabs(layout.tree(), paneId);
        if (tabs != null) return tabs;
        for (DockWindow window : layout.windows()) {
            tabs = findTabs(window.node(), paneId);
            if (tabs != null) return tabs;
        }
        return null;
    }

    private static DockTabs findTabs(DockNode node, String paneId) {
        if (node instanceof DockTabs tabs) return tabs.tabs().contains(paneId) ? tabs : null;
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                DockTabs found = findTabs(child, paneId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isDescendant(View root, View target) {
        if (root == target) return true;
        if (root instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                if (isDescendant(group.getChildAt(index), target)) return true;
            }
        }
        return false;
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    private static int atMost(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
    }
}
