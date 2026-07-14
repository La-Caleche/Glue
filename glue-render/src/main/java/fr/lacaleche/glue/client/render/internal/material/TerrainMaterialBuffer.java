package fr.lacaleche.glue.client.render.internal.material;

import fr.lacaleche.glue.client.render.pipeline.MaterialFrame;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

import java.util.Optional;

/** Selects the terrain-material capture implementation used by the current world frame. */
public final class TerrainMaterialBuffer {

    private static final VanillaTerrainMaterialCapture VANILLA = new VanillaTerrainMaterialCapture();
    private static final SodiumTerrainMaterialCapture SODIUM = new SodiumTerrainMaterialCapture();

    private static TerrainMaterialCapture active;
    private static long frameSequence;

    private TerrainMaterialBuffer() {
    }

    public static void init() {
        VANILLA.init();
    }

    /** Opens a material frame with the capture that matches the terrain renderer in use. */
    public static void beginFrame(long sequence, boolean requested) {
        frameSequence = sequence;
        active = null;
        VANILLA.cancelFrame();
        SODIUM.cancelFrame();
        if (!requested) return;
        active = FabricLoader.getInstance().isModLoaded("sodium") ? SODIUM : VANILLA;
        active.beginFrame(sequence);
    }

    public static void cancelFrame(long sequence) {
        frameSequence = sequence;
        active = null;
        VANILLA.cancelFrame();
        SODIUM.cancelFrame();
    }

    public static Optional<MaterialFrame> currentFrame(long sequence) {
        if (active == null || sequence != frameSequence) return Optional.empty();
        return active.currentFrame(sequence);
    }

    static void captureVanilla(ChunkSectionsToRender sections) {
        if (active == VANILLA) VANILLA.capture(sections);
    }

    static void beginSodiumPass(Object renderPass) {
        if (active == SODIUM) SODIUM.beginPass(renderPass);
    }

    static void endSodiumPass() {
        if (active == SODIUM) SODIUM.endPass();
    }

    public static void cleanup() {
        active = null;
        VANILLA.cleanup();
        SODIUM.cleanup();
    }
}
