package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.mcsx.client.GameFocus;
import fr.lacaleche.glue.mcsx.client.reactive.ClientMirror;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Hands the player to the Studio's viewport pane while the workspace stays open.
 *
 * <p>There is no input emulation here. Once focus is handed over, vanilla reads its own bindings,
 * runs {@code Minecraft#handleKeybinds} and turns the player from the grabbed cursor — the game never
 * paused, it just was not reading input while the workspace held it. All this pane does is say who
 * currently owns the input, and mirror that into a {@link Value} the UI can bind to.</p>
 *
 * <p>{@link GameFocus} takes a request from any thread and applies it on the client thread, so
 * neither hand-off marshals anything itself. Neither one publishes its own outcome either: focus is
 * also dropped from outside this class — Escape releases it, and so does closing the workspace — so
 * the mirror is fed from the authoritative state on every client tick instead.</p>
 */
@Environment(EnvType.CLIENT)
final class ViewportController {

    private final ClientMirror<Boolean> captured = ClientMirror.of(false);
    private boolean initialized;

    Value<Boolean> captured() {
        return this.captured.value();
    }

    void init() {
        if (this.initialized) return;

        this.initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> this.captured.set(GameFocus.isHeld()));
    }

    /** Called from the UI thread when the viewport pane is clicked. */
    void capture() {
        GameFocus.request();
    }

    void release() {
        GameFocus.release();
    }
}
