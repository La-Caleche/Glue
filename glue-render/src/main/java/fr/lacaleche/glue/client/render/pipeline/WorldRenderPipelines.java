package fr.lacaleche.glue.client.render.pipeline;

import fr.lacaleche.glue.Glue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Priority-based proxy for the world-frame implementation selected for each frame. */
@Environment(EnvType.CLIENT)
public final class WorldRenderPipelines {

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final List<BooleanSupplier> MATERIAL_INTEREST = new ArrayList<>();
    private static WorldRenderPipeline active;
    private static long frameSequence;
    private static String lastLoggedPipeline;
    private static String lastLoggedFailure;

    private WorldRenderPipelines() {
    }

    /**
     * Registers an implementation. Higher priorities are considered first.
     * Registration is intended for client initialization before world rendering starts.
     */
    public static void register(WorldRenderPipeline pipeline, int priority) {
        if (pipeline == null) throw new NullPointerException("pipeline");
        if (pipeline.id() == null || pipeline.id().isBlank()) {
            throw new IllegalArgumentException("pipeline id must not be blank");
        }
        ENTRIES.removeIf(entry -> entry.pipeline.id().equals(pipeline.id()));
        ENTRIES.add(new Entry(pipeline, priority));
        ENTRIES.sort(Comparator.comparingInt(Entry::priority).reversed());
    }

    /**
     * Declares that a consumer needs material data whenever the supplier returns true.
     * Material capture costs a terrain replay or an extra attachment, so it only runs while at
     * least one registered consumer is interested in the frame being started.
     */
    public static void requestMaterial(BooleanSupplier interest) {
        if (interest == null) throw new NullPointerException("interest");
        MATERIAL_INTEREST.add(interest);
    }

    /** Selects one implementation and fixes that choice for the rest of this frame. */
    public static void beginFrame() {
        frameSequence++;
        active = null;
        for (Entry entry : ENTRIES) {
            if (entry.pipeline.isApplicable()) {
                active = entry.pipeline;
                break;
            }
        }
        if (active == null) return;
        boolean materialRequested = false;
        for (BooleanSupplier interest : MATERIAL_INTEREST) {
            if (interest.getAsBoolean()) {
                materialRequested = true;
                break;
            }
        }
        active.beginFrame(frameSequence, materialRequested);
        if (!active.id().equals(lastLoggedPipeline)) {
            Glue.LOGGER.info("World render pipeline selected: {}", active.id());
            lastLoggedPipeline = active.id();
        }
    }

    public static Optional<WorldRenderFrame> currentFrame() {
        if (active == null) return Optional.empty();
        Optional<WorldRenderFrame> frame = active.currentFrame(frameSequence);
        if (frame.isPresent()) {
            lastLoggedFailure = null;
            return frame;
        }
        String failure = active.id() + ": " + active.unavailableReason();
        if (!failure.equals(lastLoggedFailure)) {
            Glue.LOGGER.warn("World render pipeline unavailable: {}", failure);
            lastLoggedFailure = failure;
        }
        return Optional.empty();
    }

    public static boolean isAuxiliaryPass() {
        return active != null && active.isAuxiliaryPass();
    }

    public static Optional<String> activePipelineId() {
        return active == null ? Optional.empty() : Optional.of(active.id());
    }

    public static Optional<String> unavailableReason() {
        if (active == null) return Optional.of("no registered pipeline is applicable");
        if (active.currentFrame(frameSequence).isPresent()) return Optional.empty();
        return Optional.of(active.unavailableReason());
    }

    private record Entry(WorldRenderPipeline pipeline, int priority) {
    }
}
