package fr.lacaleche.glue.web.internal.host;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.web.WebPointerEvent;
import fr.lacaleche.glue.web.WebSurface;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Routes a host's Minecraft input to one surface drawn into a GUI rectangle: coordinate mapping,
 * held buttons, click counts, hover and cursor ownership. Client thread only.
 */
public final class SurfaceInput {

    private static final long MULTI_CLICK_NANOS = 500_000_000L;
    private static final int NO_POINTER = Integer.MIN_VALUE;

    private int left;
    private int top;
    private int width = 1;
    private int height = 1;
    private int buttons;
    private boolean hovered;
    private int pointerX = NO_POINTER;
    private int pointerY = NO_POINTER;
    private int lastButton = -1;
    private long lastClickAt;
    private double lastClickX;
    private double lastClickY;
    private int clicks = 1;

    public void setBounds(int x, int y, int width, int height) {
        this.left = x;
        this.top = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width && y >= this.top && y < this.top + this.height;
    }

    public boolean isCapturing() {
        return this.buttons != 0;
    }

    /** Reports hover changes; hosts call it on movement and may call it every frame. */
    public void move(WebSurface surface, double x, double y) {
        if (!isUsable(surface)) return;
        if (!this.contains(x, y) && !this.isCapturing()) {
            if (!this.hovered) return;
            this.hovered = false;
            this.pointerX = NO_POINTER;
            this.pointerY = NO_POINTER;
            surface.mouse(WebPointerEvent.EXITED, this.pageX(surface, x), this.pageY(surface, y), -1, modifiers(), 1);
            return;
        }

        int pageX = this.pageX(surface, x);
        int pageY = this.pageY(surface, y);
        if (this.hovered && pageX == this.pointerX && pageY == this.pointerY) return;
        this.hovered = true;
        this.pointerX = pageX;
        this.pointerY = pageY;
        surface.mouse(WebPointerEvent.MOVED, pageX, pageY, -1, modifiers(), 1);
    }

    public boolean press(WebSurface surface, double x, double y, int button) {
        if (!isUsable(surface) || button < 0 || button > 2 || !this.contains(x, y)) return false;
        long now = System.nanoTime();
        boolean repeated = button == this.lastButton && now - this.lastClickAt < MULTI_CLICK_NANOS
                && Math.abs(x - this.lastClickX) < 3 && Math.abs(y - this.lastClickY) < 3;
        this.clicks = repeated ? this.clicks % 3 + 1 : 1;
        this.lastButton = button;
        this.lastClickAt = now;
        this.lastClickX = x;
        this.lastClickY = y;
        this.buttons |= 1 << button;
        surface.mouse(WebPointerEvent.PRESSED, this.pageX(surface, x), this.pageY(surface, y), button, modifiers(), this.clicks);
        return true;
    }

    public boolean release(WebSurface surface, double x, double y, int button) {
        if (button < 0 || button > 2 || (this.buttons & 1 << button) == 0) return false;
        this.buttons &= ~(1 << button);
        if (isUsable(surface)) {
            surface.mouse(WebPointerEvent.RELEASED, this.pageX(surface, x), this.pageY(surface, y), button, modifiers(), this.clicks);
        }
        return true;
    }

    public boolean drag(WebSurface surface, double x, double y, int button) {
        if (!isUsable(surface) || button < 0 || button > 2 || (this.buttons & 1 << button) == 0) return false;
        this.pointerX = this.pageX(surface, x);
        this.pointerY = this.pageY(surface, y);
        surface.mouse(WebPointerEvent.DRAGGED, this.pointerX, this.pointerY, button, modifiers(), 1);
        return true;
    }

    /**
     * Releases buttons the window no longer holds. Screen containers only forward left-button
     * releases to their focused child, so embedded hosts reconcile the others every frame.
     */
    public void releaseStaleButtons(WebSurface surface, double x, double y) {
        if (this.buttons == 0) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int button = 0; button <= 2; button++) {
            if ((this.buttons & 1 << button) != 0 && GLFW.glfwGetMouseButton(window, button) != GLFW.GLFW_PRESS) {
                this.release(surface, x, y, button);
            }
        }
    }

    /** Minecraft reports wheel notches; Chromium expects 120 units per notch. */
    public boolean scroll(WebSurface surface, double x, double y, double deltaX, double deltaY) {
        if (!isUsable(surface) || !this.contains(x, y)) return false;
        surface.wheel(this.pageX(surface, x), this.pageY(surface, y), modifiers(),
                (int) Math.round(deltaX * 120), (int) Math.round(deltaY * 120));
        return true;
    }

    public void updateCursor(WebSurface surface, boolean ownsInput, double x, double y) {
        if (surface == null) return;
        surface.setCursorActive(ownsInput && !surface.isClosed() && (this.contains(x, y) || this.isCapturing()));
    }

    /** Forgets held buttons and hover, and releases the cursor. */
    public void reset(WebSurface surface) {
        this.buttons = 0;
        this.hovered = false;
        this.pointerX = NO_POINTER;
        this.pointerY = NO_POINTER;
        if (surface != null) surface.setCursorActive(false);
    }

    /** Current GLFW modifier bits, for input callbacks that do not provide them. */
    public static int modifiers() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        int modifiers = 0;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) modifiers |= GLFW.GLFW_MOD_ALT;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)) modifiers |= GLFW.GLFW_MOD_SUPER;
        return modifiers;
    }

    private int pageX(WebSurface surface, double x) {
        return (int) Math.floor((x - this.left) * surface.width() / this.width);
    }

    private int pageY(WebSurface surface, double y) {
        return (int) Math.floor((y - this.top) * surface.height() / this.height);
    }

    private static boolean isDown(long window, int left, int right) {
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }

    private static boolean isUsable(WebSurface surface) {
        return surface != null && !surface.isClosed();
    }
}
