package fr.lacaleche.glue.testmod.render.block.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.render.outline.SimpleBlockOutlineRenderer;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;

public class ExampleBlockOutlineRenderer extends SimpleBlockOutlineRenderer {

    @Override
    protected void renderShape(VoxelShape voxelShape, PoseStack.Pose transform, VertexConsumer consumer, Color color) {
        super.renderShape(voxelShape, transform, consumer, Color.RED);
    }
}
