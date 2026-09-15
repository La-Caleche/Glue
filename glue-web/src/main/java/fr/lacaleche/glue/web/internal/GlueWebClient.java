package fr.lacaleche.glue.web.internal;

import net.fabricmc.api.ClientModInitializer;

/** Fabric owns registration and process shutdown; consumers own individual surfaces. */
public final class GlueWebClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CefRuntime.bootstrap();
    }
}
