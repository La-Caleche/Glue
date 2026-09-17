package fr.lacaleche.glue.testmod.scene;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Positions each block using its preview transform; the base renderer performs the single draw. */
final class SceneTestPreviewRenderer extends BlockSceneRenderer {

    private final SceneTestController controller;

    SceneTestPreviewRenderer(SceneTestController controller) {
        this.controller = controller;
        this.setHalfExtentX(SceneTestController.HALF_X);
        this.setHalfExtentZ(SceneTestController.HALF_Z);
        this.setMinY(SceneTestController.MIN_Y);
        this.setMaxY(SceneTestController.MAX_Y);
    }

    @Override
    protected void renderBlock(PoseStack matrices, BlockPos position, BlockState state, int x, int y, int z) {
        TransformationComponent transform = this.controller.getBlockTransform(position);
        if (transform == null) {
            super.renderBlock(matrices, position, state, x, y, z);
            return;
        }
        matrices.translate(transform.translation().x(), transform.translation().y(), transform.translation().z());
        matrices.mulPose(transform.leftRotation());
        matrices.scale(transform.scale().x(), transform.scale().y(), transform.scale().z());
        matrices.mulPose(transform.rightRotation());
        matrices.translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    protected void renderGrid(PoseStack matrices) {
        BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        float min = -5;
        float max = 6;
        for (float step = min; step <= max; step++) {
            vertices.addVertex(step, 0, min).setColor(1f, 1f, 1f, 0.4f).setNormal(0, 0, 1);
            vertices.addVertex(step, 0, max).setColor(1f, 1f, 1f, 0.4f).setNormal(0, 0, 1);
            vertices.addVertex(min, 0, step).setColor(1f, 1f, 1f, 0.4f).setNormal(1, 0, 0);
            vertices.addVertex(max, 0, step).setColor(1f, 1f, 1f, 0.4f).setNormal(1, 0, 0);
        }
        MeshData mesh = vertices.build();
        if (mesh != null) RenderType.lines().draw(mesh);
    }
}
