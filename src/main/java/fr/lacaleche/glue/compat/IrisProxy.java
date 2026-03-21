package fr.lacaleche.glue.compat;

import net.irisshaders.iris.api.v0.IrisApi;

class IrisProxy {
    private IrisProxy() {
    }

    public static boolean isIrisShaderEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
