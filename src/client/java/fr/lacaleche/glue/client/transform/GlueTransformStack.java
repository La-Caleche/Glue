package fr.lacaleche.glue.client.transform;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.extension.PoseStackExtension;
import fr.lacaleche.glue.client.transform.flywheel.Transform;
import fr.lacaleche.glue.client.transform.flywheel.TransformStack;
import net.minecraft.core.Direction;

public interface GlueTransformStack extends Transform<GlueTransformStack>, TransformStack<GlueTransformStack> {

    static PoseStackTransform of(PoseStack stack) {
        return ((PoseStackExtension) stack).glue$transformStack();
    }

    default GlueTransformStack uncenterAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> translate(0, 0, -CENTER);
            case Y -> translate(0, -CENTER, 0);
            case Z -> translate(-CENTER, 0, 0);
        };
    }

    default GlueTransformStack mirror(Direction.Axis axis) {
        return switch (axis) {
            case X -> translate(0, 0, -1);
            case Y -> translate(0, -1, 0);
            case Z -> translate(-1, 0, 0);
        };
    }

    default GlueTransformStack then(Runnable runnable) {
        runnable.run();
        return this;
    }

}
