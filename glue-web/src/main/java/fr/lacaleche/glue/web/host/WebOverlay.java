package fr.lacaleche.glue.web.host;

import fr.lacaleche.glue.web.WebBuilder;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.host.OverlayLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A passive page drawn above every screen, menu and loading overlay, in and out of worlds. It never
 * takes input and covers the whole GUI unless anchored. The page opens on the first frame after
 * the game has loaded and stays open until the overlay is disabled or the client stops. Transparent
 * idle pages cost no uploads. Register overlays during client initialization.
 */
public final class WebOverlay {

    private final LayerHost layer;

    private WebOverlay(LayerHost layer) {
        this.layer = layer;
    }

    public static Builder builder(URI address) {
        return new Builder(address);
    }

    public boolean isEnabled() {
        return this.layer.isEnabled();
    }

    /** Disabling closes the page; enabling opens a new one on the next frame. */
    public void setEnabled(boolean enabled) {
        this.layer.setEnabled(enabled);
    }

    public boolean isShowing() {
        return this.layer.isShowing();
    }

    public Optional<WebSurface> surface() {
        return this.layer.surface();
    }

    /** Sends an event to the page; false while it is closed or not connected. */
    public boolean emit(String event, Object data) {
        return this.layer.emit(event, data);
    }

    private void render(GuiGraphics graphics) {
        if (Minecraft.getInstance().isGameLoadFinished() && this.layer.prepare(graphics)) this.layer.draw(graphics);
    }

    public static final class Builder extends WebBuilder<Builder> {

        private LayerPlacement placement = LayerPlacement.FULL;
        private BooleanSupplier condition = () -> true;
        private boolean registered;

        private Builder(URI address) {
            super(address);
        }

        /** Sizes the page in GUI pixels and anchors it instead of covering the GUI. */
        public Builder anchor(WebAnchor anchor, int width, int height) {
            this.placement = new LayerPlacement(Objects.requireNonNull(anchor, "anchor"), width, height,
                    this.placement.offsetX(), this.placement.offsetY());
            return this;
        }

        /** Moves an anchored page, inward from its edges. */
        public Builder offset(int x, int y) {
            this.placement = this.placement.withOffset(x, y);
            return this;
        }

        /** Shows the page only while the condition holds; the page stays open meanwhile. */
        public Builder when(BooleanSupplier condition) {
            this.condition = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Adds the overlay above all GUI content; builders register once. */
        public WebOverlay register() {
            if (this.registered) throw new IllegalStateException("This overlay builder is already registered");
            this.registered = true;
            WebOverlay overlay = new WebOverlay(
                    new LayerHost(this.surfaceBuilder(), this.declaresBridge(), this.placement, this.condition));
            OverlayLayers.add(overlay::render);
            return overlay;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
