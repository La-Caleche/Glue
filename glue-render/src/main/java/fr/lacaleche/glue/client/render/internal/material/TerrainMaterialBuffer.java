package fr.lacaleche.glue.client.render.internal.material;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Selects the terrain-material capture implementation used by the current world frame. */
public final class TerrainMaterialBuffer {

    private static final VanillaTerrainMaterialCapture VANILLA = new VanillaTerrainMaterialCapture();
    private static final SodiumTerrainMaterialCapture SODIUM = new SodiumTerrainMaterialCapture();
    private static final List<BooleanSupplier> INTEREST = new ArrayList<>();

    private static TerrainMaterialCapture active;
    private static long frameSequence;

    private TerrainMaterialBuffer() {
    }

    public static void init() {
        VANILLA.init();
    }

    /** True once {@link #beginFrame()} has opened a coherent material frame this frame (Lumos
     *  wants material, and we are on the vanilla Fancy path, not Iris/Fabulous). Gates the
     *  dynamic G-buffer capture to the same conditions as terrain material. */
    public static boolean isActive() {
        return active != null;
    }

    /** Declares that a consumer needs material data whenever the supplier returns true. */
    public static void requestWhen(BooleanSupplier interest) {
        if (interest == null) throw new NullPointerException("interest");
        INTEREST.add(interest);
    }

    /** Opens a material frame with the capture that matches the terrain renderer in use. */
    public static void beginFrame() {
        frameSequence++;
        active = null;
        VANILLA.cancelFrame();
        SODIUM.cancelFrame();
        // Lumos targets the vanilla Fancy path. Capture material only when a consumer needs it AND
        // the frame can actually produce a coherent material buffer: not under Fabulous (translucents
        // composite from a separate target, so the main depth would not match) and not under an
        // active Iris shaderpack (the pack owns its own colortex layout).
        if (!isRequested() || Minecraft.useShaderTransparency()
                || fr.lacaleche.glue.compat.RenderCompat.isIrisShaderEnabled()) return;
        active = FabricLoader.getInstance().isModLoaded("sodium") ? SODIUM : VANILLA;
        active.beginFrame(frameSequence);
    }

    public static int currentColorTextureId() {
        return active == null ? -1 : active.colorTextureId();
    }

    public static int currentDepthTextureId() {
        return active == null ? -1 : active.depthTextureId();
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

    private static boolean isRequested() {
        for (BooleanSupplier supplier : INTEREST) if (supplier.getAsBoolean()) return true;
        return false;
    }
}
