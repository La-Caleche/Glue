package fr.lacaleche.glue.client;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.internal.GlueOutlineRenderers;
import net.fabricmc.api.ClientModInitializer;

public class GlueClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();

        DrawSelectionEvents.BLOCK.register(BlockRenderer::drawBlockOutline);
        ParticleManagerEvents.BLOCK_BREAK.register(BlockRenderer::getBreakParticleShape);

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
