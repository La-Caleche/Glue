package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.events.DebugEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Inject(method = {"render"}, at = {@At("TAIL")})
    private void glue$render(PoseStack matrices, Frustum frustum, MultiBufferSource.BufferSource vertexConsumers,
                             double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        DebugManager.getInstance().renderAll(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
        DebugEvents.WORLD_DEBUG.invoker().onRenderWorldDebug(matrices, vertexConsumers,
                cameraX, cameraY, cameraZ);
    }

}
