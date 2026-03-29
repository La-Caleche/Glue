package fr.lacaleche.glue.client;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.shader.DeferredDrawQueue;
import net.fabricmc.api.ClientModInitializer;

public class GlueClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();
        GlueClientRegistries.bootstrap();

        DrawSelectionEvents.BLOCK.register(BlockRenderer::drawBlockOutline);
        ParticleManagerEvents.BLOCK_BREAK.register(BlockRenderer::getBreakParticleShape);

        // Register deferred draw queue for raw GL rendering (Iris compatibility)
        DeferredDrawQueue.init();

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
