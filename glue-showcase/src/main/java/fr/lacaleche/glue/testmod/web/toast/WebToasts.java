package fr.lacaleche.glue.testmod.web.toast;

import fr.lacaleche.glue.web.host.WebAnchor;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Toast notifications in a web overlay above every screen. Toasts raised before the page connects
 * wait in a short queue, because events are only delivered to a connected page.
 */
public final class WebToasts {

    private static final int QUEUE_LIMIT = 8;

    private final WebOverlay overlay;
    private final Deque<Toast> pending = new ArrayDeque<>();

    private WebToasts(WebOverlay overlay) {
        this.overlay = overlay;
    }

    public static WebToasts register(WebApp app) {
        WebOverlay overlay = WebOverlay.builder(app.page("toasts.html"))
                .anchor(WebAnchor.BOTTOM_RIGHT, 188, 150)
                .offset(4, 4)
                .register();
        WebToasts toasts = new WebToasts(overlay);
        ClientTickEvents.END_CLIENT_TICK.register(client -> toasts.flush());
        return toasts;
    }

    public WebOverlay overlay() {
        return this.overlay;
    }

    public void show(String title, String message) {
        if (!this.overlay.isEnabled()) return;
        if (this.pending.size() == QUEUE_LIMIT) this.pending.removeFirst();
        this.pending.addLast(new Toast(title, message));
        this.flush();
    }

    private void flush() {
        while (!this.pending.isEmpty() && this.overlay.emit("toast", this.pending.peekFirst())) {
            this.pending.removeFirst();
        }
    }

    record Toast(String title, String message) {
    }
}
