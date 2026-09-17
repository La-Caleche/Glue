package fr.lacaleche.glue.testmod.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Drives the same {@code MouseHandler} and {@code KeyboardHandler} entry points GLFW calls, so a test
 * exercises coordinate mapping and screen dispatch rather than calling a widget directly.
 *
 * <p>The two {@code MouseHandler} entry points are private, so they are reached reflectively by
 * their Mojang-mapped names. That holds in the development environment this module is the only
 * consumer of; under production mappings the names are obfuscated and the lookup fails at class
 * initialization. Moving this into a shipped module would mean widening the two methods with an
 * access widener instead.</p>
 */
public final class RealInput {

    private static final Method ON_MOVE = handlerMethod("onMove", long.class, double.class, double.class);
    private static final Method ON_PRESS = handlerMethod("onPress", long.class, int.class, int.class, int.class);

    private RealInput() {
    }

    /** Moves the pointer to a position given in framebuffer pixels, as view coordinates report them. */
    public static void moveToFramebuffer(Minecraft client, double framebufferX, double framebufferY) {
        double screenX = framebufferX * client.getWindow().getScreenWidth() / client.getWindow().getWidth();
        double screenY = framebufferY * client.getWindow().getScreenHeight() / client.getWindow().getHeight();
        invoke(ON_MOVE, client.mouseHandler, client.getWindow().getWindow(), screenX, screenY);
    }

    public static void leftButton(Minecraft client, boolean pressed) {
        invoke(ON_PRESS, client.mouseHandler, client.getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT, pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    public static void key(Minecraft client, int key, boolean pressed) {
        client.keyboardHandler.keyPress(client.getWindow().getWindow(), key, 0,
                pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    /** A full press-and-release of one key. */
    public static void tap(Minecraft client, int key) {
        key(client, key, true);
        key(client, key, false);
    }

    private static Method handlerMethod(String name, Class<?>... parameters) {
        try {
            Method method = MouseHandler.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("MouseHandler entry point is missing: " + name, exception);
        }
    }

    private static void invoke(Method method, Object receiver, Object... arguments) {
        try {
            method.invoke(receiver, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Real input injection failed", exception);
        }
    }
}
