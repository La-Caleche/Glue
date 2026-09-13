package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.dock.DockTags;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.ViewGroup;

final class DockWindowView extends ViewGroup {

    static final int MIN_WIDTH = 220;
    static final int MIN_HEIGHT = 150;
    static final int EDGE_NORTH = 1;
    static final int EDGE_SOUTH = 2;
    static final int EDGE_WEST = 4;
    static final int EDGE_EAST = 8;

    private static final int EDGE_HIT = 7;
    private static final int CORNER_HIT = 13;

    private final DockHostView host;
    private final DockTreeView tree;
    private final ShapeDrawable background = new ShapeDrawable();
    private final DockThemeBinding theme;
    private DockWindow window;
    private int borderWidth;

    DockWindowView(Context context, DockHostView host) {
        super(context);
        this.host = host;
        this.setTag(DockTags.WINDOW);
        this.setBackground(this.background);
        this.tree = new DockTreeView(context, host, this);
        this.addView(this.tree);
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            DockMetrics metrics = value.get(ThemeTokens.DOCK_METRICS);
            this.background.setColor(value.get(
                    metrics.borderWidth() > 0
                            ? ThemeTokens.DOCK_BORDER
                            : ThemeTokens.DOCK_PANE_BACKGROUND
            ));
            this.background.setCornerRadius(metrics.cornerRadius());
            this.borderWidth = metrics.borderWidth();
            this.requestLayout();
        });
    }

    void setWindow(DockWindow window, boolean structural) {
        this.window = window;
        this.tree.setNode(window.node(), structural);
    }

    DockWindow window() {
        return this.window;
    }

    DockTreeView tree() {
        return this.tree;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.theme.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.theme.detach();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int edges = this.edgeMask(event.getX(), event.getY());
            if (edges != 0) {
                this.host.floatResizePressed(this.window, edges);
                return true;
            }
            this.host.floatTouched(this.window);
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        int edges = this.edgeMask(event.getX(), event.getY());
        return edges != 0 ? resizeCursor(edges) : super.onResolvePointerIcon(event);
    }

    /** The resize cursor for a non-empty {@code EDGE_*} mask, shared with the host's drag cursor. */
    static PointerIcon resizeCursor(int edges) {
        boolean west = (edges & EDGE_WEST) != 0;
        boolean east = (edges & EDGE_EAST) != 0;
        boolean north = (edges & EDGE_NORTH) != 0;
        boolean south = (edges & EDGE_SOUTH) != 0;
        if ((north && west) || (south && east)) return Cursors.resizeNorthWestSouthEast();
        if ((north && east) || (south && west)) return Cursors.resizeNorthEastSouthWest();
        if (west || east) return Cursors.resizeHorizontal();
        return Cursors.resizeVertical();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        this.tree.measure(exact(Math.max(0, width - this.borderWidth * 2)),
                exact(Math.max(0, height - this.borderWidth * 2)));
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        this.tree.layout(this.borderWidth, this.borderWidth,
                Math.max(this.borderWidth, this.getWidth() - this.borderWidth),
                Math.max(this.borderWidth, this.getHeight() - this.borderWidth));
    }

    private int edgeMask(float x, float y) {
        int edges = 0;
        boolean cornerWest = x < CORNER_HIT && (y < CORNER_HIT || y > this.getHeight() - CORNER_HIT);
        boolean cornerEast = x > this.getWidth() - CORNER_HIT
                && (y < CORNER_HIT || y > this.getHeight() - CORNER_HIT);
        boolean cornerNorth = y < CORNER_HIT && (x < CORNER_HIT || x > this.getWidth() - CORNER_HIT);
        boolean cornerSouth = y > this.getHeight() - CORNER_HIT
                && (x < CORNER_HIT || x > this.getWidth() - CORNER_HIT);
        if (x < EDGE_HIT || cornerWest) edges |= EDGE_WEST;
        if (x > this.getWidth() - EDGE_HIT || cornerEast) edges |= EDGE_EAST;
        if (y < EDGE_HIT || cornerNorth) edges |= EDGE_NORTH;
        if (y > this.getHeight() - EDGE_HIT || cornerSouth) edges |= EDGE_SOUTH;
        return edges;
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }
}
