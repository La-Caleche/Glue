package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.UiSounds;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.DockTags;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DockLeafView extends ViewGroup {

    private final DockHostView host;
    private final DockWindowView ownerWindow;
    private final HeaderLayout header;
    private final View moveArea;
    private final LinearLayout tabs;
    private final FrameLayout trailingHeader;
    private final TextView maximize;
    private final DockContentView content;
    private final List<TabPresentation> presentations = new ArrayList<>();
    private final ShapeDrawable panelBackground = new ShapeDrawable();
    private final ColorDrawable headerBackground = new ColorDrawable();
    private final ColorDrawable contentBackground = new ColorDrawable();
    private final DockThemeBinding theme;
    private DockTabs group;
    private int headerHeight;
    private int borderWidth;
    private int primaryColor;
    private int mutedColor;
    private int activeTabColor;
    private int onColor;
    private int tabTextSize;
    private int tabPadding;
    private int iconGap;
    private int closeWidth;
    private int controlWidth;
    private int paneBackgroundColor;
    private int borderColor;
    private View activeContent;

    DockLeafView(Context context, DockHostView host, DockWindowView ownerWindow) {
        super(context);
        this.host = host;
        this.ownerWindow = ownerWindow;
        this.setBackground(this.panelBackground);
        this.header = new HeaderLayout(context);
        this.header.setBackground(this.headerBackground);
        this.moveArea = this.createMoveArea();
        this.header.addView(this.moveArea);
        this.tabs = new LinearLayout(context);
        this.tabs.setOrientation(LinearLayout.HORIZONTAL);
        this.header.addView(this.tabs);
        this.trailingHeader = new FrameLayout(context);
        this.header.addView(this.trailingHeader);
        this.maximize = new TextView(context);
        this.maximize.setText("MAX");
        this.maximize.setGravity(Gravity.CENTER);
        this.maximize.setTag(DockTags.MAXIMIZE);
        this.maximize.setOnClickListener(ignored -> {
            UiSounds.playClick();
            this.host.maximizePane(this.group.active());
        });
        this.header.addView(this.maximize);
        this.content = new DockContentView(context);
        this.content.setBackground(this.contentBackground);
        this.addView(this.header);
        this.addView(this.content);
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            DockMetrics metrics = value.get(ThemeTokens.DOCK_METRICS);
            this.borderColor = value.get(ThemeTokens.DOCK_BORDER);
            this.paneBackgroundColor = value.get(ThemeTokens.DOCK_PANE_BACKGROUND);
            this.panelBackground.setCornerRadius(metrics.cornerRadius());
            this.headerBackground.setColor(value.get(ThemeTokens.DOCK_HEADER_BACKGROUND));
            this.primaryColor = value.get(ThemeTokens.TEXT_PRIMARY);
            this.mutedColor = value.get(ThemeTokens.TEXT_MUTED);
            this.activeTabColor = value.get(ThemeTokens.DOCK_ACTIVE_TAB_BACKGROUND);
            this.onColor = value.get(ThemeTokens.CONTROL_ON);
            this.tabTextSize = Math.max(1, metrics.tabTextSize());
            this.tabPadding = metrics.tabPadding();
            this.iconGap = metrics.iconGap();
            this.closeWidth = metrics.tabCloseWidth();
            this.controlWidth = metrics.controlWidth();
            this.maximize.setTextColor(this.mutedColor);
            this.maximize.setTextSize(Math.max(1, metrics.controlTextSize()));
            this.headerHeight = metrics.headerHeight();
            this.borderWidth = metrics.borderWidth();
            this.applyPaneBackground();
            this.restyleTabs();
            this.requestLayout();
        });
    }

    void update(DockTabs group) {
        // Compares identity, not equality: an equal replacement still has to be adopted, because the
        // layout only ever locates this leaf's group by object identity.
        if (group == this.group) return;

        boolean sameTabs = this.group != null && this.group.tabs().equals(group.tabs());
        String previousActive = this.group == null ? null : this.group.active();
        this.group = group;
        if (!sameTabs) this.rebuildTabs();
        this.theme.applyCurrent();
        if (!Objects.equals(previousActive, group.active())) this.mountActive();
    }

    boolean remountPane(String paneId) {
        if (this.group == null || !Objects.equals(this.group.active(), paneId)) return false;

        this.mountActive();
        return true;
    }

    /** Each tab control's rectangle in window coordinates, in strip order, for insertion targeting. */
    List<DockRect> tabRectsInWindow() {
        List<DockRect> rects = new ArrayList<>(this.presentations.size());
        int[] location = new int[2];
        for (TabPresentation presentation : this.presentations) {
            presentation.tab().getLocationInWindow(location);
            rects.add(new DockRect(location[0], location[1],
                    presentation.tab().getWidth(), presentation.tab().getHeight()));
        }
        return rects;
    }

    DockRect tabStripRectInWindow() {
        int[] location = new int[2];
        this.tabs.getLocationInWindow(location);
        return new DockRect(location[0], location[1], this.tabs.getWidth(), this.tabs.getHeight());
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int innerWidth = Math.max(0, width - this.borderWidth * 2);
        int innerHeight = Math.max(0, height - this.borderWidth * 2);
        int measuredHeaderHeight = Math.min(this.headerHeight, innerHeight);
        this.header.measure(exact(innerWidth), exact(measuredHeaderHeight));
        this.content.measure(exact(innerWidth), exact(Math.max(0, innerHeight - measuredHeaderHeight)));
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int innerRight = Math.max(this.borderWidth, this.getWidth() - this.borderWidth);
        int innerBottom = Math.max(this.borderWidth, this.getHeight() - this.borderWidth);
        int headerBottom = Math.min(this.borderWidth + this.headerHeight, innerBottom);
        this.header.layout(this.borderWidth, this.borderWidth, innerRight, headerBottom);
        this.content.layout(this.borderWidth, headerBottom, innerRight, innerBottom);
    }

    private View createMoveArea() {
        View area = new View(this.getContext()) {
            @Override
            public boolean onTouchEvent(@NonNull MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && DockLeafView.this.ownerWindow != null) {
                    DockLeafView.this.host.floatMovePressed(DockLeafView.this.ownerWindow.window());
                    return true;
                }
                return false;
            }

            @Override
            public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
                if (DockLeafView.this.ownerWindow != null) return Cursors.move();
                return super.onResolvePointerIcon(event);
            }
        };
        area.setTag(DockTags.MOVE_AREA);
        return area;
    }

    private void rebuildTabs() {
        this.tabs.removeAllViews();
        this.presentations.clear();
        for (String paneId : this.group.tabs()) {
            DockPane pane = this.host.pane(paneId);
            TabView tab = new TabView(this.getContext(), paneId);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setTag(paneId);
            Cursors.set(tab, Cursors.hand());
            Text icon = pane.icon() == null ? null : new Text(this.getContext(), pane.icon());
            if (icon != null) {
                icon.setSingleLine(true);
                icon.setGravity(Gravity.CENTER_VERTICAL);
                tab.addView(icon, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            }
            Text title = new Text(this.getContext(), pane.title());
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER_VERTICAL);
            tab.addView(title, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT
            ));
            TextView close = null;
            if (pane.closable()) {
                close = new TextView(this.getContext());
                close.setText("x");
                close.setGravity(Gravity.CENTER);
                close.setOnClickListener(ignored -> {
                    UiSounds.playClick();
                    this.host.closePane(paneId);
                });
                tab.addView(close, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
            }
            this.presentations.add(new TabPresentation(paneId, tab, icon, title, close));
            this.tabs.addView(tab, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }
    }

    private void mountActive() {
        View focused = this.findFocus();
        this.activeContent = this.host.content(this.group.active());
        this.mount(this.content, this.activeContent);
        View trailing = this.host.trailingHeader(this.group.active());
        this.trailingHeader.removeAllViews();
        if (trailing != null) this.mount(this.trailingHeader, trailing);
        if (focused != null && isDescendant(this.activeContent, focused)) {
            focused.post(focused::requestFocus);
        }
        this.applyPaneBackground();
    }

    /**
     * A transparent pane paints neither its shell nor content area, so a dockspace hosted without a
     * background becomes a viewport onto whatever it covers.
     */
    private void applyPaneBackground() {
        String active = this.group == null ? null : this.group.active();
        boolean transparent = active != null && this.host.pane(active).transparent();
        this.content.setCutout(transparent);
        this.contentBackground.setColor(transparent ? 0 : this.paneBackgroundColor);
        this.panelBackground.setColor(transparent ? 0
                : this.borderWidth > 0 ? this.borderColor : this.paneBackgroundColor);
        this.panelBackground.setStroke(
                transparent && this.borderWidth > 0 ? this.borderWidth : 0,
                this.borderColor
        );
    }

    private void mount(FrameLayout target, View view) {
        target.removeAllViews();
        if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        target.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Applies every themed tab metric and colour, so a theme change never needs a tab rebuild. */
    private void restyleTabs() {
        String active = this.group == null ? null : this.group.active();
        for (TabPresentation presentation : this.presentations) {
            boolean selected = Objects.equals(presentation.paneId, active);
            int labelColor = selected ? this.primaryColor : this.mutedColor;
            presentation.tab.setPadding(this.tabPadding, 0,
                    presentation.close == null ? this.tabPadding : 0, 0);
            presentation.title.textSize(this.tabTextSize).color(labelColor);
            ((LinearLayout.LayoutParams) presentation.title.getLayoutParams())
                    .setMargins(presentation.icon == null ? 0 : this.iconGap, 0, 0, 0);
            if (presentation.icon != null) {
                presentation.icon.textSize(this.tabTextSize).color(selected ? this.onColor : labelColor);
            }
            if (presentation.close != null) {
                presentation.close.setTextSize(this.tabTextSize);
                presentation.close.setTextColor(this.mutedColor);
                presentation.close.getLayoutParams().width = this.closeWidth;
            }
            presentation.tab.setBackground(new ColorDrawable(selected ? this.activeTabColor : Color.TRANSPARENT));
            presentation.tab.invalidate();
        }
    }

    private final class TabView extends LinearLayout {

        private final String paneId;
        private final Paint indicator = new Paint();

        private TabView(Context context, String paneId) {
            super(context);
            this.paneId = paneId;
            this.setWillNotDraw(false);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (!this.paneId.equals(DockLeafView.this.group.active())) return;

            this.indicator.setColor(DockLeafView.this.onColor);
            canvas.drawRect(4, Math.max(0, this.getHeight() - 2), Math.max(4, this.getWidth() - 4),
                    this.getHeight(), this.indicator);
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                DockLeafView.this.host.tabPressed(DockLeafView.this.group, this.paneId);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                UiSounds.playClick();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) return true;
            return super.onTouchEvent(event);
        }
    }

    private static final class DockContentView extends FrameLayout {

        private boolean cutout;

        private DockContentView(Context context) {
            super(context);
            this.setWillNotDraw(false);
        }

        private void setCutout(boolean cutout) {
            if (this.cutout == cutout) return;

            this.cutout = cutout;
            this.invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (this.cutout) canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR);
            super.onDraw(canvas);
        }
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

    private final class HeaderLayout extends ViewGroup {

        private HeaderLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int contentWidth = Math.max(0, width - DockLeafView.this.controlWidth);
            DockLeafView.this.tabs.measure(atMost(contentWidth), exact(height));
            int tabsWidth = Math.min(contentWidth, DockLeafView.this.tabs.getMeasuredWidth());
            DockLeafView.this.trailingHeader.measure(exact(contentWidth - tabsWidth), exact(height));
            DockLeafView.this.maximize.measure(exact(Math.min(width, DockLeafView.this.controlWidth)), exact(height));
            DockLeafView.this.moveArea.measure(exact(width), exact(height));
            this.setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int controlLeft = Math.max(0, this.getWidth() - DockLeafView.this.controlWidth);
            int tabsRight = Math.min(controlLeft, DockLeafView.this.tabs.getMeasuredWidth());
            DockLeafView.this.moveArea.layout(0, 0, this.getWidth(), this.getHeight());
            DockLeafView.this.tabs.layout(0, 0, tabsRight, this.getHeight());
            DockLeafView.this.trailingHeader.layout(tabsRight, 0, controlLeft, this.getHeight());
            DockLeafView.this.maximize.layout(controlLeft, 0, this.getWidth(), this.getHeight());
        }
    }

    private record TabPresentation(String paneId, TabView tab, Text icon, Text title, TextView close) {
    }
}
