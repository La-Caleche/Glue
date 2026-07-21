package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The z-layer every overlay renders into: dialogs, popovers, menus, tooltips and toasts. It sits
 * above the screen's content and fills it, so a panel can be centred in the window or anchored to a
 * trigger regardless of where that trigger lives in the flow.
 *
 * <p>Each open overlay is one child layer holding a scrim and a panel. The scrim is always present —
 * transparent for a non-modal overlay — because it is what makes an outside click dismiss a menu.
 * Layers stack in open order, so the last one opened is the one {@code Esc} dismisses.
 */
public final class OverlayHost extends FrameLayout {

    /**
     * One open overlay: the full-size layer view, how to tell its owner it was dismissed, and the
     * effect repainting its scrim ({@code null} when the layer is not modal).
     */
    private record Layer(Object key, View view, Runnable onDismiss, Effect scrimPaint) {
    }

    private final List<Layer> layers = new ArrayList<>();
    private final KeyBindings keyBindings;

    public OverlayHost(Context context) {
        super(context);
        keyBindings = new KeyBindings(this);
        // The host must not swallow clicks meant for the content when nothing is open.
        setVisibility(GONE);
    }

    /** Workspace-wide shortcuts shared by every document rendering into this host. */
    public KeyBindings keyBindings() {
        return keyBindings;
    }

    /**
     * Shows {@code panel}, replacing any overlay previously opened under {@code key}.
     *
     * @param modal      paints the scrim and blocks clicks to the content beneath
     * @param onDismiss  invoked when the scrim is clicked or {@code Esc} closes this layer; the
     *                   owner is expected to write its {@code open} signal false, which closes it
     */
    public void open(Object key, View panel, FrameLayout.LayoutParams panelParams,
                     boolean modal, Runnable onDismiss) {
        close(key);

        FrameLayout layer = new FrameLayout(getContext());
        View scrim = new View(getContext());
        // The scrim's colour is a theme token, so it has to be repainted on a theme switch — reading
        // it once here froze it. open() is called from inside the <overlay> gate's untracked rebuild,
        // which never re-runs on a theme write; an effect created here pushes its own observer above
        // that frame and so tracks the theme signal itself. It lives as long as the layer does.
        Effect scrimPaint = modal
                ? Effect.of(() -> scrim.setBackground(
                        new ColorDrawable(Themes.active().color(Tokens.SCRIM))))
                : null;
        scrim.setClickable(true);
        if (onDismiss != null) {
            scrim.setOnClickListener(v -> onDismiss.run());
        }
        layer.addView(scrim, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        layer.addView(panel, panelParams);

        addView(layer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        layers.add(new Layer(key, layer, onDismiss, scrimPaint));
        setVisibility(VISIBLE);
        Animations.playEnter(panel);
    }

    public void close(Object key) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.key().equals(key)) {
                if (layer.scrimPaint() != null) {
                    layer.scrimPaint().dispose();
                }
                removeView(layer.view());
                layers.remove(i);
            }
        }
        if (layers.isEmpty()) {
            setVisibility(GONE);
        }
    }

    /**
     * A detached host is a torn-down workspace, so its layers close with it. Without this, a modal
     * layer whose owner never called {@link #close} would be retained forever: its scrim effect
     * subscribes to the global theme signal, which holds the effect, the scrim, the layer and the
     * whole screen behind it.
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        while (!layers.isEmpty()) {
            close(layers.get(layers.size() - 1).key());
        }
    }

    /** Dismisses the topmost overlay, if any. Returns true when a key press was consumed. */
    public boolean dismissTop() {
        if (layers.isEmpty()) {
            return false;
        }
        Layer top = layers.get(layers.size() - 1);
        if (top.onDismiss() != null) {
            top.onDismiss().run();
        } else {
            close(top.key());
        }
        return true;
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    /**
     * Positions {@code params} so the panel hangs below {@code anchor}, left edges aligned — the
     * dropdown-menu placement. Both views are located in window space, so this works no matter how
     * deeply the trigger is nested. An anchor that has not been laid out yet reports the origin, and
     * the panel simply lands top-left rather than misplacing itself off-screen.
     */
    public void anchorBelow(FrameLayout.LayoutParams params, View anchor) {
        int[] anchorAt = new int[2];
        int[] hostAt = new int[2];
        anchor.getLocationInWindow(anchorAt);
        getLocationInWindow(hostAt);
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.max(0, anchorAt[0] - hostAt[0]);
        params.topMargin = Math.max(0, anchorAt[1] - hostAt[1] + anchor.getHeight());
    }
}
