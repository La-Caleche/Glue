package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.Abstract3DController;
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

/** Selection, preview transforms and drag history. No operation writes to the world. */
public final class SceneTestController extends Abstract3DController {

    // The same inclusive region as the preview renderer.
    static final int HALF_X = 5;
    static final int HALF_Z = 5;
    static final int MIN_Y = -2;
    static final int MAX_Y = 3;

    private final BlockPos center;
    private final Map<BlockPos, TransformationComponent> blockTransforms = new HashMap<>();
    private BlockPos selectedBlockPos;
    private TransformationComponent initialDragTransform;

    public SceneTestController(BlockPos center, AbstractGizmoController gizmoController) {
        super(gizmoController);
        this.center = center.immutable();
    }

    @Override
    protected void onGizmoDragStart() {
        this.initialDragTransform = this.blockTransforms.get(this.selectedBlockPos);
    }

    @Override
    protected void onGizmoDragEnd() {
        TransformationComponent result = this.blockTransforms.get(this.selectedBlockPos);
        if (this.initialDragTransform != null && result != null && !this.initialDragTransform.equals(result)) {
            this.historyManager.execute(new UpdateBlockCommand(this, this.selectedBlockPos, this.initialDragTransform, result));
        }
        this.initialDragTransform = null;
    }

    @Override
    public void applyGizmoTransform() {
        if (this.selectedBlockPos == null) return;
        this.blockTransforms.put(this.selectedBlockPos, new TransformationComponent(
                new Vector3f(this.gizmoController.getTranslation()), new Quaternionf(this.gizmoController.getLeftRotation()),
                new Vector3f(this.gizmoController.getScale()), new Quaternionf(this.gizmoController.getRightRotation())));
    }

    public BlockPos getSelectedBlockPos() {
        return this.selectedBlockPos;
    }

    public TransformationComponent getBlockTransform(BlockPos position) {
        return this.blockTransforms.get(position);
    }

    /** Undo/redo may target a deselected block; only the selected block moves the visible gizmo. */
    public void setGizmo(BlockPos position, TransformationComponent transform) {
        this.blockTransforms.put(position.immutable(), transform);
        if (position.equals(this.selectedBlockPos)) this.gizmoController.recomposeMatrix(transform);
    }

    public void selectBlock(BlockPos position) {
        this.selectedBlockPos = position.immutable();
        TransformationComponent transform = this.blockTransforms.computeIfAbsent(this.selectedBlockPos, block ->
                new TransformationComponent(new Vector3f(block.getX() - this.center.getX() + 0.5f,
                        block.getY() - this.center.getY() + 0.5f, block.getZ() - this.center.getZ() + 0.5f),
                        new Quaternionf(), new Vector3f(1), new Quaternionf()));
        this.gizmoController.recomposeMatrix(transform);
    }

    public void clearSelectedBlock() {
        this.selectedBlockPos = null;
    }

    /** Picks the nearest unit cube at its preview translation; rotation and scale do not affect picking. */
    public void handleClick(float mouseX, float mouseY, float width, float height, OrbitCameraController camera, float scale) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        OrbitCameraController.PickRay ray = camera.createRay(mouseX, mouseY, width, height);
        Vector3f origin = new Vector3f(ray.origin()).div(scale).add(0.5f, 0.5f, 0.5f);
        Vector3f direction = new Vector3f(ray.dir()).normalize();
        float closest = Float.MAX_VALUE;
        BlockPos picked = null;
        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                for (int z = -HALF_Z; z <= HALF_Z; z++) {
                    BlockPos position = this.center.offset(x, y, z);
                    BlockState state = client.level.getBlockState(position);
                    if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                    TransformationComponent transform = this.blockTransforms.get(position);
                    Vector3f blockCenter = transform == null ? new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f)
                            : new Vector3f(transform.translation());
                    float distance = GizmoMath.intersectRayAABB(origin, direction,
                            new Vector3f(blockCenter).sub(0.5f, 0.5f, 0.5f), new Vector3f(blockCenter).add(0.5f, 0.5f, 0.5f));
                    if (distance >= 0 && distance < closest) {
                        closest = distance;
                        picked = position;
                    }
                }
            }
        }
        if (picked != null) this.selectBlock(picked);
    }
}
