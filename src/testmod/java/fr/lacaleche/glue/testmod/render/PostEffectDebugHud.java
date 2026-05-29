package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.TimedPostEffect;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Debug overlay for post-processing effects.
 *
 * <p>Provides a draggable panel listing every registered toggle and timed
 * effect. Navigate with arrow keys, trigger/toggle with Enter, stop active
 * effects with Backspace.</p>
 *
 * <p>Hold <b>ALT</b> to ungrab the mouse and drag the panel. Releasing ALT
 * re-grabs the mouse. Player movement keys still work while ALT is held.</p>
 */
@Environment(EnvType.CLIENT)
public class PostEffectDebugHud {

    public static final PostEffectDebugHud INSTANCE = new PostEffectDebugHud();

    private static final int PANEL_W = 240;
    private static final int HEADER_H = 28;
    private static final int LINE_H = 16;
    private static final int PADDING = 8;

    private boolean active;
    private int cursor;
    private int scroll;
    private final Set<Integer> keysDown = new HashSet<>();
    private final List<EffectEntry> entries = new ArrayList<>();

    private int panelX = -1;
    private int panelY = -1;

    private boolean mouseUngrabbed;
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;
    private double lastMouseX, lastMouseY;

    private PostEffectDebugHud() {
    }

    /**
     * Registers all known effects. Call once during mod init.
     */
    public void init() {
        entries.add(new EffectEntry("Blur", EffectKind.TOGGLE, TestShaders.BLUR, null));
        entries.add(new EffectEntry("Grayscale", EffectKind.TOGGLE, TestShaders.GRAYSCALE, null));

        entries.add(new EffectEntry("Departure Vortex", EffectKind.TIMED, null, TestPostShaderHandler.DEPARTURE));
        entries.add(new EffectEntry("Arrival Shockwave", EffectKind.TIMED, null, TestPostShaderHandler.ARRIVAL));
        entries.add(new EffectEntry("Denial Pulse", EffectKind.TIMED, null, TestPostShaderHandler.DENIAL));
        entries.add(new EffectEntry("Sun Surface", EffectKind.TIMED, null, TestPostShaderHandler.SUN));
        entries.add(new EffectEntry("Chromatic Aberration", EffectKind.TIMED, null, TestPostShaderHandler.CHROMATIC));
        entries.add(new EffectEntry("Shattered Screen", EffectKind.TIMED, null, TestPostShaderHandler.SHATTERED));
        entries.add(new EffectEntry("Impact Frame", EffectKind.TIMED, null, TestPostShaderHandler.IMPACT));
    }

