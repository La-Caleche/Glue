package fr.lacaleche.glue.client.render.pipeline;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.Optional;

/** Supplies coherent world-frame data without exposing a renderer-specific type. */
@Environment(EnvType.CLIENT)
public interface WorldRenderPipeline {

    String id();

    /** Whether this implementation owns the current renderer configuration. */
    boolean isApplicable();

    /** Called once at world-frame start after this implementation has been selected. */
    void beginFrame(long frameSequence, boolean materialRequested);

    /** Returns the selected frame only when every required input is coherent and ready. */
    Optional<WorldRenderFrame> currentFrame(long frameSequence);

    /** True for a renderer-owned auxiliary pass in which world effects must not run. */
    default boolean isAuxiliaryPass() {
        return false;
    }

    /** Diagnostic shown when this applicable implementation cannot provide a frame. */
    default String unavailableReason() {
        return "frame data is not ready";
    }
}
