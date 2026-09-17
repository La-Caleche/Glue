package fr.lacaleche.glue.web.internal.browser;

import org.cef.input.CefKeyEvent;
import org.cef.input.CefMouseEvent;
import org.cef.misc.EventFlags;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

/** Direct GLFW-to-CEF events; no DOM synthesis, CDP input calls or game-tick input queue. */
final class CefInput {

    private CefInput() {
    }

    static int modifiers(int glfw, int buttons) {
        int flags = 0;
        if ((glfw & GLFW.GLFW_MOD_SHIFT) != 0) flags |= EventFlags.EVENTFLAG_SHIFT_DOWN;
        if ((glfw & GLFW.GLFW_MOD_CONTROL) != 0) flags |= EventFlags.EVENTFLAG_CONTROL_DOWN;
        if ((glfw & GLFW.GLFW_MOD_ALT) != 0) flags |= EventFlags.EVENTFLAG_ALT_DOWN;
        if ((glfw & GLFW.GLFW_MOD_SUPER) != 0) flags |= EventFlags.EVENTFLAG_COMMAND_DOWN;
        if ((glfw & GLFW.GLFW_MOD_CAPS_LOCK) != 0) flags |= EventFlags.EVENTFLAG_CAPS_LOCK_ON;
        if ((glfw & GLFW.GLFW_MOD_NUM_LOCK) != 0) flags |= EventFlags.EVENTFLAG_NUM_LOCK_ON;
        if ((buttons & 1) != 0) flags |= EventFlags.EVENTFLAG_LEFT_MOUSE_BUTTON;
        if ((buttons & 2) != 0) flags |= EventFlags.EVENTFLAG_RIGHT_MOUSE_BUTTON;
        if ((buttons & 4) != 0) flags |= EventFlags.EVENTFLAG_MIDDLE_MOUSE_BUTTON;
        return flags;
    }

    static int button(int button) {
        return switch (button) {
            case 0 -> CefMouseEvent.BUTTON_LEFT;
            case 1 -> CefMouseEvent.BUTTON_RIGHT;
            case 2 -> CefMouseEvent.BUTTON_MIDDLE;
            default -> CefMouseEvent.BUTTON_NONE;
        };
    }

    static CefKeyEvent key(int key, int scanCode, int modifiers, boolean released) {
        int virtual = virtualKey(key);
        int nativeCode = scanCode;
        if (Platform.get() == Platform.WINDOWS) {
            // The fork can map Windows scan codes to layout-specific VK values for printable keys.
            if (scanCode != 0 && key >= 32 && key <= 96) virtual = 0;
            boolean extended = scanCode > 255 || key >= GLFW.GLFW_KEY_INSERT && key <= GLFW.GLFW_KEY_END
                    || key == GLFW.GLFW_KEY_RIGHT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_ALT;
            nativeCode = CefKeyEvent.buildWindowsNativeKeyCode(scanCode & 255, extended, released);
        }
        return new CefKeyEvent(released ? CefKeyEvent.KEYEVENT_KEYUP : CefKeyEvent.KEYEVENT_RAWKEYDOWN,
                modifiers(modifiers, 0), virtual, nativeCode, (modifiers & GLFW.GLFW_MOD_ALT) != 0,
                '\0', '\0', scanCode);
    }

    static int virtualKey(int key) {
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F24) return 112 + key - GLFW.GLFW_KEY_F1;
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return 96 + key - GLFW.GLFW_KEY_KP_0;
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> 27;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 13;
            case GLFW.GLFW_KEY_TAB -> 9;
            case GLFW.GLFW_KEY_BACKSPACE -> 8;
            case GLFW.GLFW_KEY_DELETE -> 46;
            case GLFW.GLFW_KEY_INSERT -> 45;
            case GLFW.GLFW_KEY_LEFT -> 37;
            case GLFW.GLFW_KEY_UP -> 38;
            case GLFW.GLFW_KEY_RIGHT -> 39;
            case GLFW.GLFW_KEY_DOWN -> 40;
            case GLFW.GLFW_KEY_HOME -> 36;
            case GLFW.GLFW_KEY_END -> 35;
            case GLFW.GLFW_KEY_PAGE_UP -> 33;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 34;
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> 16;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> 17;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> 18;
            default -> key >= 32 && key <= 126 ? key : 0;
        };
    }
}
