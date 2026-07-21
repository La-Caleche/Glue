package fr.lacaleche.glue.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;


public class FpsCameraController extends AbstractCameraController {

    private static final float ROTATION_SENSITIVITY = 0.3f;
    private static final float FAST_MULTIPLIER = 3.0f;

    private float moveSpeed = 0.1f;

    private float camYaw;
    private float camPitch;
    private Vec3 camPos;

    private double prevCursorX;
    private double prevCursorY;
    private boolean firstCapturedFrame = true;

    public float getMoveSpeed() {
        return moveSpeed;
    }

    public FpsCameraController(Entity entity) {
        this.camYaw = entity.getYRot();
        this.camPitch = Mth.clamp(entity.getXRot(), -90f, 90f);
        this.camPos = entity.getEyePosition(1.0f);
        initializeFromGame();
        applyState();
    }

    public FpsCameraController(Vec3 position, float yaw, float pitch) {
        this.camYaw = yaw;
        this.camPitch = Mth.clamp(pitch, -90f, 90f);
        this.camPos = position;
        initializeFromGame();
        applyState();
    }

    private void applyState() {
        super.setPosition(camPos);
        super.setRotation(camYaw, camPitch);
    }

    @Override
    public void tick() {
        applyState();
    }

    @Override
    public void updateDrag(float mouseX, float mouseY) {
        if (!isDragging) return;

        float deltaX = mouseX - lastMouseX;
        float deltaY = mouseY - lastMouseY;

        if (dragButton == 0) {
            mouseLook(deltaX, deltaY);
        } else if (dragButton == 1 || dragButton == 2) {
            float panScale = 0.02f;
            Vector3f right = new Vector3f(this.getLeftVector()).negate();
            Vector3f up = new Vector3f(this.getUpVector());

            camPos = camPos.add(
                    right.x * (-deltaX * panScale) + up.x * (deltaY * panScale),
                    right.y * (-deltaX * panScale) + up.y * (deltaY * panScale),
                    right.z * (-deltaX * panScale) + up.z * (deltaY * panScale)
            );
            applyState();
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void mouseLook(float deltaX, float deltaY) {
        camYaw += deltaX * ROTATION_SENSITIVITY;
        camPitch = Mth.clamp(camPitch + deltaY * ROTATION_SENSITIVITY, -90.0f, 90.0f);
        applyState();
    }

    public void move(float forward, float up, float right, boolean fast) {
        float speed = moveSpeed * (fast ? FAST_MULTIPLIER : 1.0f);

        Vector3f lookDir = new Vector3f(this.getLookVector());
        lookDir.y = 0;
        if (lookDir.lengthSquared() > 0.0001f) lookDir.normalize();

        Vector3f rightDir = new Vector3f(this.getLeftVector()).negate();
        rightDir.y = 0;
        if (rightDir.lengthSquared() > 0.0001f) rightDir.normalize();

        camPos = camPos.add(
                (lookDir.x * forward + rightDir.x * right) * speed,
                up * speed,
                (lookDir.z * forward + rightDir.z * right) * speed
        );
        applyState();
    }

    public void adjustSpeed(float scrollDelta) {
        float factor = (float) Math.pow(1.2, scrollDelta);
        moveSpeed = Mth.clamp(moveSpeed * factor, 0.01f, 10.0f);
    }

    @Override
    public void handleScroll(float scrollDelta) {
        adjustSpeed(scrollDelta);
    }

    @Override
    public boolean capturesMouseOnClick() {
        return true;
    }

    @Override
    public void processCapturedInput(long windowHandle) {
        double[] xBuf = new double[1];
        double[] yBuf = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, xBuf, yBuf);
        double cursorX = xBuf[0];
        double cursorY = yBuf[0];

        if (firstCapturedFrame) {
            prevCursorX = cursorX;
            prevCursorY = cursorY;
            firstCapturedFrame = false;
        }

        float deltaX = (float) (cursorX - prevCursorX);
        float deltaY = (float) (cursorY - prevCursorY);
        prevCursorX = cursorX;
        prevCursorY = cursorY;

        if (deltaX != 0 || deltaY != 0) {
            mouseLook(deltaX, deltaY);
        }

        float forward = 0, strafe = 0, vertical = 0;
        boolean sprint = false;

        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_W)) forward += 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_S)) forward -= 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_A)) strafe -= 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_D)) strafe += 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_SPACE)) vertical += 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT)) vertical -= 1;
        if (InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL)) sprint = true;

        if (forward != 0 || strafe != 0 || vertical != 0) {
            move(forward, vertical, strafe, sprint);
        }
    }

    @Override
    public void cleanup() {
        firstCapturedFrame = true;
    }

    @Override
    public Matrix4f buildViewMatrix() {
        Quaternionf rotation = this.rotation().conjugate(new Quaternionf());
        Matrix4f viewMatrix = new Matrix4f().rotation(rotation);
        viewMatrix.translate(-(float) camPos.x, -(float) camPos.y, -(float) camPos.z);
        return viewMatrix;
    }
}
