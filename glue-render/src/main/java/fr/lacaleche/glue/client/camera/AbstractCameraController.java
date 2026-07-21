package fr.lacaleche.glue.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public abstract class AbstractCameraController extends Camera {

    protected float fov = 70.0f;
    protected float depthFar = 512.0f;
    protected boolean isDragging = false;
    protected int dragButton = -1;
    protected float lastMouseX;
    protected float lastMouseY;

    public float getFov() { return fov; }
    public void setFov(float fov) { this.fov = fov; }

    public float getDepthFar() { return depthFar; }
    public void setDepthFar(float depthFar) { this.depthFar = depthFar; }

    public boolean isDragging() { return isDragging; }
    public int getDragButton() { return dragButton; }

    public void startDrag(float mouseX, float mouseY, int button) {
        isDragging = true;
        dragButton = button;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /**
     * Update the drag with the current mouse position.
     * Subclasses that need viewport height (e.g. for pan scale) should override
     * {@link #updateDrag(float, float, float)} instead.
     */
    public void updateDrag(float mouseX, float mouseY) {
        // no-op by default; override updateDrag(x, y, height) for height-aware cameras
    }

    /**
     * Update the drag with the current mouse position and the viewport height.
     * Override this for cameras whose pan/orbit scale depends on screen size.
     * The default delegates to the two-arg form.
     */
    public void updateDrag(float mouseX, float mouseY, float viewportHeight) {
        updateDrag(mouseX, mouseY);
    }

    public void stopDrag() {
        isDragging = false;
        dragButton = -1;
    }

    public void handleScroll(float scrollDelta) {}

    public boolean capturesMouseOnClick() { return false; }

    public void processCapturedInput(long windowHandle) {}

    public Matrix4f buildViewMatrix() {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(Axis.XP.rotationDegrees(this.getXRot()));
        matrices.mulPose(Axis.YP.rotationDegrees(this.getYRot() + 180.0f));
        Vec3 pos = this.getPosition();
        matrices.translate(-pos.x, -pos.y, -pos.z);
        return new Matrix4f(matrices.last().pose());
    }

    public void getViewMatrixArray(float[] output) {
        buildViewMatrix().get(output);
    }

    public Matrix4f buildProjectionMatrix(float width, float height) {
        return new Matrix4f().perspective(
                (float) Math.toRadians(fov),
                width / height,
                0.05f,
                depthFar);
    }

    protected void initializeFromGame() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            super.setup(client.level, client.player, true, false, 1.0f);
        }
    }

    public abstract void tick();

    public void cleanup() {}
}
