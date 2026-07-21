package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import fr.lacaleche.glue.mcsx.view.FontRegistry;
import fr.lacaleche.glue.mcsx.view.IconView;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.TextView;

/**
 * A leaf's tab strip. Pressing a tab activates it and arms a host-level drag (the drag itself —
 * threshold, ghost, drop zones — is owned by {@link DockHostView}, because it crosses panes);
 * the small × closes the tab. Restyling on active-change reuses the existing tab views so a
 * mid-press activation never detaches the view under the pointer.
 */
final class TabStripView extends FlexLayout {

    static final int HEIGHT = 32;

    private final DockHostView host;
    private final String floatId;
    private DockLeaf leaf;

    TabStripView(Context context, DockHostView host, String floatId) {
        super(context);
        this.host = host;
        this.floatId = floatId;
        setOrientation(Orientation.ROW);
        setAlignItems(Align.CENTER);
        setGap(3);
        setPadding(6, 0, 6, 0);
        setWillNotDraw(false);
        // the strip's own colours live in onDraw; the binding only re-triggers it on theme change
        Themed.onTheme(this, theme -> {
        });
    }

    void setLeaf(DockLeaf value) {
        boolean sameTabs = leaf != null && leaf.tabs().equals(value.tabs());
        leaf = value;
        if (sameTabs) {
            for (int i = 0; i < getChildCount(); i++) {
                ((TabView) getChildAt(i)).restyle();
            }
        } else {
            removeAllViews();
            for (String paneId : leaf.tabs()) {
                addView(new TabView(getContext(), paneId),
                        new LayoutParams(LayoutParams.WRAP_CONTENT, 24));
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = Paint.obtain();
        paint.setColor(Themes.active().color(Tokens.BORDER));
        canvas.drawRect(0f, getHeight() - 1f, getWidth(), getHeight(), paint);
        paint.recycle();
    }

    /** One tab: optional icon, title, close ×; the accent underline marks the active one. */
    private final class TabView extends FlexLayout {

        private final String paneId;
        private final IconView icon;
        private final TextView title;
        private final Paint underline = new Paint();
        private ShapeDrawable activeBackground;

        TabView(Context context, String paneId) {
            super(context);
            this.paneId = paneId;
            setOrientation(Orientation.ROW);
            setAlignItems(Align.CENTER);
            setGap(6);
            setPadding(9, 0, 5, 0);
            setWillNotDraw(false);

            DockPane pane = host.pane(paneId);
            String iconName = pane != null ? pane.icon() : null;
            if (iconName != null
                    && FontRegistry.getInstance().hasGlyph(FontRegistry.DEFAULT_ICONS, iconName)) {
                icon = new IconView(context, FontRegistry.DEFAULT_ICONS, iconName, null, 14);
                addView(icon, new LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            } else {
                icon = null;
            }

            title = new TextView(context);
            title.setText(pane != null ? pane.title() : paneId);
            title.setTextSize(12f);
            addView(title, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            IconView close = DockChrome.closeButton(context, () -> host.closeTab(paneId));
            addView(close, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            Themed.onTheme(this, theme -> {
                close.setColor(theme.color(Tokens.TEXT_SUBTLE));
                restyle();
            });
        }

        void restyle() {
            boolean active = paneId.equals(leaf.active());
            title.setTextColor(Themes.active().color(active ? Tokens.TEXT_PRIMARY : Tokens.TEXT_MUTED));
            if (icon != null) {
                icon.setColor(Themes.active().color(active ? Tokens.ACCENT : Tokens.TEXT_SUBTLE));
            }
            if (active) {
                if (activeBackground == null) {
                    activeBackground = new ShapeDrawable();
                    activeBackground.setShape(ShapeDrawable.RECTANGLE);
                    activeBackground.setCornerRadius(6);
                }
                // re-tinted every restyle so a theme change still lands on the cached drawable
                activeBackground.setColor(Themes.active().color(Tokens.SURFACE_ACTIVE));
                setBackground(activeBackground);
            } else {
                setBackground(null);
            }
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (paneId.equals(leaf.active())) {
                underline.setColor(Themes.active().color(Tokens.ACCENT));
                canvas.drawRect(4f, getHeight() - 2f, getWidth() - 4f, getHeight(), underline);
            }
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                host.tabPressed(leaf, paneId, floatId);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
