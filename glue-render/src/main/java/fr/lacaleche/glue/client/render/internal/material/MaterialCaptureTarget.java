package fr.lacaleche.glue.client.render.internal.material;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import fr.lacaleche.glue.client.utils.FramebufferHelper;

final class MaterialCaptureTarget {

    private final String name;
    private RenderTarget target;

    MaterialCaptureTarget(String name) {
        this.name = name;
    }

    void prepare(RenderTarget depthSource) {
        if (target == null) {
            target = new TextureTarget(name, depthSource.width, depthSource.height, true);
        } else if (target.width != depthSource.width || target.height != depthSource.height) {
            target.resize(depthSource.width, depthSource.height);
        }
    }

    void clear() {
        if (target != null) FramebufferHelper.clear(target, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    void copyDepthFrom(RenderTarget source) {
        if (target != null) target.copyDepthFrom(source);
    }

    RenderTarget renderTarget() {
        return target;
    }

    int colorTextureId() {
        return target == null ? -1 : FramebufferHelper.getColorTextureId(target);
    }

    int depthTextureId() {
        return target == null ? -1 : FramebufferHelper.getDepthTextureId(target);
    }

    int width() {
        return target == null ? 0 : target.width;
    }

    int height() {
        return target == null ? 0 : target.height;
    }

    void cleanup() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
