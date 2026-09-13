package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

final class DockDragGhostView extends LinearLayout {

    private static final int CURSOR_OFFSET = 8;

    private final ShapeDrawable background = new ShapeDrawable();
    private final TextView icon;
    private final TextView title;
    private final DockThemeBinding theme;
    private int cursorOffset;
    private int dragX;
    private int dragY;

    DockDragGhostView(Context context, DockHostView host) {
        super(context);
        this.setOrientation(HORIZONTAL);
        this.setGravity(Gravity.CENTER_VERTICAL);
        this.setBackground(this.background);
        this.setVisibility(GONE);
        this.icon = new TextView(context);
        this.icon.setGravity(Gravity.CENTER);
        this.title = new TextView(context);
        this.title.setGravity(Gravity.CENTER_VERTICAL);
        this.addView(this.icon, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        this.addView(this.title, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            this.background.setColor(value.get(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND));
            this.background.setCornerRadius(value.get(ThemeTokens.SHELL_RADIUS));
            int textSize = Math.max(1, value.get(ThemeTokens.DOCK_METRICS).tabTextSize());
            this.icon.setTextColor(value.get(ThemeTokens.CONTROL_ON));
            this.icon.setTextSize(textSize);
            this.title.setTextColor(value.get(ThemeTokens.TEXT_PRIMARY));
            this.title.setTextSize(textSize);
            int horizontal = value.get(ThemeTokens.DOCK_METRICS).tabPadding();
            int iconGap = value.get(ThemeTokens.DOCK_METRICS).iconGap();
            this.cursorOffset = CURSOR_OFFSET;
            this.setPadding(horizontal, this.cursorOffset, horizontal, this.cursorOffset);
            ((LayoutParams) this.title.getLayoutParams()).setMargins(
                    this.icon.getVisibility() == VISIBLE ? iconGap : 0,
                    0,
                    0,
                    0
            );
        });
    }

    void show(DockPane pane, float x, float y) {
        this.title.setText(pane.title().getString());
        if (pane.icon() == null) {
            this.icon.setVisibility(GONE);
        } else {
            this.icon.setText(pane.icon().getString());
            this.icon.setVisibility(VISIBLE);
        }
        this.theme.applyCurrent();
        this.move(x, y);
        this.setVisibility(VISIBLE);
    }

    void move(float x, float y) {
        this.dragX = Math.round(x) + this.cursorOffset;
        this.dragY = Math.round(y) + this.cursorOffset;
        this.layoutAtDragPosition();
    }

    void layoutAtDragPosition() {
        this.layout(
                this.dragX,
                this.dragY,
                this.dragX + this.getMeasuredWidth(),
                this.dragY + this.getMeasuredHeight()
        );
    }

    void hide() {
        this.setVisibility(GONE);
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
}
