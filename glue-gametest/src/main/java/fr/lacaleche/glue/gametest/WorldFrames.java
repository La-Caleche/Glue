package fr.lacaleche.glue.gametest;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/** Lazily installed once by a client-thread frame wait; no event listener is retained per test. */
final class WorldFrames {

    private static int count;

    static {
        WorldRenderEvents.END.register(context -> count++);
    }

    private WorldFrames() {
    }

    static int count() {
        return count;
    }
}
