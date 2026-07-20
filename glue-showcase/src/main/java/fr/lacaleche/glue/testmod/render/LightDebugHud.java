package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.math.Color;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug overlay for the deferred colored-light subsystem
 * ({@link fr.lacaleche.glue.client.render.light.LightRenderer}).
 *
 * <p>Same interaction model as {@link PostEffectDebugHud}: a draggable panel,
 * arrow-key navigation, hold <b>ALT</b> to ungrab the mouse and drag/click.
 * It lists every active {@link Light}, and expanding one (Enter) reveals its
 * properties &mdash; color, intensity, range, position and (for cones)
 * yaw/pitch/cone angles &mdash; editable with <b>←/→</b> (hold SHIFT for
 * coarse steps). A companion {@link LightShapePreviewRenderer} draws each
 * light's shape in-world (sphere for point, cone for spot), highlighting the
 * expanded one.</p>
 *
 * <p>{@code Light} is immutable, so every edit builds a new instance and swaps
 * it through {@link Lumos}. That is not a workaround &mdash; it is how
 * the pipeline is meant to be driven: the shadow and glass caches key on light
 * identity, so the swap is exactly what invalidates them and triggers a re-bake
 * with the new parameters.</p>
 *
 * <p>The panel handles both kinds of light. A visual light (rows labelled {@code #n}) swaps locally
 * through {@link DemoLights}; a <b>world</b> light (rows labelled {@code W<id>}) is server-owned, so
 * every edit, delete, and the "place" action go through the {@link Lumos} request channel &mdash; the
 * server accepts them only from operators (permission level 4, the policy {@code Testmod} installs)
 * and the change shows once it syncs back. Selection of a world light follows its id, because each
 * sync replaces the local instance.</p>
 */
@Environment(EnvType.CLIENT)
public class LightDebugHud {

    public static final LightDebugHud INSTANCE = new LightDebugHud();

    private static final int PANEL_W = 260;
    private static final int HEADER_H = 28;
    private static final int LINE_H = 14;
    private static final int PADDING = 8;
    /** Width of the "◂ value ▸" zone on the right of a field row. */
    private static final int VALUE_W = 88;

    private boolean active;
    private int cursor;
    private int scroll;
    /** Ticks each watched key has been held, for edge + hold-to-repeat detection. */
    private final Map<Integer, Integer> holdTicks = new HashMap<>();

    /** The expanded light, or null. Identity — an edited light is re-pointed on swap. */
    private Light selected;
    /** The expanded light's server id when it is a world light, else -1; survives the sync swap. */
    private long selectedWorldId = -1;
    private Light paramsFor;
    private Params paramsCache;
    private boolean previewEnabled = true;

    private int panelX = -1;
    private int panelY = -1;

    private boolean mouseUngrabbed;
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;
    private double lastMouseX, lastMouseY;
    private boolean prevMouseDown;
    private int mouseHoldFrames;

    private LightDebugHud() {
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

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    /** The light whose properties are expanded in the panel, for the in-world preview. */
    public Light getSelectedLight() {
        return selected;
    }

    public void tick() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();

        // ALT hold → ungrab mouse (same behaviour as PostEffectDebugHud)
        boolean altDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        if (altDown && !mouseUngrabbed) {
            mouseUngrabbed = true;
            GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);
            lastMouseX = mx[0];
            lastMouseY = my[0];
        } else if (!altDown && mouseUngrabbed) {
            releaseMouseIfNeeded();
        }

        if (mouseUngrabbed) {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);

            double guiScale = mc.getWindow().getGuiScale();
            double guiMouseX = mx[0] / guiScale;
            double guiMouseY = my[0] / guiScale;

            boolean mouseDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (mouseDown && !dragging) {
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

        List<Row> rows = buildRows();

        // Keyboard nav (repeats while held, so long lists / big moves aren't a chore)
        if (repeat(win, GLFW.GLFW_KEY_UP)) cursor--;
        if (repeat(win, GLFW.GLFW_KEY_DOWN)) cursor++;
        cursor = Math.clamp(cursor, 0, rows.size() - 1);

        Row row = rows.get(cursor);

        if (edge(win, GLFW.GLFW_KEY_ENTER) || edge(win, GLFW.GLFW_KEY_KP_ENTER)) {
            activate(row);
        }
        if (edge(win, GLFW.GLFW_KEY_BACKSPACE) && row.kind == Kind.LIGHT) {
            deleteLight(row);
        }
        if (row.kind == Kind.FIELD) {
            boolean coarse = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (repeat(win, GLFW.GLFW_KEY_LEFT)) adjust(row, -1f, coarse);
            if (repeat(win, GLFW.GLFW_KEY_RIGHT)) adjust(row, 1f, coarse);
        }
    }


    private enum Kind { LIGHT, FIELD, ACTION }

    private enum Action { TOGGLE_PREVIEW, ADD_POINT, ADD_SPOT, PLACE_WORLD }

    private record Row(Kind kind, Light light, Field field, Action action, int lightIndex, long worldId) {
        static Row light(Light light, int index, long worldId) {
            return new Row(Kind.LIGHT, light, null, null, index, worldId);
        }

        static Row field(Light light, Field field, long worldId) {
            return new Row(Kind.FIELD, light, field, null, -1, worldId);
        }

        static Row action(Action action) {
            return new Row(Kind.ACTION, null, null, action, -1, -1);
        }
    }

    private List<Row> buildRows() {
        ClientLevel level = Minecraft.getInstance().level;
        List<Light> lights = level == null ? List.of() : Lumos.active(level);
        Map<Long, Light> world = level == null ? Map.of() : Lumos.lights(level);

        // The expanded light may have been removed behind our back — or, for a world light,
        // swapped for a fresh instance by a sync; its id finds the successor.
        if (selected != null && !containsIdentity(lights, selected)) {
            selected = selectedWorldId >= 0 ? world.get(selectedWorldId) : null;
            if (selected == null) selectedWorldId = -1;
        }

        List<Row> rows = new ArrayList<>();
        rows.add(Row.action(Action.TOGGLE_PREVIEW));

        int i = 0;
        for (Light light : lights) {
            long worldId = worldIdOf(world, light);
            rows.add(Row.light(light, i++, worldId));
            if (light == selected) {
                for (Field field : Field.values()) {
                    if (field.spotOnly && light.type == LightType.POINT) continue;
                    rows.add(Row.field(light, field, worldId));
                }
            }
        }

        rows.add(Row.action(Action.ADD_POINT));
        rows.add(Row.action(Action.ADD_SPOT));
        rows.add(Row.action(Action.PLACE_WORLD));
        return rows;
    }

    private static boolean containsIdentity(List<Light> lights, Light light) {
        for (Light l : lights) {
            if (l == light) return true;
        }
        return false;
    }

    /**
     * The server id of a synced world light, or -1 for a visual one. Identity comparison is exact:
     * the mirror renders the very instances {@code Lumos.lights} exposes.
     */
    private static long worldIdOf(Map<Long, Light> world, Light light) {
        for (Map.Entry<Long, Light> entry : world.entrySet()) {
            if (entry.getValue() == light) return entry.getKey();
        }
        return -1;
    }

    private void activate(Row row) {
        switch (row.kind) {
            case LIGHT -> {
                boolean deselect = (selected == row.light);
                selected = deselect ? null : row.light;
                selectedWorldId = deselect ? -1 : row.worldId;
            }
            case ACTION -> {
                switch (row.action) {
                    case TOGGLE_PREVIEW -> previewEnabled = !previewEnabled;
                    case ADD_POINT -> addLight(false);
                    case ADD_SPOT -> addLight(true);
                    case PLACE_WORLD -> placeWorldLight();
                }
            }
            default -> { }
        }
    }


    /**
     * One editable property. Getter/setter work on a mutable {@link Params}
     * snapshot; {@link #adjust} rebuilds the (immutable) light from it.
     */
    private enum Field {
        RED("Red", 0.05f, 0f, 1f, false, p -> p.r, (p, v) -> p.r = v),
        GREEN("Green", 0.05f, 0f, 1f, false, p -> p.g, (p, v) -> p.g = v),
        BLUE("Blue", 0.05f, 0f, 1f, false, p -> p.b, (p, v) -> p.b = v),
        INTENSITY("Intensity", 0.1f, 0f, 25f, false, p -> p.intensity, (p, v) -> p.intensity = v),
        RANGE("Range", 0.5f, 1f, 64f, false, p -> p.range, (p, v) -> p.range = v),
        SHADOW("Shadow", 1f, 0f, 1f, false, p -> p.castsShadow ? 1f : 0f, (p, v) -> p.castsShadow = v >= 0.5f),
        POS_X("Pos X", 0.25f, -3.0e7f, 3.0e7f, false, p -> (float) p.x, (p, v) -> p.x = v),
        POS_Y("Pos Y", 0.25f, -3.0e7f, 3.0e7f, false, p -> (float) p.y, (p, v) -> p.y = v),
        POS_Z("Pos Z", 0.25f, -3.0e7f, 3.0e7f, false, p -> (float) p.z, (p, v) -> p.z = v),
        YAW("Yaw", 2.5f, -1.0e6f, 1.0e6f, true, p -> p.yaw, (p, v) -> p.yaw = v),
        PITCH("Pitch", 2.5f, -89.9f, 89.9f, true, p -> p.pitch, (p, v) -> p.pitch = v),
        // Inner stops 0.5 short of outer's ceiling: adjust() pushes outer to inner + 0.5, and
        // Light.spot rejects outer >= 89, so an inner of 88.5 would build an illegal cone.
        INNER("Inner °", 1f, 0.5f, 88.0f, true, p -> p.inner, (p, v) -> p.inner = v),
        OUTER("Outer °", 1f, 0.5f, 88.5f, true, p -> p.outer, (p, v) -> p.outer = v);

        final String label;
        final float step;
        final float min;
        final float max;
        final boolean spotOnly;
        final Getter getter;
        final Setter setter;

        Field(String label, float step, float min, float max, boolean spotOnly, Getter getter, Setter setter) {
            this.label = label;
            this.step = step;
            this.min = min;
            this.max = max;
            this.spotOnly = spotOnly;
            this.getter = getter;
            this.setter = setter;
        }

        private interface Getter { float get(Params p); }

        private interface Setter { void set(Params p, float v); }
    }

    /**
     * A mutable, editing-friendly view of a {@link Light}: direction unpacked to
     * yaw/pitch (MC player convention, so a spot placed along the view direction
     * reads back the player's own angles), cone cosines unpacked to degrees.
     */
    private static final class Params {
        LightType type;
        double x, y, z;
        float r, g, b, intensity, range;
        float yaw, pitch, inner, outer;
        int goboTextureId;
        boolean castsShadow;

        static Params of(Light light) {
            Params p = new Params();
            p.type = light.type;
            p.x = light.x;
            p.y = light.y;
            p.z = light.z;
            p.r = light.r;
            p.g = light.g;
            p.b = light.b;
            p.intensity = light.intensity;
            p.range = light.range;
            p.pitch = (float) Math.toDegrees(Math.asin(Math.clamp(-light.directionY, -1.0, 1.0)));
            p.yaw = (float) Math.toDegrees(Math.atan2(-light.directionX, light.directionZ));
            p.inner = (float) Math.toDegrees(Math.acos(Math.clamp(light.cosInner, -1.0, 1.0)));
            p.outer = (float) Math.toDegrees(Math.acos(Math.clamp(light.cosOuter, -1.0, 1.0)));
            p.goboTextureId = light.goboTextureId;
            p.castsShadow = light.castsShadow;
            return p;
        }

        Light build() {
            float yawRad = (float) Math.toRadians(yaw);
            float pitchRad = (float) Math.toRadians(pitch);
            float xz = (float) Math.cos(pitchRad);
            float dx = (float) (-Math.sin(yawRad) * xz);
            float dy = (float) -Math.sin(pitchRad);
            float dz = (float) (Math.cos(yawRad) * xz);
            Light light = switch (type) {
                case POINT -> Light.point(x, y, z, r, g, b, intensity, range);
                case SPOT -> Light.spot(x, y, z, dx, dy, dz, r, g, b, intensity, range, inner, outer);
                case GOBO -> Light.gobo(x, y, z, dx, dy, dz, r, g, b, intensity, range, inner, outer, goboTextureId);
            };
            return light.withShadow(castsShadow);
        }
    }

    private void adjust(Row row, float direction, boolean coarse) {
        Params p = Params.of(row.light);
        Field f = row.field;
        float value = f.getter.get(p) + f.step * (coarse ? 5f : 1f) * direction;
        f.setter.set(p, Math.clamp(value, f.min, f.max));

        // Keep the cone well-formed: the angle being edited pushes the other.
        if (f == Field.INNER) p.outer = Math.max(p.outer, p.inner + 0.5f);
        if (f == Field.OUTER) p.inner = Math.min(p.inner, p.outer - 0.5f);
        if (f == Field.YAW) p.yaw = Mth.wrapDegrees(p.yaw);

        replaceLight(row, p.build());
    }

    /**
     * A visual light swaps instances directly. A world light can only be changed by asking the server,
     * so the swap arrives with the sync it broadcasts back &mdash; on a remote server the value trails
     * the keypress by the round trip, and nothing moves at all if the server refuses (not an operator).
     */
    private void replaceLight(Row row, Light newLight) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (row.worldId >= 0) {
            Lumos.update(level, row.worldId, newLight);
            return;
        }
        DemoLights.INSTANCE.replace(level, row.light, newLight);
        if (selected == row.light) selected = newLight;
    }

    private void deleteLight(Row row) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (row.worldId >= 0) {
            Lumos.remove(level, row.worldId);
        } else {
            DemoLights.INSTANCE.remove(level, row.light);
        }
        if (selected == row.light) {
            selected = null;
            selectedWorldId = -1;
        }
    }

    private void addLight(boolean spot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);

        Light light = spot
                ? Light.spot(eye.x, eye.y, eye.z,
                        (float) look.x, (float) look.y, (float) look.z,
                        1.0f, 0.95f, 0.85f, 3.0f, 22.0f, 20.0f, 32.0f)
                : Light.point(eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                        1.0f, 1.0f, 1.0f, 2.5f, 10.0f);

        DemoLights.INSTANCE.spawn(mc.player.level(), light);
        selected = light;
        selectedWorldId = -1;
    }

    /**
     * Asks the server for a world light ahead of the player: stored with the dimension, synced to
     * everyone in it, back on reload. It appears in the list once the server accepts and syncs it.
     */
    private void placeWorldLight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);

        Lumos.place(mc.player.level(), Light.point(
                eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                1.0f, 0.85f, 0.6f, 2.5f, 10.0f));
    }


    private void releaseMouseIfNeeded() {
        if (!mouseUngrabbed) return;
        mouseUngrabbed = false;
        dragging = false;

        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPos(win,
                mc.getWindow().getWidth() / 2.0,
                mc.getWindow().getHeight() / 2.0);
    }

    /** True exactly once per key press. */
    private boolean edge(long window, int key) {
        return pressState(window, key) == 1;
    }

    /** True on press, then every 2nd tick after ~0.4 s held (for nav / value scrubbing). */
    private boolean repeat(long window, int key) {
        int t = pressState(window, key);
        return t == 1 || (t > 8 && (t & 1) == 0);
    }

    /** 0 = up, otherwise the number of consecutive ticks the key has been down. */
    private int pressState(long window, int key) {
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        if (!down) {
            holdTicks.remove(key);
            return 0;
        }
        return holdTicks.merge(key, 1, Integer::sum);
    }


    public void render(GuiGraphics graphics) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        List<Row> rows = buildRows();
        cursor = Math.clamp(cursor, 0, rows.size() - 1);

        int maxVisible = Math.max(1, (screenH - 60 - HEADER_H) / LINE_H);
        int panelH = HEADER_H + Math.min(rows.size(), maxVisible) * LINE_H + PADDING;

        if (panelX < 0 || panelY < 0) {
            panelX = 20;
            panelY = (screenH - panelH) / 2;
        }

        // Background + border
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, 0xAA555522);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE111111);

        // Header (drag handle)
        int headerColor = dragging ? 0xFF4A4A1E : 0xFF2A2A0E;
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + HEADER_H, headerColor);
        graphics.drawString(font, "§l§eLight Debug", panelX + 8, panelY + 4, 0xFFFFFFFF);

        String hint = mouseUngrabbed
                ? "§a[ALT] Drag mode  §7[↑↓] Nav  [◂▸] Edit  [⏎] Open  [⌫] Del"
                : "§7[ALT] Drag  [↑↓] Nav  [◂▸] Edit  [⏎] Open  [⌫] Del";
        graphics.drawString(font, hint, panelX + 8, panelY + 16, 0xFF888888);

        // Scroll
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + maxVisible) scroll = cursor - maxVisible + 1;
        scroll = Math.max(0, scroll);

        // Mouse state (for hover/click when ungrabbed)
        long win = mc.getWindow().getWindow();
        double guiMX = -1, guiMY = -1;
        boolean mouseDown = false;
        if (mouseUngrabbed) {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);
            double guiScale = mc.getWindow().getGuiScale();
            guiMX = mx[0] / guiScale;
            guiMY = my[0] / guiScale;
            mouseDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            mouseHoldFrames = mouseDown ? mouseHoldFrames + 1 : 0;
        }
        boolean clickEdge = mouseDown && !prevMouseDown && !dragging;
        boolean clickRepeat = mouseDown && !dragging
                && (clickEdge || (mouseHoldFrames > 20 && mouseHoldFrames % 5 == 0));

        int y = panelY + HEADER_H + 2;
        for (int i = scroll; i < rows.size() && i < scroll + maxVisible; i++) {
            Row row = rows.get(i);
            boolean rowSelected = (i == cursor);

            if (rowSelected) {
                graphics.fill(panelX + 2, y - 1, panelX + PANEL_W - 2, y + LINE_H - 2, 0xFF3A3A1A);
            }

            boolean hovered = mouseUngrabbed
                    && guiMX >= panelX + 2 && guiMX <= panelX + PANEL_W - 2
                    && guiMY >= y - 1 && guiMY <= y + LINE_H - 2;
            if (hovered && !rowSelected) {
                graphics.fill(panelX + 2, y - 1, panelX + PANEL_W - 2, y + LINE_H - 2, 0x33FFFFFF);
            }

            switch (row.kind) {
                case LIGHT -> renderLightRow(graphics, font, row, y);
                case FIELD -> renderFieldRow(graphics, font, row, y, rowSelected);
                case ACTION -> renderActionRow(graphics, font, row, y);
            }

            if (hovered) {
                if (row.kind == Kind.FIELD) {
                    // Left/right half of the value zone = decrement/increment.
                    int zoneL = panelX + PANEL_W - VALUE_W - PADDING;
                    if (guiMX >= zoneL && clickRepeat) {
                        cursor = i;
                        boolean coarse = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        adjust(row, guiMX < zoneL + VALUE_W / 2.0 ? -1f : 1f, coarse);
                    } else if (clickEdge) {
                        cursor = i;
                    }
                } else if (clickEdge) {
                    cursor = i;
                    activate(row);
                }
            }

            y += LINE_H;
        }

        // Scrollbar
        if (rows.size() > maxVisible) {
            int trackH = maxVisible * LINE_H;
            int thumbH = Math.max(8, trackH * maxVisible / rows.size());
            int range = rows.size() - maxVisible;
            int thumbY = panelY + HEADER_H + 2 + (range > 0 ? (trackH - thumbH) * scroll / range : 0);
            graphics.fill(panelX + PANEL_W - 3, panelY + HEADER_H,
                    panelX + PANEL_W - 1, panelY + HEADER_H + trackH, 0xFF222222);
            graphics.fill(panelX + PANEL_W - 3, thumbY,
                    panelX + PANEL_W - 1, thumbY + thumbH, 0xFF666666);
        }

        // Cursor when ungrabbed
        if (mouseUngrabbed) {
            int cx = (int) guiMX;
            int cy = (int) guiMY;
            graphics.fill(cx, cy, cx + 1, cy + 8, 0xFFFFFFFF);
            graphics.fill(cx, cy, cx + 6, cy + 1, 0xFFFFFFFF);
        }

        prevMouseDown = mouseDown;
    }

    private void renderLightRow(GuiGraphics graphics, Font font, Row row, int y) {
        Light light = row.light;
        boolean expanded = (light == selected);

        // Color swatch
        int sw = swatchColor(light);
        graphics.fill(panelX + 6, y + 2, panelX + 14, y + 10, 0xFF000000);
        graphics.fill(panelX + 7, y + 3, panelX + 13, y + 9, sw);

        String arrow = expanded ? "§e▾ " : "§7▸ ";
        String name = row.worldId >= 0 ? "§bW" + row.worldId + "§f" : "§f#" + row.lightIndex;
        graphics.drawString(font, arrow + name + " " + light.type, panelX + 18, y + 2, 0xFFFFFFFF);

        String status = String.format("§7%.1f× %.0fb", light.intensity, light.range);
        graphics.drawString(font, status, panelX + PANEL_W - font.width(status) - PADDING, y + 2, 0xFFAAAAAA);
    }

    private void renderFieldRow(GuiGraphics graphics, Font font, Row row, int y, boolean rowSelected) {
        Field field = row.field;
        float value = field.getter.get(params(row.light));

        graphics.drawString(font, "§7" + field.label, panelX + 22, y + 2, 0xFFAAAAAA);

        String text = field == Field.SHADOW ? (value >= 0.5f ? "ON" : "OFF")
                : (field == Field.YAW || field == Field.PITCH || field == Field.INNER || field == Field.OUTER)
                ? String.format("%.1f", value)
                : String.format("%.2f", value);
        String display = rowSelected ? "§e◂ §f" + text + " §e▸" : "§f" + text;
        graphics.drawString(font, display, panelX + PANEL_W - font.width(display) - PADDING, y + 2, 0xFFFFFFFF);
    }

    private void renderActionRow(GuiGraphics graphics, Font font, Row row, int y) {
        switch (row.action) {
            case TOGGLE_PREVIEW -> {
                graphics.drawString(font, "§fShape preview", panelX + 6, y + 2, 0xFFFFFFFF);
                String status = previewEnabled ? "ON" : "OFF";
                int color = previewEnabled ? 0xFF55FF55 : 0xFF888888;
                graphics.drawString(font, status, panelX + PANEL_W - font.width(status) - PADDING, y + 2, color);
            }
            case ADD_POINT -> graphics.drawString(font, "§a+ Add point light §7(ahead)", panelX + 6, y + 2, 0xFFFFFFFF);
            case ADD_SPOT -> graphics.drawString(font, "§a+ Add spot light §7(from view)", panelX + 6, y + 2, 0xFFFFFFFF);
            case PLACE_WORLD -> graphics.drawString(font, "§b+ Place world light §7(saved, needs op)", panelX + 6, y + 2, 0xFFFFFFFF);
        }
    }

    /**
     * Every field row of a frame belongs to the one expanded light, and unpacking a light's direction
     * and cone costs four transcendentals. An edit swaps in a new instance, so identity is the key.
     */
    private Params params(Light light) {
        if (paramsFor != light) {
            paramsFor = light;
            paramsCache = Params.of(light);
        }
        return paramsCache;
    }

    private static int swatchColor(Light light) {
        return Color.ofRGBA(Math.clamp(light.r, 0f, 1f), Math.clamp(light.g, 0f, 1f),
                Math.clamp(light.b, 0f, 1f), 1f).getColor();
    }
}
