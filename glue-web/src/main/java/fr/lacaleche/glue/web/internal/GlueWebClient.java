package fr.lacaleche.glue.web.internal;

import fr.lacaleche.glue.web.internal.browser.CefRuntime;

import net.fabricmc.api.ClientModInitializer;

/** Fabric owns registration and process shutdown; consumers own individual surfaces. */
public final class GlueWebClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CefRuntime.bootstrap();
    }
}
