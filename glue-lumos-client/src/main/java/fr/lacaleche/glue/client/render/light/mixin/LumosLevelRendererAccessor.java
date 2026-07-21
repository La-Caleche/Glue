package fr.lacaleche.glue.client.render.light.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's inner per-entity render so the entity shadow pass can render an entity from the
 * light's point of view exactly the way the world render does &mdash; interpolated position, view
 * yaw, partial-tick animation, packed light. Calling this inner method (rather than
 * {@code EntityRenderDispatcher.render} by hand) is also what makes the <em>local player</em> render
 * into the shadow map even in first person: the "don't draw the camera entity" skip lives in the
 * outer {@code renderLevel} loop, which this bypasses. This is the technique Iris uses.
 */
@Mixin(LevelRenderer.class)
public interface LumosLevelRendererAccessor {

    @Invoker("renderEntity")
    void glue$renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
                           float tickDelta, PoseStack poseStack, MultiBufferSource bufferSource);
}
