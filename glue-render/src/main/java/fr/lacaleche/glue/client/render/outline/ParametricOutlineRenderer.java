package fr.lacaleche.glue.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.math.Color;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A {@link SimpleBlockOutlineRenderer} that uses externally-provided color
 * and alpha values rather than hardcoded constants.
 *
 * <p>Created by {@link OutlineDefinition#bake()} — this is the runtime object
 * that replaces handwritten outline renderer subclasses for simple color overrides.</p>
 */
public class ParametricOutlineRenderer extends SimpleBlockOutlineRenderer {

    private final Color color;
    private final float alpha;

    public ParametricOutlineRenderer(Color color, float alpha) {
        this.color = color;
        this.alpha = alpha;
    }

    @Override
    protected void renderShape(VoxelShape voxelShape, PoseStack.Pose transform, VertexConsumer consumer, Color ignored) {
        float r = color.getRed() / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue() / 255.0f;
        float a = this.alpha;

        voxelShape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float xDiff = (float) (x2 - x1);
            float yDiff = (float) (y2 - y1);
            float zDiff = (float) (z2 - z1);
            float length = Mth.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
            if (length < 1e-6f) return;

            xDiff /= length;
            yDiff /= length;
            zDiff /= length;

            consumer.addVertex(transform.pose(), (float) x1, (float) y1, (float) z1)
                    .setColor(r, g, b, a)
                    .setNormal(transform, xDiff, yDiff, zDiff);
            consumer.addVertex(transform.pose(), (float) x2, (float) y2, (float) z2)
                    .setColor(r, g, b, a)
                    .setNormal(transform, xDiff, yDiff, zDiff);
        });
    }
}
