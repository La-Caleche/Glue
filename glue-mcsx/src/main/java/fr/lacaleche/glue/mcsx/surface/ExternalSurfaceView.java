package fr.lacaleche.glue.mcsx.surface;

import fr.lacaleche.glue.mcsx.cursor.Cursors;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;

/**
 * A View whose area is filled each frame by an externally-rendered texture (e.g. a
 * Blaze3D FBO) rather than by Modern UI drawing. It reserves layout space, publishes its
 * window-space rect for the host to blit into (see {@link ExternalSurfaceHost}) and routes
 * pointer gestures to an optional {@link SurfaceGestureListener}.
 *
 * <p>The blit is performed by the host on the render thread, layered <em>below</em> the UI: this
 * view punches a transparent hole over its own rect, the texture shows through it, and anything
 * the UI paints on top — a menu, a dialog, an absolutely-positioned overlay — stays visible.
 */
public class ExternalSurfaceView extends View {

    private final SurfaceSource source;
    @Nullable
    private SurfaceGestureListener gestureListener;

    private final int[] location = new int[2];
    private final Paint holePaint = new Paint();
    private boolean dragging;

    public ExternalSurfaceView(@NonNull Context context, @NonNull SurfaceSource source) {
        super(context);
        this.source = source;
        setClickable(true);
        setWillNotDraw(false);
        holePaint.setBlendMode(BlendMode.CLEAR);
    }

    public void setGestureListener(@Nullable SurfaceGestureListener listener) {
        this.gestureListener = listener;
    }

    SurfaceSource source() {
        return source;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ExternalSurfaceHost.getInstance().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        ExternalSurfaceHost.getInstance().unregister(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        publishBounds();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        publishBounds();
        // Punch a transparent hole in the UI layer so the externally-blitted surface (drawn
        // below the UI) shows through, while dialogs and menus painted over this view stay
        // visible on top.
        canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(), 0f, holePaint);
    }

    private void publishBounds() {
        getLocationInWindow(location);
        ExternalSurfaceHost.getInstance().updateBounds(this, location[0], location[1], getWidth(), getHeight());
    }

    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        return Cursors.move();
    }

    @Override
    public boolean onGenericMotionEvent(@NonNull MotionEvent event) {
        if (gestureListener != null && event.getAction() == MotionEvent.ACTION_SCROLL) {
            return gestureListener.onSurfaceScroll(
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL), event.getX(), event.getY());
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onHoverEvent(@NonNull MotionEvent event) {
        if (gestureListener != null) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                        gestureListener.onSurfaceHover(event.getX(), event.getY());
                case MotionEvent.ACTION_HOVER_EXIT -> gestureListener.onSurfaceHoverExit();
                default -> { }
            }
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (gestureListener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                int button = event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                        ? MotionEvent.BUTTON_SECONDARY : MotionEvent.BUTTON_PRIMARY;
                dragging = gestureListener.onSurfaceDown(event.getX(), event.getY(), button);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    gestureListener.onSurfaceMove(event.getX(), event.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    gestureListener.onSurfaceUp();
                    dragging = false;
                }
                return true;
            }
            default -> {
                return super.onTouchEvent(event);
            }
        }
    }
}
