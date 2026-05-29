package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A tick-based, single-shot post-processing effect backed by a {@link PostShaderHandle}.
 *
 * <p>Each instance has a fixed duration (in ticks), an optional progress curve, and
 * an optional UBO writer. Call {@link #trigger()} to start the effect and {@link #tick()}
 * each client tick. Pass this to a {@link GluePostEffectRenderer} to have the lifecycle
 * managed automatically.</p>
 *
 * <pre>{@code
 * TimedPostEffect effect = TimedPostEffect.builder(MyShaders.IMPACT)
 *         .ubo("ImpactConfig", 16)
 *         .duration(10)
 *         .uniforms(w -> w.putFloat(0.6f).putFloat(0.05f).putFloat(0f).putFloat(5f))
 *         .build();
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class TimedPostEffect {

    private final PostShaderHandle handle;
    private final String uboName;
    private final int uboSizeBytes;
    private final int durationTicks;
    private final Function<Float, Float> progressCurve;
    private final Consumer<UniformWriter> uniformWriter;

    private int currentTick = -1;

    private TimedPostEffect(PostShaderHandle handle, String uboName, int uboSizeBytes,
                            int durationTicks, Function<Float, Float> progressCurve,
                            Consumer<UniformWriter> uniformWriter) {
        this.handle = handle;
        this.uboName = uboName;
        this.uboSizeBytes = uboSizeBytes;
        this.durationTicks = durationTicks;
        this.progressCurve = progressCurve;
        this.uniformWriter = uniformWriter;
    }

    public static Builder builder(PostShaderHandle handle) {
        return new Builder(handle);
    }

    /**
     * Starts the effect from the beginning. Safe to call while already active (restarts).
     */
    public void trigger() {
        currentTick = 0;
    }

    /**
     * Stops the effect immediately.
     */
    public void stop() {
        currentTick = -1;
    }

    /**
     * Returns true while the effect is playing.
     */
    public boolean isActive() {
        return currentTick >= 0 && currentTick < durationTicks;
    }

    /**
     * Advances the effect by one tick. Call once per client tick.
     * Automatically stops the effect when its duration elapses.
     */
    public void tick() {
        if (currentTick < 0) return;
        currentTick++;
        if (currentTick >= durationTicks) {
            currentTick = -1;
        }
    }

    /**
     * Applies the effect to the main render target if active.
     *
     * @param mc          current Minecraft instance
     * @param pool        the resource pool for this frame
     * @param partialTick interpolation factor within the current tick
     * @return true if the effect was rendered, false if inactive
     */
    public boolean render(Minecraft mc, CrossFrameResourcePool pool, float partialTick) {
        if (!isActive()) return false;

        float rawProgress = Mth.clamp((currentTick + partialTick) / durationTicks, 0f, 1f);
        float progress = progressCurve.apply(rawProgress);

        RenderTarget target = mc.getMainRenderTarget();
        handle.setUniform(uboName, uboSizeBytes, builder -> {
            UniformWriter writer = new UniformWriter(builder, progress);
            uniformWriter.accept(writer);
        });
        handle.apply(target, pool);
        return true;
    }

    /**
     * Thin wrapper around {@link Std140Builder} that also carries the current
     * progress value, allowing uniform writers to write relative values without
     * needing to capture progress in a closure.
     */
    public static class UniformWriter {

        private final Std140Builder builder;
        private final float progress;

        UniformWriter(Std140Builder builder, float progress) {
            this.builder = builder;
            this.progress = progress;
        }

        /**
         * Returns the current [0..1] progress value (after curve is applied).
         */
        public float progress() {
            return progress;
        }

        /**
         * Writes a float value to the UBO.
         */
        public UniformWriter putFloat(float value) {
            builder.putFloat(value);
            return this;
        }

        /**
         * Writes the current progress value to the UBO.
         */
        public UniformWriter putProgress() {
            builder.putFloat(progress);
            return this;
        }
    }

    public static class Builder {

        private final PostShaderHandle handle;
        private String uboName;
        private int uboSizeBytes = 4;
        private int durationTicks = 20;
        private Function<Float, Float> progressCurve = t -> t;
        private Consumer<UniformWriter> uniformWriter = UniformWriter::putProgress;

        private Builder(PostShaderHandle handle) {
            this.handle = handle;
        }

        /**
         * Sets the UBO block name and its size in bytes.
         *
         * @param name      the UBO block name in the shader (e.g. {@code "ImpactConfig"})
         * @param sizeBytes total size in bytes; must be a positive multiple of 4
         */
        public Builder ubo(String name, int sizeBytes) {
            this.uboName = name;
            this.uboSizeBytes = sizeBytes;
            return this;
        }

        /**
         * Duration of the effect in ticks.
         */
        public Builder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        /**
         * Linear progress curve (default).
         */
        public Builder curveLinear() {
            this.progressCurve = t -> t;
            return this;
        }

        /**
         * Reverse linear curve: starts at 1.0, ends at 0.0.
         */
        public Builder curveReverse() {
            this.progressCurve = t -> 1.0f - t;
            return this;
        }

        /**
         * Custom progress curve. Input and output are both in [0, 1].
         */
        public Builder curve(Function<Float, Float> curve) {
            this.progressCurve = curve;
            return this;
        }

        /**
         * Custom UBO writer. The {@link UniformWriter} provides the current
         * (post-curve) progress and helpers for writing floats.
         */
        public Builder uniforms(Consumer<UniformWriter> writer) {
            this.uniformWriter = writer;
            return this;
        }

        public TimedPostEffect build() {
            if (uboName == null || uboName.isBlank()) {
                throw new IllegalStateException("UBO name must be set via ubo(name, sizeBytes) and be non-blank");
            }
            if (uboSizeBytes <= 0 || uboSizeBytes % 4 != 0) {
                throw new IllegalArgumentException(
                        "uboSizeBytes must be a positive multiple of 4 (Std140 float alignment), got: " + uboSizeBytes);
            }
            return new TimedPostEffect(handle, uboName, uboSizeBytes, durationTicks, progressCurve, uniformWriter);
        }
    }
}
