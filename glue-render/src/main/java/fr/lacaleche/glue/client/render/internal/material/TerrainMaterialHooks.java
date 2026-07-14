package fr.lacaleche.glue.client.render.internal.material;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

/** Renderer hook bridge; mixins delegate here without owning capture state. */
public final class TerrainMaterialHooks {

    private TerrainMaterialHooks() {
    }

    public static void captureVanilla(ChunkSectionsToRender sections) {
        TerrainMaterialBuffer.captureVanilla(sections);
    }

    public static void beginSodiumPass(Object renderPass) {
        TerrainMaterialBuffer.beginSodiumPass(renderPass);
    }

    public static void endSodiumPass() {
        TerrainMaterialBuffer.endSodiumPass();
    }
}
