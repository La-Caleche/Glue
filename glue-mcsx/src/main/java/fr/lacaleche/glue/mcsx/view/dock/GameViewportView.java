package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

/**
 * The pane the game lives in. It draws nothing of its own — it punches a transparent hole
 * through the UI layer (the {@code ExternalSurfaceView} pattern) so the game, blitted into this
 * rect by the embedding present, shows through — and it publishes its window-space bounds to
 * {@link ViewportEmbedding} every layout and draw, which is what drives the framebuffer pinning.
 * A press inside it hands input to the game per the configured {@link ViewportInput.Mode}.
 */
public final class GameViewportView extends FrameLayout
        implements fr.lacaleche.glue.mcsx.viewport.GamePassthrough {

    private final int[] location = new int[2];
    private final Paint holePaint = new Paint();

    public GameViewportView(Context context) {
        super(context);
        setClickable(true);
        setWillNotDraw(false);
        holePaint.setBlendMode(BlendMode.CLEAR);

        TextView caption = new TextView(context);
        caption.setText("click to play · esc to release");
        caption.setTextSize(11f);
        Themed.onTheme(caption, theme -> caption.setTextColor(theme.color(Tokens.TEXT_SUBTLE)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        params.bottomMargin = 8;
        addView(caption, params);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        publishBounds();
    }

    @Override
    protected void onDraw(@icyllis.modernui.annotation.NonNull Canvas canvas) {
        publishBounds();
        canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(), 0f, holePaint);
    }

    private void publishBounds() {
        getLocationInWindow(location);
        ViewportEmbedding.setPaneBounds(location[0], location[1], getWidth(), getHeight());
    }

    @Override
    public boolean onTouchEvent(@icyllis.modernui.annotation.NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int button = event.isButtonPressed(MotionEvent.BUTTON_SECONDARY) ? 1 : 0;
            ViewportInput.onViewportPressed(button);
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** The caption only makes sense while the dock owns the pointer. */
    @Override
    public void onDrawForeground(@icyllis.modernui.annotation.NonNull Canvas canvas) {
        View caption = getChildAt(0);
        int wanted = ViewportInput.dockOwnsPointer() ? VISIBLE : INVISIBLE;
        if (caption.getVisibility() != wanted) {
            caption.setVisibility(wanted);
        }
        super.onDrawForeground(canvas);
    }
}
