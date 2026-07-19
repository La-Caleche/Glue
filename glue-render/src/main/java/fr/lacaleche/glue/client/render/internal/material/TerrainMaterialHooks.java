package fr.lacaleche.glue.client.render.internal.material;

/** Renderer hook bridge; mixins delegate here without owning capture state. */
public final class TerrainMaterialHooks {

    private TerrainMaterialHooks() {
    }

    public static void beginSodiumPass(Object renderPass) {
        TerrainMaterialBuffer.beginSodiumPass(renderPass);
    }

    public static void endSodiumPass() {
        TerrainMaterialBuffer.endSodiumPass();
    }
}
