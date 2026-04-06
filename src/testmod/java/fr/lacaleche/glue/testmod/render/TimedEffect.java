package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Function;

public class TimedEffect {

    private final PostShaderHandle handle;
    private final String uboName;
    private final int uboSize;
    private final int durationTicks;
    private final Function<Float, Float> progressCurve;
    private final Consumer<UniformWriter> uniformWriter;

    private int currentTick = -1;

    private TimedEffect(PostShaderHandle handle, String uboName, int uboSize, int durationTicks,
                        Function<Float, Float> progressCurve, Consumer<UniformWriter> uniformWriter) {
        this.handle = handle;
        this.uboName = uboName;
        this.uboSize = uboSize;
        this.durationTicks = durationTicks;
        this.progressCurve = progressCurve;
        this.uniformWriter = uniformWriter;
    }

    public void trigger() {
        currentTick = 0;
    }

    public void stop() {
        currentTick = -1;
    }

    public boolean isActive() {
        return currentTick >= 0 && currentTick < durationTicks;
    }

    public void tick() {
        if (currentTick < 0) return;
        currentTick++;
        if (currentTick >= durationTicks) {
            currentTick = -1;
        }
    }

    public boolean render(Minecraft mc, CrossFrameResourcePool pool, float partialTick) {
        if (!isActive()) return false;

        float rawProgress = Mth.clamp((currentTick + partialTick) / durationTicks, 0f, 1f);
        float progress = progressCurve.apply(rawProgress);

        RenderTarget target = mc.getMainRenderTarget();
        handle.setUniform(uboName, uboSize, builder -> {
            UniformWriter writer = new UniformWriter(builder, progress);
            uniformWriter.accept(writer);
        });
        handle.apply(target, pool);
        return true;
    }

    public static Builder builder(PostShaderHandle handle) {
        return new Builder(handle);
    }

    public static class UniformWriter {

        private final Std140Builder builder;
        private final float progress;

        UniformWriter(Std140Builder builder, float progress) {
            this.builder = builder;
            this.progress = progress;
        }

        public float progress() {
            return progress;
        }

        public UniformWriter putFloat(float value) {
            builder.putFloat(value);
            return this;
        }

        public UniformWriter putProgress() {
            builder.putFloat(progress);
            return this;
        }
    }

    public static class Builder {

        private final PostShaderHandle handle;
        private String uboName;
        private int uboSize = 4;
        private int durationTicks = 20;
        private Function<Float, Float> progressCurve = t -> t;
        private Consumer<UniformWriter> uniformWriter = UniformWriter::putProgress;

        private Builder(PostShaderHandle handle) {
            this.handle = handle;
        }

        public Builder ubo(String name, int sizeBytes) {
            this.uboName = name;
            this.uboSize = sizeBytes;
            return this;
        }

        public Builder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        public Builder curveLinear() {
            this.progressCurve = t -> t;
            return this;
        }

        public Builder curveReverse() {
            this.progressCurve = t -> 1.0f - t;
            return this;
        }

        public Builder curve(Function<Float, Float> curve) {
            this.progressCurve = curve;
            return this;
        }

        public Builder uniforms(Consumer<UniformWriter> writer) {
            this.uniformWriter = writer;
            return this;
        }

        public TimedEffect build() {
            if (uboName == null) {
                throw new IllegalStateException("UBO name must be set");
            }
            return new TimedEffect(handle, uboName, uboSize, durationTicks, progressCurve, uniformWriter);
        }
    }
}
