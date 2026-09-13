package fr.lacaleche.glue.mcsx.dock;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McsxDock implements ModInitializer {

    public static final String MOD_ID = "glue-mcsx-dock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MCSX Dock library is ready !");
    }
}
