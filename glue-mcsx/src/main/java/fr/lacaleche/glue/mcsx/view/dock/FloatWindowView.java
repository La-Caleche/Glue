package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockFloat;
import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.cursor.Cursors;
import fr.lacaleche.glue.mcsx.view.FontRegistry;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import fr.lacaleche.glue.mcsx.view.IconView;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

/**
 * A floating dock window: title bar (drag to move, × to close), a {@link DockTreeView} body, and
 * resize zones along every edge and corner. Move/resize/redock gestures are armed here but owned
 * by {@link DockHostView} — the window only decides <em>what</em> was pressed.
 */
final class FloatWindowView extends ViewGroup {

    static final int HEADER_H = 30;
    static final int MIN_W = 220;
    static final int MIN_H = 150;
    static final int EDGE_N = 1;
    static final int EDGE_S = 2;
    static final int EDGE_W = 4;
    static final int EDGE_E = 8;

    private static final int EDGE_HIT = 6;
    private static final int CORNER_HIT = 12;

    private final DockHostView host;
    private final FlexLayout header;
    private final FrameLayout iconSlot;
    private final TextView title;
    private final DockTreeView tree;
    private String floatId;

    FloatWindowView(Context context, DockHostView host, String floatId) {
        super(context);
        this.host = host;
        this.floatId = floatId;

        ShapeDrawable chrome = new ShapeDrawable();
        chrome.setShape(ShapeDrawable.RECTANGLE);
        chrome.setCornerRadius(12);
        setBackground(chrome);

        header = new FlexLayout(context) {
            @Override
            public boolean onTouchEvent(@NonNull MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    FloatWindowView.this.host.floatHeaderPressed(FloatWindowView.this.floatId);
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };
        header.setOrientation(Orientation.ROW);
        header.setAlignItems(Align.CENTER);
        header.setGap(7);
        header.setPadding(10, 0, 8, 0);

        iconSlot = new FrameLayout(context);
        header.addView(iconSlot, new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        title = new TextView(context);
        title.setTextSize(12f);
        FlexLayout.LayoutParams titleParams = new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.grow = 1f;
        header.addView(title, titleParams);

        TextView floating = new TextView(context);
        floating.setText("floating");
        floating.setTextSize(10.5f);
        header.addView(floating, new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        IconView close = DockChrome.closeButton(context, () -> this.host.closeFloat(this.floatId));
        header.addView(close, new FlexLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        addView(header);
        tree = new DockTreeView(context, host, floatId);
        addView(tree);

        Themed.onTheme(this, theme -> {
            chrome.setColor(theme.color(Tokens.SURFACE_2));
            chrome.setStroke(1, theme.color(Tokens.BORDER_STRONG));
            title.setTextColor(theme.color(Tokens.TEXT_PRIMARY));
            floating.setTextColor(theme.color(Tokens.TEXT_SUBTLE));
            close.setColor(theme.color(Tokens.TEXT_SUBTLE));
            if (iconSlot.getChildCount() > 0) {
                ((IconView) iconSlot.getChildAt(0)).setColor(theme.color(Tokens.ACCENT));
            }
        });
    }

    void setData(DockFloat data, boolean structural) {
        floatId = data.id();
        tree.setNode(data.node(), structural);
        DockPane pane = data.node() instanceof DockLeaf leaf && leaf.active() != null
                ? host.pane(leaf.active()) : null;
        title.setText(pane != null ? pane.title() : "Window");
        iconSlot.removeAllViews();
        String iconName = pane != null ? pane.icon() : null;
        if (iconName != null
                && FontRegistry.getInstance().hasGlyph(FontRegistry.DEFAULT_ICONS, iconName)) {
            IconView icon = new IconView(
                    getContext(), FontRegistry.DEFAULT_ICONS, iconName, null, 13);
            icon.setColor(Themes.active().color(Tokens.ACCENT));
            iconSlot.addView(icon, new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    String floatId() {
        return floatId;
    }

    DockTreeView tree() {
        return tree;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        header.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(HEADER_H, MeasureSpec.EXACTLY));
        tree.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - HEADER_H), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        header.layout(0, 0, width, HEADER_H);
        tree.layout(0, HEADER_H, width, height);
    }

    /** Presses landing on the resize border claim the pointer before any child sees it. */
    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            host.floatTouched(floatId);
            int edges = edgeMask(event.getX(), event.getY());
            if (edges != 0) {
                host.floatResizePressed(floatId, edges);
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // consume the DOWN whose intercept armed a resize, and any stray press on the chrome —
        // a click on a floating window must never fall through to the pane beneath it
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        int edges = edgeMask(event.getX(), event.getY());
        boolean n = (edges & EDGE_N) != 0;
        boolean s = (edges & EDGE_S) != 0;
        boolean w = (edges & EDGE_W) != 0;
        boolean e = (edges & EDGE_E) != 0;
        if ((n && w) || (s && e)) {
            return Cursors.resizeNWSE();
        }
        if ((n && e) || (s && w)) {
            return Cursors.resizeNESW();
        }
        if (w || e) {
            return Cursors.resizeEW();
        }
        if (n || s) {
            return Cursors.resizeNS();
        }
        return super.onResolvePointerIcon(event);
    }

    private int edgeMask(float x, float y) {
        int edges = 0;
        boolean nearW = x < EDGE_HIT;
        boolean nearE = x > getWidth() - EDGE_HIT;
        boolean nearN = y < EDGE_HIT;
        boolean nearS = y > getHeight() - EDGE_HIT;
        if (nearW || (x < CORNER_HIT && (y < CORNER_HIT || y > getHeight() - CORNER_HIT))) {
            edges |= EDGE_W;
        }
        if (nearE || (x > getWidth() - CORNER_HIT && (y < CORNER_HIT || y > getHeight() - CORNER_HIT))) {
            edges |= EDGE_E;
        }
        if (nearN || (y < CORNER_HIT && (x < CORNER_HIT || x > getWidth() - CORNER_HIT))) {
            edges |= EDGE_N;
        }
        if (nearS || (y > getHeight() - CORNER_HIT && (x < CORNER_HIT || x > getWidth() - CORNER_HIT))) {
            edges |= EDGE_S;
        }
        return edges;
    }
}
