package fr.lacaleche.glue.client.render.internal.material;

import fr.lacaleche.glue.client.render.internal.gbuffer.CoreShaderMaterialPatch;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Decides whether the current world frame can produce a coherent terrain material frame.
 *
 * <p>Vanilla terrain needs no driver here: the patched {@code core/terrain} core shader writes its
 * material during the terrain draw itself, redirected into the shared G-buffer by
 * {@code GBufferCapture} -- the same seam entities and particles use. Sodium owns its own
 * render-pass plumbing, so it is the one path that still needs an explicit attach/detach around its
 * opaque pass.
 */
public final class TerrainMaterialBuffer {

    private static final SodiumTerrainMaterialCapture SODIUM = new SodiumTerrainMaterialCapture();
    private static final List<BooleanSupplier> INTEREST = new ArrayList<>();
    private static final List<Runnable> RELEASE = new ArrayList<>();

    private static boolean active;
    private static boolean sodiumActive;
    private static long frameSequence;

    private TerrainMaterialBuffer() {
    }

    /** True once {@link #beginFrame()} has opened a material frame this frame (Lumos wants
     *  material, not Fabulous, and either the terrain renderer in use can write material or an
     *  Iris shaderpack frame admits the self-contained captures). Gates the G-buffer capture. */
    public static boolean isActive() {
        return active;
    }

    /** Declares that a consumer needs material data whenever the supplier returns true. */
    public static void requestWhen(BooleanSupplier interest) {
        if (interest == null) throw new NullPointerException("interest");
        INTEREST.add(interest);
    }

    /**
     * Declares work to run when the frame stops being able to capture material at all, so a consumer
     * can free GPU memory it only needs while material is being captured. Fabulous selected can hold
     * that state for the rest of a session, and the frame loop would otherwise never revisit those
     * allocations.
     *
     * <p>Runs on the render thread, once per transition. Losing interest does NOT release: a consumer
     * whose last light just left will likely want it back, and rebuilding on that edge costs more
     * than it reclaims. Consumers must still tolerate being released and rebuilt at any time.
     */
    public static void releaseOnClose(Runnable release) {
        if (release == null) throw new NullPointerException("release");
        RELEASE.add(release);
    }

    /** Opens a material frame if the terrain renderer in use can fill one, and notifies
     *  {@link #releaseOnClose} consumers on the frame the gate closes. */
    public static void beginFrame() {
        frameSequence++;
        boolean wasActive = active;
        active = false;
        sodiumActive = false;
        SODIUM.cancelFrame();
        openFrame();
        // Release only when the frame CANNOT capture -- not when nothing happened to ask this frame.
        // Interest flickers (the last light leaving and coming back), and rebuilding every program,
        // VAO and target on that edge would churn far more than it reclaims. Being blocked by
        // Fabulous lasts, which is what makes the allocations worth freeing.
        if (wasActive && !active && isBlocked()) {
            for (Runnable release : RELEASE) release.run();
        }
    }

    /** Whether this frame could not capture material even if a consumer asked. */
    private static boolean isBlocked() {
        return Minecraft.useShaderTransparency();
    }

    private static void openFrame() {
        // Capture material only when a consumer needs it AND the frame can actually produce a
        // coherent material buffer: not under Fabulous (translucents composite from a separate
        // target, so the main depth would not match).
        if (!isRequested() || isBlocked()) return;

        // Under an active Iris shaderpack the pack owns every base geometry program, so neither
        // terrain patch can ride the world draws -- but the SELF-CONTAINED captures (the
        // glass/water/metal re-renders, drawn with Glue's own pipelines after the pack's world
        // pass) still fill their attachments. The frame opens in that reduced mode: base surfaces
        // stay uncaptured and consumers resolve them through their estimate path.
        if (fr.lacaleche.glue.compat.RenderCompat.isIrisShaderEnabled()) {
            active = true;
            return;
        }

        // Terrain carries its id on EITHER path only because a chunk shader was source-patched. If an
        // anchor ever drifts, that patch no-ops and terrain reaches consumers with NO id -- and
        // consumers cap light on every pixel they cannot identify, so the whole world would go unlit.
        // Claiming nothing is the safe failure: with no material capability at all the cap does not
        // arm, and consumers fall back to a depth-derivative normal and an estimated albedo, which
        // still lights the world. Both branches must therefore gate on their own patch.
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            if (!SodiumMaterialShaderPatch.isReady()) return;
            sodiumActive = true;
            active = true;
            SODIUM.beginFrame(frameSequence);
            return;
        }
        active = CoreShaderMaterialPatch.isTerrainReady();
    }

    static void beginSodiumPass(Object renderPass) {
        if (sodiumActive) SODIUM.beginPass(renderPass);
    }

    static void endSodiumPass() {
        if (sodiumActive) SODIUM.endPass();
    }

    public static void cleanup() {
        active = false;
        sodiumActive = false;
        SODIUM.cleanup();
    }

    private static boolean isRequested() {
        for (BooleanSupplier supplier : INTEREST) if (supplier.getAsBoolean()) return true;
        return false;
    }
}
