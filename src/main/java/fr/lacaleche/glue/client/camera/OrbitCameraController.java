package fr.lacaleche.glue.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class OrbitCameraController extends AbstractCameraController {

    private static final float DEFAULT_MIN_ZOOM = 0.5f;
    private static final float DEFAULT_MAX_ZOOM = 50.0f;
    private static final float ZOOM_SENSITIVITY = 0.2f;
    private static final float ROTATION_SENSITIVITY = 0.5f;

    private float minZoom = DEFAULT_MIN_ZOOM;
    private float maxZoom = DEFAULT_MAX_ZOOM;
    private boolean invertPanY = true;
    private float zoom = 5f;
    private Vector3f pivot = new Vector3f(0, 0, 0);

    public OrbitCameraController(Vector3f pivot) {
        this.pivot = new Vector3f(pivot);
        initializeFromGame();
        this.reset();
    }

    public OrbitCameraController(Vector3f pivot, Entity cameraEntity) {
        this.pivot = new Vector3f(pivot);
        initializeFromGame();
        this.setOrbitRotation(cameraEntity.getXRot(), cameraEntity.getYRot() + 180.0f);
        this.zoom = Mth.clamp(
                (float) cameraEntity.position().distanceTo(
                        new net.minecraft.world.phys.Vec3(pivot.x, pivot.y, pivot.z)),
                minZoom, maxZoom);
        this.updateCameraState();
    }

    public OrbitCameraController(Minecraft client, BlockPos pos) {
        this(new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f),
                client.getCameraEntity());
    }

    public float getMinZoom() { return minZoom; }
    public void setMinZoom(float minZoom) { this.minZoom = minZoom; }

    public float getMaxZoom() { return maxZoom; }
    public void setMaxZoom(float maxZoom) { this.maxZoom = maxZoom; }

    public boolean isInvertPanY() { return invertPanY; }
    public void setInvertPanY(boolean invertPanY) { this.invertPanY = invertPanY; }

    public float getZoom() { return zoom; }

    public Vector3f getPivot() { return pivot; }

    public void setPivot(Vector3f pivot) {
        this.pivot = new Vector3f(pivot);
        this.updateCameraState();
    }

    public float getRotationX() { return super.getXRot(); }
    public float getRotationY() { return super.getYRot(); }

    public void setOrbitRotation(float rotX, float rotY) {
        super.setRotation(rotY, Mth.clamp(rotX, -90.0f, 90.0f));
        this.updateCameraState();
    }

    public void setZoom(float zoom) {
        this.zoom = Mth.clamp(zoom, minZoom, maxZoom);
        this.updateCameraState();
    }

    @Override
    public void handleScroll(float scrollDelta) {
        zoom -= scrollDelta * ZOOM_SENSITIVITY;
        zoom = Mth.clamp(zoom, minZoom, maxZoom);
        this.updateCameraState();
    }

    @Override
    public void updateDrag(float mouseX, float mouseY, float viewportHeight) {
        if (!isDragging) return;

        float deltaX = mouseX - lastMouseX;
        float deltaY = mouseY - lastMouseY;

        if (dragButton == 0) {
            float newRotY = getRotationY() + deltaX * ROTATION_SENSITIVITY;
            float newRotX = Mth.clamp(getRotationX() + deltaY * ROTATION_SENSITIVITY, -90.0f, 90.0f);
            this.setOrbitRotation(newRotX, newRotY);
        } else if (dragButton == 1) {
            float scale = (zoom * 1.1547f) / viewportHeight;
            float panYSign = invertPanY ? 1 : -1;

            Vector3f right = new Vector3f(this.getLeftVector()).negate();
            Vector3f up = new Vector3f(this.getUpVector());

            pivot.add(
                    right.x * (-deltaX * scale) + up.x * (panYSign * deltaY * scale),
                    right.y * (-deltaX * scale) + up.y * (panYSign * deltaY * scale),
                    right.z * (-deltaX * scale) + up.z * (panYSign * deltaY * scale)
            );
            this.updateCameraState();
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public void tick() {
        updateCameraState();
    }

    public void reset() {
        zoom = 5f;
        isDragging = false;
        dragButton = -1;
        this.setOrbitRotation(30.0f, 45.0f);
    }

    /**
     * Repositions the camera to frame a bounding box, fitting pivot and zoom automatically.
     *
     * @param minX Scene-space AABB minimum X
     * @param minY Scene-space AABB minimum Y
     * @param minZ Scene-space AABB minimum Z
     * @param maxX Scene-space AABB maximum X
     * @param maxY Scene-space AABB maximum Y
     * @param maxZ Scene-space AABB maximum Z
     */
    public void fitToBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        setPivot(new Vector3f(cx, cy, cz));

        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5f;
        float distance = radius / (float) Math.tan(Math.toRadians(fov * 0.5));
        setZoom(Mth.clamp(distance, minZoom, maxZoom));
    }

    private void updateCameraState() {
        super.setPosition(pivot.x, pivot.y, pivot.z);
        super.move(-zoom, 0, 0);
    }

    @Override
    public Matrix4f buildViewMatrix() {
        PoseStack matrices = new PoseStack();
        matrices.translate(0, 0, -zoom);
        matrices.mulPose(Axis.XP.rotationDegrees(getRotationX()));
        matrices.mulPose(Axis.YP.rotationDegrees(getRotationY() + 180.0f));
        matrices.translate(-pivot.x, -pivot.y, -pivot.z);
        return new Matrix4f(matrices.last().pose());
    }

    public PickRay createRay(float mouseX, float mouseY, float screenWidth, float screenHeight) {
        float x = (2.0f * mouseX) / screenWidth - 1.0f;
        float y = 1.0f - (2.0f * mouseY) / screenHeight;

        Matrix4f invViewProj = new Matrix4f();
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(fov),
                screenWidth / screenHeight, 0.1f, 1000.0f);
        Matrix4f view = buildViewMatrix();

        proj.mul(view, invViewProj).invert();

        Vector4f rayStart = new Vector4f(x, y, -1.0f, 1.0f).mul(invViewProj);
        Vector4f rayEnd = new Vector4f(x, y, 1.0f, 1.0f).mul(invViewProj);

        rayStart.div(rayStart.w);
        rayEnd.div(rayEnd.w);

        Vector3f origin = new Vector3f(rayStart.x, rayStart.y, rayStart.z);
        Vector3f dir = new Vector3f(rayEnd.x, rayEnd.y, rayEnd.z).sub(origin).normalize();

        return new PickRay(origin, dir);
    }

    public record PickRay(Vector3f origin, Vector3f dir) {}
}
