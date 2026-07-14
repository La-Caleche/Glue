package fr.lacaleche.glue.client.render.light;

import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.render.light.internal.pipeline.LightRenderCoordinator;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/** Public configuration and lifecycle facade for the deferred light renderer. */
@Environment(EnvType.CLIENT)
public final class LightRenderer {

    private static final LightRenderCoordinator COORDINATOR = new LightRenderCoordinator();
    private static boolean initialized;

    private LightRenderer() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ShadowPipelines.init();
        RenderEvents.POST_WORLD_RENDER.register(COORDINATOR::render);
    }

    /** Caps resident shadow maps. Lights past the budget still illuminate without shadows. */
    public static void setShadowBudget(int spotShadows, int pointShadows) {
        COORDINATOR.setShadowBudget(spotShadows, pointShadows);
    }

    /** Limits expensive new shadow-map renders while preserving cached maps. */
    public static void setShadowUpdateBudget(int spotBakesPerFrame, int pointBakesPerFrame) {
        COORDINATOR.setShadowUpdateBudget(spotBakesPerFrame, pointBakesPerFrame);
    }

    /** Sets the light cull distance; {@code <= 0} follows Minecraft's render distance. */
    public static void setMaxLightDistance(double blocks) {
        COORDINATOR.setMaxLightDistance(blocks);
    }

    public static void onBlockChanged(BlockPos position) {
        if (initialized) COORDINATOR.onBlockChanged(position);
    }

    public static void cleanup() {
        COORDINATOR.cleanup();
    }

    static void configureWorld(WorldLightContext context) {
        COORDINATOR.configureWorld(context);
    }

    static void reloadResources() {
        COORDINATOR.reloadResources();
    }
}
