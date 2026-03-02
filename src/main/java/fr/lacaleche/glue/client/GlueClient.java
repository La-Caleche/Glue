package fr.lacaleche.glue.client;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.internal.GlueOutlineRenderers;
import net.fabricmc.api.ClientModInitializer;

public class GlueClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
