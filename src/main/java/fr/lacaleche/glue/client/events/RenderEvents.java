package fr.lacaleche.glue.client.events;

import fr.lacaleche.glue.consumer.QuadConsumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

public class RenderEvents {

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
}
