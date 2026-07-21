package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockRect;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

/**
 * The drag feedback layer, visible only mid tab/window drag: the tinted rect previewing where the
 * drop would land, the five-zone cross over the hovered leaf, and the four root-edge buttons.
 * Draw-only — it never takes input (the host routes the drag), so it can cover everything.
 */
final class DropOverlayView extends View {

    private static final int BUTTON = 34;
    private static final int CROSS_GAP = 44;
    private static final int EDGE_INSET = 12;

    private final Paint paint = new Paint();

    private boolean active;
    private DropTarget drop;
    private DockRect targetRect;
    private DockRect stage;
    private boolean treeEmpty;

    DropOverlayView(Context context) {
        super(context);
        setVisibility(GONE);
        // colours are read in onDraw; the binding only re-triggers it on theme change
        Themed.onTheme(this, theme -> {
        });
    }

    void update(DropTarget newDrop, DockRect newTargetRect, DockRect newStage, boolean newTreeEmpty) {
        active = true;
        drop = newDrop;
        targetRect = newTargetRect;
        stage = newStage;
        treeEmpty = newTreeEmpty;
        setVisibility(VISIBLE);
        invalidate();
    }

    void hide() {
        active = false;
        drop = null;
        targetRect = null;
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!active || stage == null) {
            return;
        }
        drawHighlight(canvas);
        if (drop != null && drop.kind() == DropTarget.Kind.LEAF && targetRect != null) {
            float cx = targetRect.x() + targetRect.w() / 2f;
            float cy = targetRect.y() + targetRect.h() / 2f;
            drawZoneButton(canvas, cx, cy, DropZone.CENTER, drop.zone() == DropZone.CENTER);
            drawZoneButton(canvas, cx - CROSS_GAP, cy, DropZone.LEFT, drop.zone() == DropZone.LEFT);
            drawZoneButton(canvas, cx + CROSS_GAP, cy, DropZone.RIGHT, drop.zone() == DropZone.RIGHT);
            drawZoneButton(canvas, cx, cy - CROSS_GAP, DropZone.TOP, drop.zone() == DropZone.TOP);
            drawZoneButton(canvas, cx, cy + CROSS_GAP, DropZone.BOTTOM, drop.zone() == DropZone.BOTTOM);
        }
        if (!treeEmpty) {
            drawRootEdges(canvas);
        }
    }

    private void drawHighlight(Canvas canvas) {
        DockRect rect = null;
        if (drop == null) {
            return;
        }
        if (drop.kind() == DropTarget.Kind.ROOT) {
            rect = treeEmpty ? stage : half(stage, drop.zone());
        } else if (targetRect != null) {
            rect = drop.zone() == DropZone.CENTER ? targetRect : half(targetRect, drop.zone());
        }
        if (rect == null) {
            return;
        }
        paint.setStyle(Paint.FILL);
        paint.setColor(Themes.active().color(Tokens.ACCENT_SUBTLE));
        canvas.drawRoundRect(rect.x(), rect.y(), rect.right(), rect.bottom(), 8, paint);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Themes.active().color(Tokens.ACCENT));
        canvas.drawRoundRect(rect.x(), rect.y(), rect.right(), rect.bottom(), 8, paint);
    }

    private static DockRect half(DockRect rect, DropZone zone) {
        return switch (zone) {
            case LEFT -> new DockRect(rect.x(), rect.y(), rect.w() / 2, rect.h());
            case RIGHT -> new DockRect(rect.x() + rect.w() / 2, rect.y(), rect.w() / 2, rect.h());
            case TOP -> new DockRect(rect.x(), rect.y(), rect.w(), rect.h() / 2);
            case BOTTOM -> new DockRect(rect.x(), rect.y() + rect.h() / 2, rect.w(), rect.h() / 2);
            case CENTER -> rect;
        };
    }

    private void drawRootEdges(Canvas canvas) {
        boolean root = drop != null && drop.kind() == DropTarget.Kind.ROOT;
        float midY = stage.y() + stage.h() / 2f;
        float midX = stage.x() + stage.w() / 2f;
        drawZoneButton(canvas, stage.x() + EDGE_INSET + BUTTON / 2f, midY,
                DropZone.LEFT, root && drop.zone() == DropZone.LEFT);
        drawZoneButton(canvas, stage.right() - EDGE_INSET - BUTTON / 2f, midY,
                DropZone.RIGHT, root && drop.zone() == DropZone.RIGHT);
        drawZoneButton(canvas, midX, stage.y() + EDGE_INSET + BUTTON / 2f,
                DropZone.TOP, root && drop.zone() == DropZone.TOP);
        drawZoneButton(canvas, midX, stage.bottom() - EDGE_INSET - BUTTON / 2f,
                DropZone.BOTTOM, root && drop.zone() == DropZone.BOTTOM);
    }

    /** A rounded 34px square with either a filled center marker or a directional chevron. */
    private void drawZoneButton(Canvas canvas, float cx, float cy, DropZone zone, boolean hot) {
        float h = BUTTON / 2f;
        paint.setStyle(Paint.FILL);
        paint.setColor(Themes.active().color(hot ? Tokens.ACCENT : Tokens.SURFACE_2));
        canvas.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 8, paint);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Themes.active().color(hot ? Tokens.ACCENT : Tokens.BORDER_STRONG));
        canvas.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 8, paint);

        int ink = Themes.active().color(hot ? Tokens.ACCENT_CONTRAST : Tokens.TEXT_MUTED);
        paint.setColor(ink);
        if (zone == DropZone.CENTER) {
            paint.setStyle(Paint.FILL);
            canvas.drawRoundRect(cx - 5.5f, cy - 5.5f, cx + 5.5f, cy + 5.5f, 3, paint);
            return;
        }
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.CAP_ROUND);
        paint.setStrokeJoin(Paint.JOIN_ROUND);
        float arm = 4.5f;
        float[] chevron = switch (zone) {
            case RIGHT -> new float[]{cx - 2, cy - arm - 1, cx + 3, cy, cx - 2, cy + arm + 1};
            case LEFT -> new float[]{cx + 2, cy - arm - 1, cx - 3, cy, cx + 2, cy + arm + 1};
            case TOP -> new float[]{cx - arm - 1, cy + 2, cx, cy - 3, cx + arm + 1, cy + 2};
            case BOTTOM -> new float[]{cx - arm - 1, cy - 2, cx, cy + 3, cx + arm + 1, cy - 2};
            default -> null;
        };
        if (chevron != null) {
            canvas.drawLines(chevron, 0, chevron.length, true, paint);
        }
    }
}
