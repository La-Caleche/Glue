package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.dock.DockTags;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGeometry;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;

final class SplitterView extends View {

    private final DockHostView host;
    private final Paint paint = new Paint();
    private final DockThemeBinding theme;
    private DockGeometry.Splitter splitter;
    private int borderColor;
    private int borderWidth;
    private int hoverColor;
    private boolean hovered;

    SplitterView(Context context, DockHostView host) {
        super(context);
        this.host = host;
        this.setTag(DockTags.SPLITTER);
        this.setWillNotDraw(false);
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            this.borderColor = value.get(ThemeTokens.DOCK_BORDER);
            this.borderWidth = value.get(ThemeTokens.DOCK_METRICS).borderWidth();
            this.hoverColor = value.get(ThemeTokens.DOCK_DROP_HIGHLIGHT);
            this.invalidate();
        });
    }

    void setSplitter(DockGeometry.Splitter splitter) {
        this.splitter = splitter;
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
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.splitter == null) return;

        if (this.hovered) {
            this.paint.setColor(this.hoverColor);
            canvas.drawRect(0, 0, this.getWidth(), this.getHeight(), this.paint);
        } else if (this.borderWidth > 0) {
            this.paint.setColor(this.borderColor);
            if (this.splitter.split().axis() == DockAxis.HORIZONTAL) {
                float center = this.getWidth() / 2.0f;
                float halfWidth = this.borderWidth / 2.0f;
                canvas.drawRect(center - halfWidth, 0, center + halfWidth, this.getHeight(), this.paint);
            } else {
                float center = this.getHeight() / 2.0f;
                float halfWidth = this.borderWidth / 2.0f;
                canvas.drawRect(0, center - halfWidth, this.getWidth(), center + halfWidth, this.paint);
            }
        }
    }

    @Override
    public boolean onHoverEvent(@NonNull MotionEvent event) {
        boolean was = this.hovered;
        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) this.hovered = true;
        else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) this.hovered = false;
        if (was != this.hovered) this.invalidate();
        return super.onHoverEvent(event);
    }

    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        if (this.splitter == null) return super.onResolvePointerIcon(event);

        return this.splitter.split().axis() == DockAxis.HORIZONTAL
                ? Cursors.resizeHorizontal()
                : Cursors.resizeVertical();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && this.splitter != null) {
            this.host.splitterPressed(this.splitter);
            return true;
        }
        return super.onTouchEvent(event);
    }

}
