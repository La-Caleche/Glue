package fr.lacaleche.glue;

import fr.lacaleche.glue.internal.GlueComponentTypes;
import fr.lacaleche.glue.internal.GlueRegistries;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Glue implements ModInitializer {

    public static final String MOD_ID = "glue";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        GlueComponentTypes.registerComponentTypes();
        GlueRegistries.bootstrap();

        LOGGER.info("Glue library is ready !");
    }

}