package fr.lacaleche.glue.web.host;

import fr.lacaleche.glue.web.WebBuilder;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.host.HostTicker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A passive page in the gameplay HUD. It never takes input, and it covers the whole GUI unless
 * anchored. The page opens on the first HUD frame in a world and closes when the world is left.
 *
 * <p>A HUD can replace vanilla elements: the first replaced element hosts the page and the others are
 * hidden. Vanilla rendering continues until the page has painted and, when it declares actions,
 * state or slots, connected its bridge; it also returns while the HUD is disabled, its condition is
 * false, or its page failed. Otherwise the HUD is attached before or after a vanilla element, or
 * drawn last. Register HUDs during client initialization.</p>
 */
public final class WebHud {

    private final ResourceLocation id;
    private final LayerHost layer;

    private WebHud(ResourceLocation id, LayerHost layer) {
        this.id = id;
        this.layer = layer;
    }

    public static Builder builder(ResourceLocation id, URI address) {
        return new Builder(id, address);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public boolean isEnabled() {
        return this.layer.isEnabled();
    }

    /** Disabling closes the page and restores replaced vanilla elements. */
    public void setEnabled(boolean enabled) {
        this.layer.setEnabled(enabled);
    }

    /** Whether the page is currently drawn instead of what it replaces. */
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

    private boolean prepareAndDraw(GuiGraphics graphics) {
        if (Minecraft.getInstance().level == null || !this.layer.prepare(graphics)) return false;
        this.layer.draw(graphics);
        return true;
    }

    private boolean tick() {
        if (Minecraft.getInstance().level == null) this.layer.release();
        return true;
    }

    public static final class Builder extends WebBuilder<Builder> {

        private final ResourceLocation id;
        private final List<ResourceLocation> replaced = new ArrayList<>();
        private ResourceLocation after;
        private ResourceLocation before;
        private LayerPlacement placement = LayerPlacement.FULL;
        private BooleanSupplier condition = () -> true;
        private boolean registered;

        private Builder(ResourceLocation id, URI address) {
            super(address);
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Replaces vanilla HUD elements, such as {@code VanillaHudElements.HOTBAR}; the first hosts the page. */
        public Builder replaces(ResourceLocation... elements) {
            if (elements.length == 0) throw new IllegalArgumentException("Name at least one element to replace");
            for (ResourceLocation element : elements) {
                Objects.requireNonNull(element, "element");
                if (this.replaced.contains(element)) throw new IllegalArgumentException("Element is replaced twice: " + element);
                this.replaced.add(element);
            }
            return this;
        }

        /** Draws the page right after a vanilla element and inherits its render condition. */
        public Builder after(ResourceLocation element) {
            this.after = Objects.requireNonNull(element, "element");
            return this;
        }

        /** Draws the page right before a vanilla element and inherits its render condition. */
        public Builder before(ResourceLocation element) {
            this.before = Objects.requireNonNull(element, "element");
            return this;
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

        /** Registers the HUD with Fabric's HUD registry; builders register once. */
        public WebHud register() {
            if (this.registered) throw new IllegalStateException("This HUD builder is already registered");
            int placements = (this.replaced.isEmpty() ? 0 : 1) + (this.after == null ? 0 : 1) + (this.before == null ? 0 : 1);
            if (placements > 1) throw new IllegalStateException("Choose one of replaces, after or before");
            this.registered = true;

            WebHud hud = new WebHud(this.id,
                    new LayerHost(this.surfaceBuilder(), this.declaresBridge(), this.placement, this.condition));
            if (!this.replaced.isEmpty()) {
                HudElementRegistry.replaceElement(this.replaced.getFirst(), vanilla -> (graphics, delta) -> {
                    if (!hud.prepareAndDraw(graphics)) vanilla.render(graphics, delta);
                });
                for (ResourceLocation element : this.replaced.subList(1, this.replaced.size())) {
                    HudElementRegistry.replaceElement(element, vanilla -> (graphics, delta) -> {
                        if (!hud.isShowing()) vanilla.render(graphics, delta);
                    });
                }
            } else {
                HudElement element = (graphics, delta) -> {
                    if (!Minecraft.getInstance().options.hideGui) hud.prepareAndDraw(graphics);
                };
                if (this.after != null) HudElementRegistry.attachElementAfter(this.after, this.id, element);
                else if (this.before != null) HudElementRegistry.attachElementBefore(this.before, this.id, element);
                else HudElementRegistry.addLast(this.id, element);
            }
            HostTicker.track(hud::tick);
            return hud;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
