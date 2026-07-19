package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.client.render.light.internal.gl.GlDeferredLightPass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlGlassReflectionPass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightBloomPass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightCombinePass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightCompositePass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightDenoisePass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightResources;
import fr.lacaleche.glue.client.render.light.internal.gl.GlTintBlurPass;
import fr.lacaleche.glue.client.render.light.internal.gl.LightAccumulator;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowBaker;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowParams;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.List;

/** Accumulates visible lights into HDR and composites them into the selected world frame. */
final class DeferredLightPass {

    private final GlLightResources resources = new GlLightResources();
    private final GlDeferredLightPass deferred = new GlDeferredLightPass(resources);
    private final GlLightCompositePass composite = new GlLightCompositePass(resources);
    private final GlLightCombinePass combine = new GlLightCombinePass(resources);
    private final GlGlassReflectionPass reflection = new GlGlassReflectionPass(resources);
    private final GlLightDenoisePass denoise = new GlLightDenoisePass(resources);
    private final GlLightBloomPass bloom = new GlLightBloomPass(resources);
    private final GlTintBlurPass tintBlur = new GlTintBlurPass(resources);
    private final LightAccumulator accumulator = new LightAccumulator();
    private final float[] blobData = new float[EntityShadowBlobs.FLOATS];

    GlTintBlurPass tintBlur() {
        return tintBlur;
    }

    void render(LumosFrame frame, Matrix4f viewProjection, Matrix4f inverseViewProjection,
                Vector3d camera, List<Light> lights, ShadowBaker shadows,
                Minecraft minecraft, float partialTick) {
        if (!accumulator.beginFrame(frame.width(), frame.height())) return;
        int lightFramebuffer = accumulator.getFramebufferId();
        if (lightFramebuffer <= 0) return;

        int gbufferAlbedo = GBufferCapture.albedoNormalTextureId();
        int gbufferId = GBufferCapture.materialIdTextureId();
        int gbufferProps = GBufferCapture.materialPropsTextureId();

        // Seconds for water ripple animation, wrapped to a day to keep the sine phase precise.
        float time = minecraft.level != null
                ? (float) (((minecraft.level.getGameTime() % 24000L) + partialTick) * 0.05)
                : 0.0f;

        for (Light light : lights) {
            int[] bounds = LightInfluence.of(light, camera)
                    .screenBounds(viewProjection, frame.width(), frame.height());
            if (bounds == null) continue;
            List<ShadowParams> maps = shadows.get(light);
            if (maps == null || maps.isEmpty()) {
                // Only lights with no shadow map fall back to capsule blobs; a mapped light casts a
                // real per-frame entity depth map, so collecting (and uploading) blobs for it is waste.
                int blobCount = EntityShadowBlobs.collect(minecraft, light, camera, partialTick, blobData);
                accumulate(lightFramebuffer, frame, viewProjection, inverseViewProjection,
                        camera, light, bounds, null,
                        gbufferAlbedo, gbufferId, gbufferProps, blobCount, time);
                continue;
            }
            for (ShadowParams map : maps) {
                accumulate(lightFramebuffer, frame, viewProjection, inverseViewProjection,
                        camera, light, bounds, map,
                        gbufferAlbedo, gbufferId, gbufferProps, 0, time);
            }
        }

        accumulator.captureScene(frame.framebufferId());
        int denoised = denoise.apply(accumulator.getColorTextureId(), frame.sceneDepthTextureId(),
                frame.width(), frame.height());

        // Composite the lit scene into a linear HDR buffer, bloom its VISIBLE bright pixels,
        // then combine (add bloom + tonemap) to the main target. Blooming the lit buffer --
        // the actual on-screen brightness -- is what keeps the glow on genuine highlights.
        int litTexture = accumulator.getLitTextureId();
        boolean composited = composite.render(denoised,
                accumulator.getSceneTextureId(), accumulator.getSceneDepthTextureId(),
                gbufferAlbedo, gbufferId, inverseViewProjection,
                accumulator.getLitFramebufferId(),
                frame.width(), frame.height());
        if (!composited) return;
        int bloomTexture = bloom.apply(litTexture, frame.width(), frame.height());
        combine.render(litTexture, bloomTexture, frame.framebufferId(),
                frame.width(), frame.height());

        // Environment reflection on the panes. Re-capture AFTER the combine so the mirror
        // shows the final lit scene, then blend it onto the glass pixels (identified by the
        // GLASS material id in the G-buffer).
        if (gbufferId > 0) {
            accumulator.captureScene(frame.framebufferId());
            reflection.render(accumulator.getSceneTextureId(), accumulator.getSceneDepthTextureId(),
                    gbufferId, gbufferAlbedo, viewProjection, inverseViewProjection, camera, time,
                    frame.framebufferId(), frame.width(), frame.height());
        }
    }

    void cleanup() {
        tintBlur.cleanup();
        denoise.cleanup();
        bloom.cleanup();
        resources.cleanup();
        accumulator.cleanup();
    }

    private void accumulate(int lightFramebuffer, LumosFrame frame,
                            Matrix4f viewProjection, Matrix4f inverseViewProjection,
                            Vector3d camera, Light light, int[] bounds, ShadowParams shadow,
                            int gbufferAlbedo, int gbufferId, int gbufferProps, int blobCount, float time) {
        deferred.render(lightFramebuffer, frame.sceneDepthTextureId(),
                viewProjection, inverseViewProjection, camera, light,
                frame.width(), frame.height(), bounds, shadow,
                gbufferAlbedo, gbufferId, gbufferProps,
                blobData, blobCount, time);
    }

}
