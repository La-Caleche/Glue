package fr.lacaleche.glue.client.events;

import fr.lacaleche.glue.consumer.QuadConsumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class RenderEvents {

    /** Material-capture teardown: closes the G-buffer window and restores blend state Lumos then reads. */
    public static final ResourceLocation PHASE_CAPTURE =
            ResourceLocation.fromNamespaceAndPath("glue", "capture");
    /** Deferred lighting: composites Lumos into the frame before any consumer post-processing sees it. */
    public static final ResourceLocation PHASE_LIGHTING =
            ResourceLocation.fromNamespaceAndPath("glue", "lighting");

    public static final Event<Consumer<GuiGraphics>> RENDER_HUD = EventFactory.createArrayBacked(
            Consumer.class,
            listeners -> guiContext -> {
                for (Consumer<GuiGraphics> listener : listeners) {
                    listener.accept(guiContext);
                }
            }
    );

    public static final Event<QuadConsumer<GuiGraphics, Float, Integer, Integer>> MAIN_RENDER = EventFactory.createArrayBacked(
            QuadConsumer.class,
            listeners -> (guiContext, tickDelta, screenWidth, screenHeight) -> {
                for (QuadConsumer<GuiGraphics, Float, Integer, Integer> listener : listeners) {
                    listener.accept(guiContext, tickDelta, screenWidth, screenHeight);
                }
            }
    );

    public static final Event<Runnable> POST_WORLD_RENDER = EventFactory.createArrayBacked(
            Runnable.class,
            listeners -> () -> {
                for (Runnable listener : listeners) {
                    listener.run();
                }
            }
    );

    static {
        // Registration order of this event is mod-init order, which is not guaranteed: a consumer that
        // depends on glue can still initialize before it. Post-processing effects register on the default
        // phase and MUST see the final lit frame, so pin the ordering explicitly rather than to luck:
        // capture teardown, then lighting, then everyone else (post effects included).
        POST_WORLD_RENDER.addPhaseOrdering(PHASE_CAPTURE, PHASE_LIGHTING);
        POST_WORLD_RENDER.addPhaseOrdering(PHASE_LIGHTING, Event.DEFAULT_PHASE);
    }
}
