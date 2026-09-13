package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.GameSurface;
import fr.lacaleche.glue.mcsx.client.component.ReactiveView;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;

/**
 * The viewport: a pane that paints almost nothing, so the world rendering behind the workspace shows
 * through it. Clicking captures the player; Escape releases while the viewport owns game focus.
 */
final class ViewportPaneView extends ReactiveView {

    private static final float FRAME_INSET = 0.5f;

    private final Paint paint = new Paint();
    private final Value<Theme> theme;
    private final StudioSession session;
    private final Object workspace;
    private final ViewportController controller;
    private final ViewTreeObserver.OnPreDrawListener boundsPublisher = () -> {
        this.publishBounds();
        return true;
    };
    private GameViewport.Bounds submittedBounds;

    ViewportPaneView(Context context, Value<Theme> theme, StudioSession session, Object workspace) {
        super(context);
        this.theme = theme;
        this.session = session;
        this.workspace = workspace;
        this.controller = session.viewport();
        this.invalidateOn(theme, this.controller.captured());
        this.setFocusable(true);
        // ModernUI's own click detection discriminates a tap from a drag that merely ends here, which
        // a raw ACTION_UP handler would not.
        this.setOnClickListener(ignored -> {
            this.requestFocus();
            this.controller.capture();
        });
        Cursors.set(this, Cursors.crosshair());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.submittedBounds = null;
        this.getViewTreeObserver().addOnPreDrawListener(this.boundsPublisher);
        this.invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.getViewTreeObserver().removeOnPreDrawListener(this.boundsPublisher);
        super.onDetachedFromWindow();
        // Dock mutations retain pane identity by reparenting the same View. Let the matching attach
        // complete before treating this as a real removal; workspace unmount clears synchronously.
        Core.postOnUiThread(() -> {
            if (!this.isAttachedToWindow()) this.clearBounds();
        });
    }

    /**
     * The pane stops drawing when it is behind another tab or another pane is maximized, but the world
     * would go on rendering into the rectangle it last claimed — now covered by opaque content. Drop
     * the claim as soon as the pane is no longer showing.
     */
    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) this.clearBounds();
    }

    /**
     * ModernUI lays this View out in framebuffer pixels, which is exactly the space
     * {@link GameViewport} wants, so the pane's own rectangle is the world's rectangle.
     */
    private void publishBounds() {
        int width = this.getWidth();
        int height = this.getHeight();
        if (width <= 0 || height <= 0 || !this.isShown()) {
            this.clearBounds();
            return;
        }

        int[] location = new int[2];
        this.getLocationInWindow(location);
        GameViewport.Bounds next = new GameViewport.Bounds(location[0], location[1], width, height);
        if (next.equals(this.submittedBounds)) return;

        this.submittedBounds = next;
        this.session.publishWorldClaims(
                this.workspace,
                this,
                next,
                new GameSurface.Bounds(location[0], location[1], width, height)
        );
    }

    /**
     * Drops the world and input claims. Reached from three owners — pre-draw republishing, the
     * detach/visibility fallback, and the workspace's synchronous unmount — and idempotent so any
     * order of those is fine.
     */
    private void clearBounds() {
        this.submittedBounds = null;
        this.session.clearWorldClaims(this.workspace, this);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode != KeyEvent.KEY_ESCAPE || !this.controller.captured().get()) {
            return super.onKeyDown(keyCode, event);
        }

        this.controller.release();
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Theme active = this.theme.get();
        float width = this.getWidth();
        float height = this.getHeight();
        boolean capturing = this.controller.captured().get();
        int onColor = active.get(ThemeTokens.CONTROL_ON);

        this.paint.setAntiAlias(true);
        this.paint.setStyle(Paint.STROKE);
        this.paint.setStrokeWidth(2.0f);
        this.paint.setColor(ThemeTokens.withAlpha(onColor, capturing ? 102 : 28));
        canvas.drawRoundRect(
                FRAME_INSET,
                FRAME_INSET,
                width - FRAME_INSET,
                height - FRAME_INSET,
                active.get(ThemeTokens.CORNER_RADIUS),
                this.paint
        );

    }

}
