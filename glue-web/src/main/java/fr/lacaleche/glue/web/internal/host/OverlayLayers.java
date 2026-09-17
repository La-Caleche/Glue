package fr.lacaleche.glue.web.internal.host;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Layers drawn above screens, menus and loading overlays, in registration order. */
public final class OverlayLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static final List<Consumer<GuiGraphics>> LAYERS = new ArrayList<>();

    private OverlayLayers() {
    }

    public static void add(Consumer<GuiGraphics> layer) {
        LAYERS.add(Objects.requireNonNull(layer, "layer"));
    }

    public static void render(Minecraft client, GuiRenderState renderState) {
        if (LAYERS.isEmpty()) return;
        GuiGraphics graphics = new GuiGraphics(client, renderState);
        graphics.nextStratum();
        Iterator<Consumer<GuiGraphics>> layers = LAYERS.iterator();
        while (layers.hasNext()) {
            try {
                layers.next().accept(graphics);
            } catch (RuntimeException exception) {
                LOGGER.error("Web overlay failed and was removed", exception);
                layers.remove();
            }
        }
    }
}
