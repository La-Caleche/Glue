package fr.lacaleche.glue.client.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Shared constants for the Glue shader/buffer infrastructure.
 */
@Environment(EnvType.CLIENT)
public final class GlueConstants {

    /**
     * Default byte-buffer capacity for RenderType vertex buffers.
     * Sized to hold a typical small batch without reallocation.
     */
    public static final int DEFAULT_BUFFER_SIZE = 1536;

    private GlueConstants() {
    }
}
