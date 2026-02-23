package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.extension.PoseStackExtension;
import fr.lacaleche.glue.client.transform.PoseStackTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PoseStack.class)
public class PoseStackMixin implements PoseStackExtension {

    @Unique
    private PoseStackTransform glue$wrapper;

    @Override
    public PoseStackTransform glue$transformStack() {
        if (glue$wrapper == null) {
            glue$wrapper = new PoseStackTransform((PoseStack) (Object) this);
        }
        return glue$wrapper;
    }

}
