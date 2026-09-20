package fr.lacaleche.glue.web.host;

import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.browser.BrowserSession;
import fr.lacaleche.glue.web.internal.host.HostSizing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** The lazily opened, non-interactive page shared by HUD and overlay layers. Client thread only. */
final class LayerHost {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");

    private final WebSurface.Builder page;
    private final boolean needsBridge;
    private final LayerPlacement placement;
    private final BooleanSupplier condition;
    private WebSurface surface;
    private ScreenRectangle bounds;
    private boolean enabled = true;
    private boolean failed;

    LayerHost(WebSurface.Builder page, boolean needsBridge, LayerPlacement placement, BooleanSupplier condition) {
        this.page = page;
        this.needsBridge = needsBridge;
        this.placement = placement;
        this.condition = condition;
    }

    /** Opens or fits the page for this frame; returns whether it should replace what it covers. */
    boolean prepare(GuiGraphics graphics) {
        if (!this.enabled || this.failed || BrowserSession.isRuntimeStopping()) return false;
        if (this.surface != null && this.surface.isClosed()) {
            LOGGER.warn("Web layer {} closed unexpectedly; vanilla content is shown instead: {}",
                    this.surface.url(), this.surface.error());
            this.failed = true;
            this.surface = null;
            return false;
        }

        this.bounds = this.placement.bounds(graphics.guiWidth(), graphics.guiHeight());
        if (this.surface == null) {
            HostSizing.Fit fit = HostSizing.fit(this.bounds.width(), this.bounds.height());
            this.surface = this.page.size(fit.width(), fit.height()).scale(fit.scale()).open();
        } else {
            HostSizing.apply(this.surface, this.bounds.width(), this.bounds.height());
        }
        return this.isShowing();
    }

    void draw(GuiGraphics graphics) {
        this.surface.draw(graphics, this.bounds.left(), this.bounds.top(), this.bounds.width(), this.bounds.height());
    }

    /**
     * Enabled, painted, connected when the page needs the bridge, and allowed by the condition. Until
     * then, replaced content keeps rendering.
     */
    boolean isShowing() {
        return this.enabled && !this.failed && this.surface != null && !this.surface.isClosed()
                && this.surface.hasFrame()
                && (!this.needsBridge || this.surface.isConnected())
                && this.condition.getAsBoolean();
    }

    boolean isEnabled() {
        return this.enabled;
    }

    void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) this.release();
    }

    /** Closes the page and clears a failure; the next prepared frame opens a new page. */
    void release() {
        if (this.surface != null && !this.surface.isClosed()) this.surface.close();
        this.surface = null;
        this.failed = false;
    }

    Optional<WebSurface> surface() {
        return Optional.ofNullable(this.surface);
    }

    boolean emit(String event, Object data) {
        return this.surface != null && !this.surface.isClosed() && this.surface.emit(event, data);
    }
}
