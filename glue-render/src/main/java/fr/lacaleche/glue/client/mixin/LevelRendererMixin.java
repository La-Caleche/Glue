package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * Capture the matrices the level is actually drawn with. Deriving a view matrix
     * from {@code camera.rotation()} instead loses view bobbing, so anything that
     * unprojects the depth buffer drifts against the world while the player moves.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void glue$captureFrameMatrices(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
                                           boolean renderBlockOutline, Camera camera,
                                           Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                           GpuBufferSlice fogBuffer, Vector4f fogColor, boolean isFoggy,
                                           CallbackInfo ci) {
        FrameMatrices.capture(frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderHitOutline",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void glue$renderHitOutline(PoseStack matrices, VertexConsumer vertexConsumer, Entity entity, double cameraX, double cameraY, double cameraZ, BlockPos pos, BlockState state, int color, CallbackInfo ci) {
        if (DrawSelectionEvents.BLOCK.invoker().onHighlightBlock(minecraft, minecraft.level, new Vec3(cameraX, cameraY, cameraZ), minecraft.hitResult, matrices, renderBuffers.bufferSource()))
            ci.cancel();
    }

}
