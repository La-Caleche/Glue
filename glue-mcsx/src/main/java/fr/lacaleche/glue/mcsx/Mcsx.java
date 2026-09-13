package fr.lacaleche.glue.mcsx;

import fr.lacaleche.glue.mcsx.client.style.McsxStylesheetLoader;
import fr.lacaleche.glue.mcsx.client.theme.McsxThemeLoader;
import fr.lacaleche.glue.mcsx.client.internal.CursorRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mcsx implements ModInitializer {

    public static final String MOD_ID = "mcsx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CursorRegistry.initialize());
        ClientLifecycleEvents.CLIENT_STOPPING.register(CursorRegistry::close);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new McsxThemeLoader()
        );
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new McsxStylesheetLoader()
        );
        LOGGER.info("MCSX library is ready !");
    }

}
