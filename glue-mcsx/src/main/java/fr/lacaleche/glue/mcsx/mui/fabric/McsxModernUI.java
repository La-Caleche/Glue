package fr.lacaleche.glue.mcsx.mui.fabric;

import fr.lacaleche.glue.mcsx.mui.ModernUIClient;
import fr.lacaleche.glue.mcsx.mui.ModernUIMod;
import fr.lacaleche.glue.mcsx.mui.ImageStore;
import fr.lacaleche.glue.mcsx.view.FontRegistry;
import icyllis.modernui.graphics.Image;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;

/**
 * Client entrypoint that boots the MCSX Modern UI host.
 * <p>
 * Replicates only the essential startup sequence of the upstream
 * {@code ModernUIFabricClient} (text engine, config, gui-scale option,
 * resource-reload listeners, music and key bindings were all stripped).
 * <p>
 * Extending {@link ModernUIClient} is load-bearing: the {@code ModernUI}
 * base constructor wires up {@code Resources.getSystem()} and registers this
 * instance as the application singleton, which the view system needs before
 * the UI thread's {@code init()} runs.
 */
public final class McsxModernUI extends ModernUIClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ModernUIMod.sDevelopment = true;
        }

        // Lets the view system resolve image resources through the MC resource manager.
        Image.setLegacyFactory(ImageStore.getInstance());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(FontRegistry.getInstance());

        // The render thread + Arc3D GL backend + UIManagerFabric.initialize() are
        // bootstrapped by MixinRenderSystem at RenderSystem.initRenderer (render
        // thread, GL ready). Here we only pre-warm the Arc3D immediate context once
        // the client has fully started (also on the render thread).
        ClientLifecycleEvents.CLIENT_STARTED.register((minecraft) ->
                UIManagerFabric.initializeRenderer());

        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "MCSX Modern UI host initialized");
    }
}
