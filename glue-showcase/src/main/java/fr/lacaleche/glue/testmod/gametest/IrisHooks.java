package fr.lacaleche.glue.testmod.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

/**
 * Thin guard around the Iris v0 API for test steps. Iris is a compileOnly dependency, so every
 * caller must check {@link #loaded()} before touching a method that resolves an Iris class.
 */
@Environment(EnvType.CLIENT)
final class IrisHooks {

    private IrisHooks() {
    }

    static boolean loaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    static boolean shaderPackInUse() {
        return loaded() && IrisApi.getInstance().isShaderPackInUse();
    }

    /** Applies immediately: Iris rebuilds its pipeline on the spot (can take seconds). */
    static void setShadersEnabled(boolean enabled) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
    }
}
