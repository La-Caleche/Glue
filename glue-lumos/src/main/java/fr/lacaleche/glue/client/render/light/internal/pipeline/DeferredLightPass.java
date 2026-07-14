package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.internal.gl.GlDeferredLightPass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightCompositePass;
import fr.lacaleche.glue.client.render.light.internal.gl.GlLightResources;
import fr.lacaleche.glue.client.render.light.internal.gl.GlTintBlurPass;
import fr.lacaleche.glue.client.render.light.internal.gl.LightAccumulator;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowBaker;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowParams;
import fr.lacaleche.glue.client.render.pipeline.MaterialFrame;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderFrame;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.List;

/** Accumulates visible lights into HDR and composites them into the selected world frame. */
final class DeferredLightPass {

    private final GlLightResources resources = new GlLightResources();
    private final GlDeferredLightPass deferred = new GlDeferredLightPass(resources);
    private final GlLightCompositePass composite = new GlLightCompositePass(resources);
    private final GlTintBlurPass tintBlur = new GlTintBlurPass(resources);
    private final LightAccumulator accumulator = new LightAccumulator();

    GlTintBlurPass tintBlur() {
        return tintBlur;
    }

    void render(WorldRenderFrame frame, Matrix4f viewProjection, Matrix4f inverseViewProjection,
                Vector3d camera, List<Light> lights, ShadowBaker shadows,
                GlassBufferPass.Textures glass) {
        if (!accumulator.beginFrame(frame.width(), frame.height())) return;
        int lightFramebuffer = accumulator.getFramebufferId();
        if (lightFramebuffer <= 0) return;

        MaterialFrame material = frame.material().orElse(null);
        int materialColor = material == null ? -1 : material.colorTextureId();
        int materialDepth = material == null ? -1 : material.depthTextureId();

        for (Light light : lights) {
            int[] bounds = LightInfluence.of(light, camera)
                    .screenBounds(viewProjection, frame.width(), frame.height());
            if (bounds == null) continue;
            List<ShadowParams> maps = shadows.get(light);
            if (maps == null || maps.isEmpty()) {
                accumulate(lightFramebuffer, frame, viewProjection, inverseViewProjection,
                        camera, light, bounds, null, glass, materialColor, materialDepth);
                continue;
            }
            for (ShadowParams map : maps) {
                accumulate(lightFramebuffer, frame, viewProjection, inverseViewProjection,
                        camera, light, bounds, map, glass, materialColor, materialDepth);
            }
        }

        accumulator.captureScene(frame.sourceFramebufferId());
        composite.render(accumulator.getColorTextureId(),
                accumulator.getSceneTextureId(), accumulator.getSceneDepthTextureId(),
                materialColor, materialDepth, frame.destinationFramebufferId(),
                frame.width(), frame.height());
    }

    void cleanup() {
        tintBlur.cleanup();
        resources.cleanup();
        accumulator.cleanup();
    }

    private void accumulate(int lightFramebuffer, WorldRenderFrame frame,
                            Matrix4f viewProjection, Matrix4f inverseViewProjection,
                            Vector3d camera, Light light, int[] bounds, ShadowParams shadow,
                            GlassBufferPass.Textures glass, int materialColor, int materialDepth) {
        deferred.render(lightFramebuffer, frame.sceneDepthTextureId(),
                viewProjection, inverseViewProjection, camera, light,
                frame.width(), frame.height(), bounds, shadow, glass.colorId(), glass.depthId(),
                materialColor, materialDepth);
    }

}
