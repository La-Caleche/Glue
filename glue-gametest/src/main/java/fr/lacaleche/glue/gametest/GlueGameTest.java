package fr.lacaleche.glue.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint: arms a {@link GameTestRunner} when the launch names a test via
 * {@code -Dglue.gametest=<name>}. Without the property this module is inert.
 */
@Environment(EnvType.CLIENT)
public final class GlueGameTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String name = System.getProperty("glue.gametest");
        if (name == null || name.isEmpty()) {
            return;
        }
        GameTestRunner runner = new GameTestRunner(name);
        ClientTickEvents.END_CLIENT_TICK.register(runner::tick);
        TestContext.LOGGER.info("[gametest] armed: {}", name);
    }
}
