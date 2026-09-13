package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGuides;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DropTarget;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

import java.util.List;

/**
 * Paints the drop preview, the guide controls and the tab insertion caret during a drag. Every
 * rectangle comes from {@link DockGuides}, which is also what resolves drops, so what this view
 * shows is exactly what a release performs.
 */
final class DockDropOverlayView extends View {

    private final Paint paint = new Paint();
    private final DockThemeBinding theme;
    private List<DockGuides.Guide> guides = List.of();
    private DropTarget target;
    private DockRect preview;
    private DockRect caret;
    private boolean themed;
    private int highlightColor;
    private int borderColor;
    private int onColor;
    private int headerColor;
    private int backgroundColor;
    private int mutedColor;
    private float cornerRadius;
    private float strokeWidth;

    DockDropOverlayView(Context context, DockHostView host) {
        super(context);
        this.setVisibility(GONE);
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            this.highlightColor = value.get(ThemeTokens.DOCK_DROP_HIGHLIGHT);
            this.borderColor = value.get(ThemeTokens.DOCK_BORDER);
            this.onColor = value.get(ThemeTokens.CONTROL_ON);
            this.headerColor = value.get(ThemeTokens.DOCK_HEADER_BACKGROUND);
            this.backgroundColor = value.get(ThemeTokens.DOCK_BACKGROUND);
            this.mutedColor = value.get(ThemeTokens.TEXT_MUTED);
            this.cornerRadius = value.get(ThemeTokens.DOCK_METRICS).cornerRadius();
            this.strokeWidth = value.get(ThemeTokens.DOCK_METRICS).borderWidth();
            this.themed = true;
            this.invalidate();
        });
    }

    void show(List<DockGuides.Guide> guides, DropTarget target, DockRect preview, DockRect caret) {
        this.guides = guides;
        this.target = target;
        this.preview = preview;
        this.caret = caret;
        this.setVisibility(VISIBLE);
        this.invalidate();
    }

    void hide() {
        this.guides = List.of();
        this.target = null;
        this.preview = null;
        this.caret = null;
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

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!this.themed) return;

        this.drawPreview(canvas);
        for (DockGuides.Guide guide : this.guides) {
            this.drawGuide(canvas, guide, guide.target().equals(this.target));
        }
        if (this.caret != null) {
            this.paint.setStyle(Paint.FILL);
            this.paint.setColor(this.onColor);
            canvas.drawRect(this.caret.x(), this.caret.y(), this.caret.right(), this.caret.bottom(), this.paint);
        }
    }

    private void drawPreview(Canvas canvas) {
        if (this.preview == null) return;

        this.paint.setStyle(Paint.FILL);
        this.paint.setColor(this.highlightColor);
        canvas.drawRoundRect(this.preview.x(), this.preview.y(), this.preview.right(), this.preview.bottom(),
                this.cornerRadius, this.paint);
        this.drawOptionalStroke(
                canvas,
                this.preview.x(),
                this.preview.y(),
                this.preview.right(),
                this.preview.bottom(),
                this.cornerRadius,
                this.borderColor
        );
    }

    private void drawGuide(Canvas canvas, DockGuides.Guide guide, boolean hot) {
        DockRect rect = guide.rect();
        float centerX = rect.x() + rect.width() / 2.0f;
        float centerY = rect.y() + rect.height() / 2.0f;
        float radius = Math.min(6, this.cornerRadius);
        this.paint.setStyle(Paint.FILL);
        this.paint.setColor(hot ? this.onColor : this.headerColor);
        canvas.drawRoundRect(rect.x(), rect.y(), rect.right(), rect.bottom(), radius, this.paint);
        this.drawOptionalStroke(
                canvas,
                rect.x(),
                rect.y(),
                rect.right(),
                rect.bottom(),
                radius,
                hot ? this.onColor : this.borderColor
        );

        this.paint.setColor(hot ? this.backgroundColor : this.mutedColor);
        this.paint.setStyle(Paint.FILL);
        DropTarget.Zone zone = guide.target().zone();
        if (zone == DropTarget.Zone.CENTER) {
            canvas.drawRoundRect(centerX - 5, centerY - 5, centerX + 5, centerY + 5, 2, this.paint);
            return;
        }
        float arm = 5;
        float thickness = 2;
        if (zone == DropTarget.Zone.LEFT || zone == DropTarget.Zone.RIGHT) {
            float direction = zone == DropTarget.Zone.LEFT ? -1 : 1;
            canvas.drawRect(centerX - thickness / 2, centerY - arm, centerX + thickness / 2,
                    centerY + arm, this.paint);
            float end = centerX + direction * arm;
            canvas.drawRect(Math.min(centerX, end), centerY - thickness / 2, Math.max(centerX, end),
                    centerY + thickness / 2, this.paint);
        } else {
            float direction = zone == DropTarget.Zone.TOP ? -1 : 1;
            canvas.drawRect(centerX - arm, centerY - thickness / 2, centerX + arm,
                    centerY + thickness / 2, this.paint);
            float end = centerY + direction * arm;
            canvas.drawRect(centerX - thickness / 2, Math.min(centerY, end), centerX + thickness / 2,
                    Math.max(centerY, end), this.paint);
        }
    }

    private void drawOptionalStroke(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int color
    ) {
        if (this.strokeWidth <= 0) return;

        this.paint.setStyle(Paint.STROKE);
        this.paint.setStrokeWidth(this.strokeWidth);
        this.paint.setColor(color);
        canvas.drawRoundRect(left, top, right, bottom, radius, this.paint);
        this.paint.setStyle(Paint.FILL);
    }
}
