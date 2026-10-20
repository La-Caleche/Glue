package fr.lacaleche.glue.gametest.mixin;

import fr.lacaleche.glue.gametest.CameraPose;
import fr.lacaleche.glue.gametest.internal.film.FilmView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the camera on the film pose while the player stays where it is, unrendered. */
@Mixin(Camera.class)
public abstract class CameraFilmMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void glueGametest$filmPose(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                       float partialTick, CallbackInfo ci) {
        CameraPose pose = FilmView.pose();
        if (pose == null) return;
        this.setRotation(pose.yaw(), pose.pitch());
        this.setPosition(pose.eye());
    }
}
