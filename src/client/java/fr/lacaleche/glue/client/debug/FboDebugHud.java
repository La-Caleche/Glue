package fr.lacaleche.glue.client.debug;

import fr.lacaleche.glue.client.debug.internal.Framebuffers;

import java.util.function.IntSupplier;

/**
 * Adds textures to the framebuffer viewer, the developer menu's Framebuffers page. The viewer lists
 * them after the main target's buffers.
 */
public final class FboDebugHud {

    private FboDebugHud() {
    }

    /**
     * Lists a full-screen texture another owner keeps alive, such as a G-buffer attachment, shown at the
     * main target's size. The supplier gives its live GL name each frame; a name of zero or less is
     * shown empty. Call it during client initialisation.
     */
    public static void registerTexture(String name, IntSupplier id) {
        Framebuffers.registerTexture(name, id);
    }
}
