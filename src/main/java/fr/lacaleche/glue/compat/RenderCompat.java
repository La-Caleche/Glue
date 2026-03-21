package fr.lacaleche.glue.compat;

import net.fabricmc.loader.api.FabricLoader;

public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }
}
