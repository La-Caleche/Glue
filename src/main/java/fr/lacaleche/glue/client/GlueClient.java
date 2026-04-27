package fr.lacaleche.glue.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.debug.FboDebugHud;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.client.shader.internal.DeferredDrawQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class GlueClient implements ClientModInitializer {

    private static final KeyMapping FBO_DEBUG_KEY = new KeyMapping(
            "key.glue.fbo_debug_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.glue"
    );

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();
        GlueClientRegistries.bootstrap();

        DrawSelectionEvents.BLOCK.register(BlockRenderer::drawBlockOutline);
        ParticleManagerEvents.BLOCK_BREAK.register(BlockRenderer::getBreakParticleShape);

        DeferredDrawQueue.init();

        KeyBindingHelper.registerKeyBinding(FBO_DEBUG_KEY);

        RenderEvents.RENDER_HUD.register(guiGraphics -> {
            if (FboDebugHud.INSTANCE.isActive()) {
                FboDebugHud.INSTANCE.render(guiGraphics);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;
            while (FBO_DEBUG_KEY.consumeClick()) {
                FboDebugHud.INSTANCE.toggle();
            }
            FboDebugHud.INSTANCE.tick();
        });

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
