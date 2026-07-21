package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import com.mojang.blaze3d.vertex.*;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block scene renderer with gizmo transform overlay.
 * <p>
 * Extends {@link BlockSceneRenderer} and overrides {@link #renderBlock} to
 * apply per-block {@link TransformationComponent} from the {@link SceneTestController},
 * so gizmo-dragged blocks appear at their new positions in the preview.
 */
public class SceneTestPreviewRenderer extends BlockSceneRenderer {

    private SceneTestController sceneController;

    public void setSceneController(SceneTestController sceneController) {
        this.sceneController = sceneController;
    }

    @Override
    protected void renderBlock(PoseStack matrices, BlockPos worldPos, BlockState blockState,
                               int relX, int relY, int relZ) {
        TransformationComponent transform = sceneController != null
                ? sceneController.getBlockTransform(worldPos)
                : null;

        if (transform != null) {
            matrices.translate(
                    transform.translation().x(),
                    transform.translation().y(),
                    transform.translation().z());
            matrices.mulPose(transform.leftRotation());
            matrices.scale(
                    transform.scale().x(),
                    transform.scale().y(),
                    transform.scale().z());
            matrices.mulPose(transform.rightRotation());
            matrices.translate(-0.5f, -0.5f, -0.5f);
        } else {
            super.renderBlock(matrices, worldPos, blockState, relX, relY, relZ);
        }
    }

    @Override
    protected void renderGrid(PoseStack matrices) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);

        float min = -5.0f;
        float max = 6.0f;
        float y = 0f;
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = .4f;

        for (float i = min; i <= max; i += 1.0f) {
            bufferBuilder.addVertex(i, y, min).setColor(r, g, b, a).setNormal(0, 0, max - min);
            bufferBuilder.addVertex(i, y, max).setColor(r, g, b, a).setNormal(0, 0, max - min);

            bufferBuilder.addVertex(min, y, i).setColor(r, g, b, a).setNormal(max - min, 0, 0);
            bufferBuilder.addVertex(max, y, i).setColor(r, g, b, a).setNormal(max - min, 0, 0);
        }

        MeshData mesh = bufferBuilder.build();
        if (mesh != null) {
            RenderType.lines().draw(mesh);
        }
    }
}
