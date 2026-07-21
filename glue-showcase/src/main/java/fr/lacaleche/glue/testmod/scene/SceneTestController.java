package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.render.gizmo.Abstract3DController;
import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.AbstractGizmoController;
import fr.lacaleche.glue.client.render.gizmo.GizmoMath;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Controls scene interaction: block selection via AABB raycasting and
 * gizmo-based transforms. All transforms are preview-only — the real world
 * is never modified.
 */
public class SceneTestController extends Abstract3DController {

    // Render half-extents — must match SceneTestPreviewRenderer
    private static final int HALF_X = 5;
    private static final int MIN_Y = -2;
    private static final int MAX_Y = 3;
    private static final int HALF_Z = 5;

    private final BlockPos playerPos;
    private TransformationComponent initialDragTransform = null;

    /**
     * Per-position transforms for the preview. These do NOT modify the real world.
     */
    private final Map<BlockPos, TransformationComponent> blockTransforms;

    private BlockPos selectedBlockPos = null;

    public SceneTestController(BlockPos playerPos, AbstractGizmoController gizmoController) {
        super(gizmoController);
        this.playerPos = playerPos;
        this.blockTransforms = new HashMap<>();
    }

    public BlockPos getSelectedBlockPos() {
        return selectedBlockPos;
    }

    @Override
    protected void onGizmoDragStart() {
        TransformationComponent transform = blockTransforms.getOrDefault(selectedBlockPos, null);
        if (transform != null) {
            initialDragTransform = transform;
        }
    }

    @Override
    protected void onGizmoDragEnd() {
        if (initialDragTransform != null) {
            TransformationComponent finalTransform = blockTransforms.getOrDefault(selectedBlockPos, null);
            if (finalTransform != null && !initialDragTransform.equals(finalTransform)) {
                historyManager
                        .execute(new UpdateItemCommand(this, selectedBlockPos, initialDragTransform, finalTransform));
            }
        }
        initialDragTransform = null;
    }

    public void setGizmo(BlockPos blockPos, TransformationComponent transform) {
        this.blockTransforms.put(blockPos, transform);
        updateGizmo(transform);
    }

    @Override
    public void applyGizmoTransform() {
        applyGizmoToSlot();
    }

    public void selectBlock(BlockPos blockPos) {
        if (blockPos == null) {
            this.selectedBlockPos = null;
            return;
        }

        this.selectedBlockPos = blockPos;
        setGizmoFromBlock();
    }

    public void clearSelectedBlock() {
        this.selectedBlockPos = null;
    }

    public void setGizmoFromBlock() {
        if (selectedBlockPos == null)
            return;

        TransformationComponent transform = this.blockTransforms.get(selectedBlockPos);
        if (transform == null) {
            // Initialize at the block's center in scene coordinates
            Vector3f relativePos = new Vector3f(
                    selectedBlockPos.getX() - playerPos.getX() + 0.5f,
                    selectedBlockPos.getY() - playerPos.getY() + 0.5f,
                    selectedBlockPos.getZ() - playerPos.getZ() + 0.5f);
            transform = new TransformationComponent(
                    relativePos,
                    new Quaternionf(),
                    new Vector3f(1, 1, 1),
                    new Quaternionf());
            this.blockTransforms.put(selectedBlockPos, transform);
        }

        updateGizmo(transform);
    }

    /**
     * Reads properties from the selected block and updates the gizmo matrix.
     */
    public void updateGizmo(TransformationComponent transform) {
        if (selectedBlockPos == null) {
            gizmoController.resetTransform();
            return;
        }

        gizmoController.recomposeMatrix(transform);
    }

    /**
     * Reads the current gizmo matrix and updates the block's preview transform.
     * Does NOT modify the real world — only updates the preview data.
     */
    public void applyGizmoToSlot() {
        if (selectedBlockPos == null)
            return;

        TransformationComponent newTransform = new TransformationComponent(
                new Vector3f(gizmoController.getTranslation()),
                new Quaternionf(gizmoController.getLeftRotation()),
                new Vector3f(gizmoController.getScale()),
                new Quaternionf(gizmoController.getRightRotation()));

        this.blockTransforms.put(selectedBlockPos, newTransform);
    }

    /**
     * Get the preview-only transform for a block position, or null if none.
     */
    public TransformationComponent getBlockTransform(BlockPos pos) {
        return blockTransforms.get(pos);
    }

    /**
     * Performs AABB raycasting against all visible blocks in the scene to find
     * which block the user clicked on. The nearest hit is selected.
     * The real world is never modified — only the preview selection changes.
     */
    public void handleClick(float mouseX, float mouseY, float screenWidth, float screenHeight,
            OrbitCameraController camera, float scale) {
        OrbitCameraController.PickRay ray = camera.createRay(mouseX, mouseY, screenWidth, screenHeight);

        float scaleValue = scale;

        // Transform ray into scene-local space
        Vector3f localOrigin = new Vector3f(ray.origin()).div(scaleValue).add(0.5f, 0.5f, 0.5f);
        Vector3f localDir = new Vector3f(ray.dir()).normalize();

        float closestDist = Float.MAX_VALUE;
        BlockPos closestPos = null;
        Minecraft client = Minecraft.getInstance();

        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                for (int z = -HALF_Z; z <= HALF_Z; z++) {
                    BlockPos worldPos = playerPos.offset(x, y, z);
                    BlockState state = client.level.getBlockState(worldPos);

                    if (state.isAir())
                        continue;
                    if (state.getRenderShape() != RenderShape.MODEL)
                        continue;

                    TransformationComponent transform = blockTransforms.get(worldPos);
                    Vector3f blockCenter;
                    if (transform != null) {
                        blockCenter = new Vector3f(transform.translation());
                    } else {
                        blockCenter = new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f);
                    }

                    Vector3f boxMin = new Vector3f(blockCenter).sub(0.5f, 0.5f, 0.5f);
                    Vector3f boxMax = new Vector3f(blockCenter).add(0.5f, 0.5f, 0.5f);

                    float t = GizmoMath.intersectRayAABB(localOrigin, localDir, boxMin, boxMax);
                    if (t >= 0 && t < closestDist) {
                        closestDist = t;
                        closestPos = worldPos;
                    }
                }
            }
        }

        if (closestPos != null) {
            selectBlock(closestPos);
        }
    }
}
