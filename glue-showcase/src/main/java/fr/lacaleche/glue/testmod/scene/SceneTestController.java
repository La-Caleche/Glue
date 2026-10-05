package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.Gizmo;
import fr.lacaleche.glue.client.render.gizmo.GizmoPose;
import fr.lacaleche.glue.client.render.gizmo.GizmoTarget;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.history.HistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Map;

/** Selection, preview transforms and drag history. No operation writes to the world. */
public final class SceneTestController implements GizmoTarget {

    // The same inclusive region as the preview renderer.
    static final int HALF_X = 5;
    static final int HALF_Z = 5;
    static final int MIN_Y = -2;
    static final int MAX_Y = 3;

    private final BlockPos center;
    private final Gizmo gizmo;
    private final HistoryManager historyManager = new HistoryManager();
    private final Map<BlockPos, TransformationComponent> blockTransforms = new HashMap<>();
    private BlockPos selectedBlockPos;

    public SceneTestController(BlockPos center, Gizmo gizmo) {
        this.center = center.immutable();
        this.gizmo = gizmo;
    }

    @Override
    public GizmoPose pose() {
        TransformationComponent transform = this.blockTransforms.get(this.selectedBlockPos);
        Vector3f translation = transform.translation();
        return new GizmoPose(new Vector3d(translation.x, translation.y, translation.z), transform.leftRotation(),
                transform.scale());
    }

    @Override
    public void preview(GizmoPose pose) {
        if (this.selectedBlockPos == null) return;

        this.blockTransforms.put(this.selectedBlockPos, transform(pose));
    }

    @Override
    public void commit(GizmoPose before, GizmoPose after) {
        if (this.selectedBlockPos == null) return;

        this.historyManager.execute(new UpdateBlockCommand(this, this.selectedBlockPos, transform(before), transform(after)));
    }

    public HistoryManager getHistoryManager() {
        return this.historyManager;
    }

    public void undo() {
        this.historyManager.undo();
    }

    public void redo() {
        this.historyManager.redo();
    }

    public BlockPos getSelectedBlockPos() {
        return this.selectedBlockPos;
    }

    public TransformationComponent getBlockTransform(BlockPos position) {
        return this.blockTransforms.get(position);
    }

    /** Undo and redo may target a deselected block; the gizmo reads the selected one's transform each frame. */
    public void setTransform(BlockPos position, TransformationComponent transform) {
        this.blockTransforms.put(position.immutable(), transform);
    }

    public void selectBlock(BlockPos position) {
        this.selectedBlockPos = position.immutable();
        this.blockTransforms.computeIfAbsent(this.selectedBlockPos, block ->
                new TransformationComponent(new Vector3f(block.getX() - this.center.getX() + 0.5f,
                        block.getY() - this.center.getY() + 0.5f, block.getZ() - this.center.getZ() + 0.5f),
                        new Quaternionf(), new Vector3f(1), new Quaternionf()));
        this.gizmo.setTarget(this);
    }

    public void clearSelectedBlock() {
        this.selectedBlockPos = null;
        this.gizmo.setTarget(null);
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
                    float distance = intersectCube(origin, direction, blockCenter);
                    if (distance >= 0 && distance < closest) {
                        closest = distance;
                        picked = position;
                    }
                }
            }
        }
        if (picked != null) this.selectBlock(picked);
    }

    private static TransformationComponent transform(GizmoPose pose) {
        Vector3d position = pose.position();
        return new TransformationComponent(new Vector3f((float) position.x, (float) position.y, (float) position.z),
                pose.rotation(), pose.scale(), new Quaternionf());
    }

    /** The distance along a ray to a unit cube around a centre, or -1 when it misses. */
    private static float intersectCube(Vector3fc origin, Vector3fc direction, Vector3fc center) {
        float near = Float.NEGATIVE_INFINITY;
        float far = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float start = origin.get(axis);
            float step = direction.get(axis);
            float low = center.get(axis) - 0.5f;
            float high = center.get(axis) + 0.5f;
            if (Math.abs(step) < 1.0E-8f) {
                if (start < low || start > high) return -1;
                continue;
            }
            float first = (low - start) / step;
            float second = (high - start) / step;
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
        }
        if (far < Math.max(near, 0)) return -1;

        return Math.max(near, 0);
    }
}
