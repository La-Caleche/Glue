package fr.lacaleche.glue.client.events;

import fr.lacaleche.glue.consumer.QuadConsumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Defines custom render events for Glue.
 */
public class RenderEvents {

    /**
     * Event triggered to render custom HUD elements.
     */
    public static final Event<Consumer<GuiGraphics>> RENDER_HUD = EventFactory.createArrayBacked(
            Consumer.class,
            listeners -> guiContext -> {
                for (Consumer<GuiGraphics> listener : listeners) {
                    listener.accept(guiContext);
                }
            }
    );

    /**
     * Event triggered during the main render cycle.
     * Allows adding custom rendering logic with access to tickDelta and screen dimensions.
     */
    public static final Event<QuadConsumer<GuiGraphics, Float, Integer, Integer>> MAIN_RENDER = EventFactory.createArrayBacked(
            QuadConsumer.class,
            listeners -> (guiContext, tickDelta, screenWidth, screenHeight) -> {
                for (QuadConsumer<GuiGraphics, Float, Integer, Integer> listener : listeners) {
                    listener.accept(guiContext, tickDelta, screenWidth, screenHeight);
                }
            }
    );

    /**
     * Event fired AFTER Glue has blitted custom shader output to the main framebuffer,
     * but BEFORE HUD/GUI rendering. This is the correct place to apply post-processing
     * effects (blur, grayscale, etc.) so they affect both vanilla and custom shader content.
     *
     * <p>Timing (with Iris): fires after Iris composite + Glue blit, before depth clear.</p>
     * <p>Timing (vanilla): fires at WorldRenderEvents.LAST, after Glue blit.</p>
     */
    public static final Event<Runnable> POST_WORLD_RENDER = EventFactory.createArrayBacked(
            Runnable.class,
            listeners -> () -> {
                for (Runnable listener : listeners) {
                    listener.run();
                }
            }
    );
}

