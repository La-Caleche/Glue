package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * GLFW Implementation of AbstractGizmoController.
 * Handles input bindings and rendering backend using GLFW/Minecraft APIs.
 */
public class GlfwGizmoController extends AbstractGizmoController {

    private final GlfwGizmoBackend backend = new GlfwGizmoBackend();

    private long windowHandle;
    private float mouseX;
    private float mouseY;

    private boolean leftMouseDownPrev = false;
    private boolean leftMouseClicked = false;

    public void setWindowHandle(long handle) {
        this.windowHandle = handle;
    }

    /**
     * Stores the current mouse position for use by getMouseX/getMouseY.
     * Should be called before manipulate() each frame.
     */
    public void updateMousePosition(float x, float y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    /**
     * Called once per frame to update click edge detection.
     * Must be called before manipulate() each frame.
     */
    public void updateFrame() {
        boolean leftMouseDownNow = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        leftMouseClicked = leftMouseDownNow && !leftMouseDownPrev;
        leftMouseDownPrev = leftMouseDownNow;
    }

    @Override
    protected float getMouseX() {
        return mouseX;
    }

    @Override
    protected float getMouseY() {
        return mouseY;
    }

    @Override
    protected boolean isLeftMouseDown() {
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    protected boolean isLeftMouseClicked() {
        return leftMouseClicked;
    }

    @Override
    protected boolean isShiftDown() {
        return InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    @Override
    protected boolean isControlDown() {
        return InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL);
    }

    @Override
    protected GizmoRenderBackend getRenderBackend() {
        return backend;
    }

    /**
     * Returns the render backend for flushing buffered draw commands.
     */
    public GlfwGizmoBackend getBackend() {
        return backend;
    }
}
