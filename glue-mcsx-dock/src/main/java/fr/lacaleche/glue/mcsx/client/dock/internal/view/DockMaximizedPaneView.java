package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.UiSounds;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

final class DockMaximizedPaneView extends ViewGroup {

    private final DockHostView host;
    private final HeaderLayout header;
    private final LinearLayout identity;
    private final FrameLayout trailingHeader;
    private final TextView restore;
    private final TextView close;
    private final FrameLayout content;
    private final ColorDrawable background = new ColorDrawable();
    private final ColorDrawable headerBackground = new ColorDrawable();
    private final DockThemeBinding theme;
    private int headerHeight;
    private int identityTextSize;
    private int identityPadding;
    private int iconGap;
    private int controlWidth;
    private int identityColor;
    private Text identityIcon;
    private Text identityTitle;
    private DockPane pane;

    DockMaximizedPaneView(Context context, DockHostView host) {
        super(context);
        this.host = host;
        this.setFocusable(true);
        this.setVisibility(GONE);
        this.setBackground(this.background);
        this.header = new HeaderLayout(context);
        this.header.setBackground(this.headerBackground);
        this.identity = new LinearLayout(context);
        this.identity.setOrientation(LinearLayout.HORIZONTAL);
        this.identity.setGravity(Gravity.CENTER_VERTICAL);
        this.header.addView(this.identity);
        this.trailingHeader = new FrameLayout(context);
        this.header.addView(this.trailingHeader);
        this.restore = this.control("RESTORE", "dock-restore-pane");
        this.restore.setOnClickListener(ignored -> {
            UiSounds.playClick();
            this.host.restoreMaximizedPane();
        });
        this.header.addView(this.restore);
        this.close = this.control("x", "dock-close-maximized-pane");
        this.close.setOnClickListener(ignored -> {
            UiSounds.playClick();
            this.host.closePane(this.pane.id());
        });
        this.header.addView(this.close);
        this.content = new FrameLayout(context);
        this.addView(this.header);
        this.addView(this.content);
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            DockMetrics metrics = value.get(ThemeTokens.DOCK_METRICS);
            // A transparent pane keeps its hole while maximized: no background behind the content,
            // ordinary header chrome. show() assigns the pane before it re-applies the theme.
            this.background.setColor(this.pane != null && this.pane.transparent()
                    ? 0
                    : value.get(ThemeTokens.DOCK_PANE_BACKGROUND));
            this.headerBackground.setColor(value.get(ThemeTokens.DOCK_HEADER_BACKGROUND));
            int muted = value.get(ThemeTokens.TEXT_MUTED);
            this.identityColor = value.get(ThemeTokens.TEXT_PRIMARY);
            int controlTextSize = Math.max(1, metrics.controlTextSize());
            this.restore.setTextColor(muted);
            this.restore.setTextSize(controlTextSize);
            this.close.setTextColor(muted);
            this.close.setTextSize(controlTextSize);
            this.headerHeight = metrics.headerHeight();
            this.identityTextSize = Math.max(1, metrics.tabTextSize());
            this.identityPadding = metrics.tabPadding();
            this.iconGap = metrics.iconGap();
            this.controlWidth = metrics.controlWidth();
            this.restyleIdentity();
            this.requestLayout();
        });
    }

    void show(DockPane pane, View paneContent, View trailing) {
        this.pane = pane;
        this.identity.removeAllViews();
        this.identityIcon = pane.icon() == null ? null : new Text(this.getContext(), pane.icon());
        if (this.identityIcon != null) {
            this.identityIcon.setSingleLine(true);
            this.identityIcon.setGravity(Gravity.CENTER_VERTICAL);
            this.identity.addView(this.identityIcon, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT));
        }
        this.identityTitle = new Text(this.getContext(), pane.title());
        this.identityTitle.setSingleLine(true);
        this.identityTitle.setGravity(Gravity.CENTER_VERTICAL);
        this.identity.addView(this.identityTitle, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        mount(this.trailingHeader, trailing);
        mount(this.content, paneContent);
        this.close.setVisibility(pane.closable() ? VISIBLE : GONE);
        this.theme.applyCurrent();
        this.setVisibility(VISIBLE);
        this.requestLayout();
    }

    void hide() {
        this.content.removeAllViews();
        this.trailingHeader.removeAllViews();
        this.identity.removeAllViews();
        this.identityIcon = null;
        this.identityTitle = null;
        this.pane = null;
        this.setVisibility(GONE);
    }

    private void restyleIdentity() {
        this.identity.setPadding(this.identityPadding, 0, this.identityPadding, 0);
        if (this.identityIcon != null) {
            this.identityIcon.textSize(this.identityTextSize).color(this.identityColor);
        }
        if (this.identityTitle == null) return;

        this.identityTitle.textSize(this.identityTextSize).color(this.identityColor);
        ((LinearLayout.LayoutParams) this.identityTitle.getLayoutParams())
                .setMargins(this.identityIcon == null ? 0 : this.iconGap, 0, 0, 0);
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
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int measuredHeaderHeight = Math.min(this.headerHeight, height);
        this.header.measure(exact(width), exact(measuredHeaderHeight));
        this.content.measure(exact(width), exact(Math.max(0, height - measuredHeaderHeight)));
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int headerBottom = Math.min(this.headerHeight, this.getHeight());
        this.header.layout(0, 0, this.getWidth(), headerBottom);
        this.content.layout(0, headerBottom, this.getWidth(), this.getHeight());
    }

    private TextView control(String text, String tag) {
        TextView control = new TextView(this.getContext());
        control.setText(text);
        control.setGravity(Gravity.CENTER);
        control.setTag(tag);
        return control;
    }

    private static void mount(FrameLayout target, View view) {
        target.removeAllViews();
        if (view == null) return;
        if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        target.addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    private final class HeaderLayout extends ViewGroup {

        private int restoreWidth;
        private int closeWidth;

        private HeaderLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            this.restoreWidth = this.measureControl(DockMaximizedPaneView.this.restore, width, height);
            this.closeWidth = DockMaximizedPaneView.this.close.getVisibility() == VISIBLE
                    ? this.measureControl(DockMaximizedPaneView.this.close, width, height)
                    : 0;
            int available = Math.max(0, width - this.restoreWidth - this.closeWidth);
            DockMaximizedPaneView.this.identity.measure(atMost(available), exact(height));
            int identityWidth = Math.min(available, DockMaximizedPaneView.this.identity.getMeasuredWidth());
            DockMaximizedPaneView.this.trailingHeader.measure(exact(available - identityWidth), exact(height));
            this.setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int restoreLeft = Math.max(0, this.getWidth() - this.closeWidth - this.restoreWidth);
            int identityRight = Math.min(restoreLeft, DockMaximizedPaneView.this.identity.getMeasuredWidth());
            DockMaximizedPaneView.this.identity.layout(0, 0, identityRight, this.getHeight());
            DockMaximizedPaneView.this.trailingHeader.layout(identityRight, 0, restoreLeft, this.getHeight());
            DockMaximizedPaneView.this.restore.layout(restoreLeft, 0,
                    Math.min(this.getWidth(), restoreLeft + this.restoreWidth), this.getHeight());
            if (this.closeWidth > 0) {
                DockMaximizedPaneView.this.close.layout(this.getWidth() - this.closeWidth, 0,
                        this.getWidth(), this.getHeight());
            }
        }

        /** A themed control width is a minimum: a label such as RESTORE keeps its own width when wider. */
        private int measureControl(TextView control, int width, int height) {
            control.measure(atMost(width), exact(height));
            int preferred = Math.max(DockMaximizedPaneView.this.controlWidth, control.getMeasuredWidth());
            int constrained = Math.min(width, preferred);
            control.measure(exact(constrained), exact(height));
            return constrained;
        }
    }

    private static int atMost(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
    }
}