    public void toggle() {
        active = !active;
        if (active) {
            cursor = 0;
            scroll = 0;
            panelX = -1;
            panelY = -1;

            if (this.lastMouseX > 0 && this.lastMouseY > 0) {
                GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), this.lastMouseX, this.lastMouseY);
            }
        } else {
            releaseMouseIfNeeded();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void tick() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();

        // ALT hold → ungrab mouse
        boolean altDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        if (altDown && !mouseUngrabbed) {
            mouseUngrabbed = true;
            GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

            // Warp cursor to center of panel header for convenience
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);
            lastMouseX = mx[0];
            lastMouseY = my[0];
        } else if (!altDown && mouseUngrabbed) {
            releaseMouseIfNeeded();
        }

        // Handle dragging
        if (mouseUngrabbed) {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);

            double guiScale = mc.getWindow().getGuiScale();
            double guiMouseX = mx[0] / guiScale;
            double guiMouseY = my[0] / guiScale;

            boolean mouseDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (mouseDown && !dragging) {
                // Check if mouse is over the header
                if (guiMouseX >= panelX && guiMouseX <= panelX + PANEL_W
                        && guiMouseY >= panelY && guiMouseY <= panelY + HEADER_H) {
                    dragging = true;
                    dragOffsetX = guiMouseX - panelX;
                    dragOffsetY = guiMouseY - panelY;
                }
            }

            if (dragging) {
                panelX = (int) (guiMouseX - dragOffsetX);
                panelY = (int) (guiMouseY - dragOffsetY);

                // Clamp to screen
                int screenW = mc.getWindow().getGuiScaledWidth();
                int screenH = mc.getWindow().getGuiScaledHeight();
                panelX = Math.clamp(panelX, 0, screenW - PANEL_W);
                panelY = Math.clamp(panelY, 0, screenH - 40);
            }

            if (!mouseDown) {
                dragging = false;
            }

            lastMouseX = mx[0];
            lastMouseY = my[0];
        }

        // Keyboard nav
        if (edge(win, GLFW.GLFW_KEY_UP)) cursor--;
        if (edge(win, GLFW.GLFW_KEY_DOWN)) cursor++;
        cursor = Math.clamp(cursor, 0, entries.size() - 1);

        if (edge(win, GLFW.GLFW_KEY_ENTER) || edge(win, GLFW.GLFW_KEY_KP_ENTER)) {
            activateEntry(entries.get(cursor));
        }
        if (edge(win, GLFW.GLFW_KEY_BACKSPACE)) {
            stopEntry(entries.get(cursor));
        }
    }

    private void releaseMouseIfNeeded() {
        if (!mouseUngrabbed) return;
        mouseUngrabbed = false;
        dragging = false;

        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        // Re-center cursor so camera doesn't snap
        GLFW.glfwSetCursorPos(win,
                mc.getWindow().getWidth() / 2.0,
                mc.getWindow().getHeight() / 2.0);
    }

    private boolean edge(long window, int key) {
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        if (down && keysDown.add(key)) return true;
        if (!down) keysDown.remove(key);
        return false;
    }

    private void activateEntry(EffectEntry entry) {
        switch (entry.kind) {
            case TOGGLE -> TestPostShaderHandler.INSTANCE.toggleByHandle(entry.handle);
            case TIMED -> { if (entry.timed != null) entry.timed.trigger(); }
        }
    }

    private void stopEntry(EffectEntry entry) {
        if (entry.kind == EffectKind.TIMED && entry.timed != null) {
            entry.timed.stop();
        }
    }

    public void render(GuiGraphics graphics) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int maxVisible = Math.max(1, (screenH - 60 - HEADER_H) / LINE_H);
        int panelH = HEADER_H + Math.min(entries.size(), maxVisible) * LINE_H + PADDING;

        // Default position: centered
        if (panelX < 0 || panelY < 0) {
            panelX = (screenW - PANEL_W) / 2;
            panelY = (screenH - panelH) / 2;
        }

        // Background + border
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, 0xAA333355);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE111111);

        // Header (drag handle)
        int headerColor = dragging ? 0xFF2A2A5E : 0xFF1A1A2E;
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + HEADER_H, headerColor);
        graphics.drawString(font, "§l§dPost Effect Debug", panelX + 8, panelY + 4, 0xFFFFFFFF);

        String hint = mouseUngrabbed
                ? "§a[ALT] Drag mode  §7[↑↓] Nav  [⏎] Fire  [⌫] Stop"
                : "§7[ALT] Drag  [↑↓] Nav  [⏎] Fire  [⌫] Stop";
        graphics.drawString(font, hint, panelX + 8, panelY + 16, 0xFF888888);

        // Scroll
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + maxVisible) scroll = cursor - maxVisible + 1;
        scroll = Math.max(0, scroll);

        // Entries
        int y = panelY + HEADER_H + 2;
        for (int i = scroll; i < entries.size() && i < scroll + maxVisible; i++) {
            EffectEntry entry = entries.get(i);
            boolean selected = (i == cursor);

            if (selected) {
                graphics.fill(panelX + 2, y - 1, panelX + PANEL_W - 2, y + LINE_H - 2, 0xFF2A2A4A);
            }

            String status = getStatus(entry);
            int statusColor = getStatusColor(entry);

            String kindTag = entry.kind == EffectKind.TOGGLE ? "§8[T] " : "§8[⚡] ";
            String pointer = selected ? "§f▸ " : "  ";
            String label = pointer + kindTag + "§f" + entry.name;

            graphics.drawString(font, label, panelX + 6, y + 2, 0xFFFFFFFF);
            graphics.drawString(font, status, panelX + PANEL_W - font.width(status) - 8, y + 2, statusColor);

            // Click support when mouse is ungrabbed
            if (mouseUngrabbed) {
                double[] mx = new double[1], my = new double[1];
                long win = mc.getWindow().getWindow();
                GLFW.glfwGetCursorPos(win, mx, my);
                double guiScale = mc.getWindow().getGuiScale();
                double guiMX = mx[0] / guiScale;
                double guiMY = my[0] / guiScale;

                if (guiMX >= panelX + 2 && guiMX <= panelX + PANEL_W - 2
                        && guiMY >= y - 1 && guiMY <= y + LINE_H - 2) {
                    // Highlight hovered entry
                    if (!selected) {
                        graphics.fill(panelX + 2, y - 1, panelX + PANEL_W - 2, y + LINE_H - 2, 0x33FFFFFF);
                    }
                    // Click to activate
                    if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                            && !dragging) {
                        cursor = i;
                        activateEntry(entry);
                    }
                }
            }

            y += LINE_H;
        }

        // Scrollbar
        if (entries.size() > maxVisible) {
            int trackH = maxVisible * LINE_H;
            int thumbH = Math.max(8, trackH * maxVisible / entries.size());
            int range = entries.size() - maxVisible;
            int thumbY = panelY + HEADER_H + 2 + (range > 0 ? (trackH - thumbH) * scroll / range : 0);
            graphics.fill(panelX + PANEL_W - 3, panelY + HEADER_H,
                    panelX + PANEL_W - 1, panelY + HEADER_H + trackH, 0xFF222222);
            graphics.fill(panelX + PANEL_W - 3, thumbY,
                    panelX + PANEL_W - 1, thumbY + thumbH, 0xFF666666);
        }

        // Draw cursor when ungrabbed
        if (mouseUngrabbed) {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), mx, my);
            double guiScale = mc.getWindow().getGuiScale();
            int cx = (int) (mx[0] / guiScale);
            int cy = (int) (my[0] / guiScale);
            graphics.fill(cx, cy, cx + 1, cy + 8, 0xFFFFFFFF);
            graphics.fill(cx, cy, cx + 6, cy + 1, 0xFFFFFFFF);
        }
    }

    private String getStatus(EffectEntry entry) {
        return switch (entry.kind) {
            case TOGGLE -> TestPostShaderHandler.INSTANCE.isToggled(entry.handle) ? "ON" : "OFF";
            case TIMED -> (entry.timed != null && entry.timed.isActive()) ? "▶ PLAYING" : "READY";
        };
    }

    private int getStatusColor(EffectEntry entry) {
        return switch (entry.kind) {
            case TOGGLE -> TestPostShaderHandler.INSTANCE.isToggled(entry.handle) ? 0xFF55FF55 : 0xFF888888;
            case TIMED -> (entry.timed != null && entry.timed.isActive()) ? 0xFFFFAA00 : 0xFF55FF55;
        };
    }

    private enum EffectKind { TOGGLE, TIMED }

    private record EffectEntry(String name, EffectKind kind,
                               PostShaderHandle handle, TimedPostEffect timed) {
    }
}
