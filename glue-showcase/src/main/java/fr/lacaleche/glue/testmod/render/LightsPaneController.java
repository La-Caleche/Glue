package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backs both light debug panes — the "Lights" list ({@code assets/mcsx/ui/debug/lights.mcsx}) and
 * the "Properties" editor ({@code assets/mcsx/ui/debug/properties.mcsx}) bind this one shared
 * instance. Rows list every active {@link Light} — visual ({@code #n}) and server-owned world
 * lights ({@code W<id>}) alike; clicking one loads the editor pane, whose sliders rebuild the
 * immutable light and swap it through {@link Lumos}. That swap is the intended invalidation path: the shadow and glass caches key on
 * light identity, so the new instance is what triggers the re-bake.
 *
 * <p>Threading follows the MCSX contract. {@link GlueDebugDock}'s tick pump gathers the light set
 * on the client thread and applies it to signals on the UI thread; edits run the opposite way, the
 * slider {@link Effect} capturing values on the UI thread and hopping to the client thread to call
 * {@link Lumos}. A world-light edit takes a server round trip: the sync swaps in a fresh instance
 * and the selection follows its id.</p>
 *
 * <p>{@link LightShapePreviewRenderer} reads the selection and preview flag from volatile mirrors,
 * never from the signals — the reactive runtime is single-threaded.</p>
 */
@Environment(EnvType.CLIENT)
public final class LightsPaneController extends ScreenController {

    /** One list row. Display values only, so the keyed {@code <for>} reuses unchanged rows. */
    public record LightRow(String swatch, String name, String type, String meta,
                           String rowClasses, int index) {
    }

    /** What one client tick gathered; applied to signals on the UI thread. */
    record Snapshot(List<Light> lights, Map<Long, Light> world) {
    }

    /** Grace ticks a just-swapped visual selection survives the pump seeing a stale light list. */
    private static final int SWAP_GRACE_TICKS = 2;

    /** Written on the UI thread at pane bind, read by the client-thread tick pump. */
    @Nullable
    private static volatile LightsPaneController active;

    private final Signal<List<LightRow>> rows = signal(List.of());
    private final Signal<Boolean> preview = signal(true);
    private final Signal<Boolean> hasSelection = signal(false);
    private final Computed<Boolean> noSelection = computed(() -> !hasSelection.get());
    private final Signal<Boolean> spotSelected = signal(false);
    private final Signal<String> selectionTitle = signal("");

    private final Signal<Integer> red = signal(100);
    private final Signal<Integer> green = signal(100);
    private final Signal<Integer> blue = signal(100);
    /** Tenths: 25 = intensity 2.5. */
    private final Signal<Integer> intensity = signal(25);
    private final Signal<Integer> range = signal(10);
    private final Signal<Boolean> castsShadow = signal(true);
    private final Signal<Integer> yaw = signal(0);
    private final Signal<Integer> pitch = signal(0);
    private final Signal<Integer> inner = signal(20);
    private final Signal<Integer> outer = signal(32);

    private final Computed<Float> redFraction = computed(() -> red.get() / 100f);
    private final Computed<Float> greenFraction = computed(() -> green.get() / 100f);
    private final Computed<Float> blueFraction = computed(() -> blue.get() / 100f);
    private final Computed<Float> intensityFraction = computed(() -> intensity.get() / 250f);
    private final Computed<Float> rangeFraction = computed(() -> (range.get() - 1) / 63f);
    private final Computed<Float> yawFraction = computed(() -> (yaw.get() + 180) / 360f);
    private final Computed<Float> pitchFraction = computed(() -> (pitch.get() + 89) / 178f);
    private final Computed<Float> innerFraction = computed(() -> (inner.get() - 1) / 86f);
    private final Computed<Float> outerFraction = computed(() -> (outer.get() - 2) / 86f);
    private final Computed<String> intensityLabel =
            computed(() -> String.format(Locale.ROOT, "%.1f", intensity.get() * 0.1f));

    private List<Light> lights = List.of();
    private Map<Long, Light> world = Map.of();
    @Nullable
    private Light selected;
    private long selectedWorldId = -1;
    /** True while {@link #loadEditor} writes the signals, so the edit effect stays quiet. */
    private boolean loading;
    private int swapGrace;

    /** Read by the in-world preview on the render thread; the signals are UI-thread-only. */
    @Nullable
    private volatile Light selectedMirror;
    private volatile boolean previewMirror = true;

    public LightsPaneController() {
        active = this;
        Effect.of(() -> previewMirror = preview.get());
        Effect.of(this::applyEdit);
    }

    @Nullable
    static LightsPaneController active() {
        return active;
    }

    static void deactivate() {
        active = null;
    }

    /** Whether the in-world wireframes should draw: pane open and its Preview switch on. */
    public static boolean previewEnabled() {
        LightsPaneController controller = active;
        return controller != null && controller.previewMirror;
    }

    /** The light to highlight in the in-world preview, or null. */
    @Nullable
    public static Light selectedLight() {
        LightsPaneController controller = active;
        return controller == null ? null : controller.selectedMirror;
    }

    /** Client thread. Copies, because the UI thread must not touch the live world-light view. */
    static Snapshot snapshot() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new Snapshot(List.of(), Map.of());
        return new Snapshot(Lumos.active(level), new LinkedHashMap<>(Lumos.lights(level)));
    }

    /** UI thread. */
    void applySnapshot(Snapshot snapshot) {
        lights = snapshot.lights();
        world = snapshot.world();
        if (selected != null && !containsIdentity(lights, selected)) {
            if (selectedWorldId >= 0) {
                // A world-light edit came back from the server as a fresh instance under the same id.
                selected = world.get(selectedWorldId);
                if (selected == null) selectedWorldId = -1;
                else loadEditor(selected);
            } else if (swapGrace > 0) {
                // A visual swap we made is still in flight to the client thread; keep the selection.
                swapGrace--;
            } else {
                selected = null;
            }
        }
        selectedMirror = selected;
        rebuildRows();
    }

    private void rebuildRows() {
        List<LightRow> next = new ArrayList<>(lights.size());
        for (int i = 0; i < lights.size(); i++) {
            Light light = lights.get(i);
            long worldId = worldIdOf(light);
            next.add(new LightRow(swatch(light),
                    worldId >= 0 ? "W" + worldId : "#" + i,
                    light.type.name(),
                    String.format(Locale.ROOT, "%.1f× %.0fb", light.intensity, light.range),
                    light == selected ? "bg-accent-subtle" : "",
                    i));
        }
        if (!next.equals(rows.get())) rows.set(List.copyOf(next));
        hasSelection.set(selected != null);
        spotSelected.set(selected != null && selected.type != LightType.POINT);
        selectionTitle.set(selected == null ? ""
                : (selectedWorldId >= 0 ? "W" + selectedWorldId : "Visual") + " · " + selected.type);
    }

    private void select(LightRow row) {
        if (row.index() >= lights.size()) return;
        Light light = lights.get(row.index());
        if (light == selected) {
            selected = null;
            selectedWorldId = -1;
        } else {
            selected = light;
            selectedWorldId = worldIdOf(light);
            loadEditor(light);
        }
        selectedMirror = selected;
        rebuildRows();
    }

    /**
     * Signal writes re-run the edit effect synchronously, so the whole load happens under the
     * {@code loading} guard. Values are quantized into the sliders' integer ranges.
     */
    private void loadEditor(Light light) {
        loading = true;
        try {
            red.set(Math.clamp(Math.round(light.r * 100f), 0, 100));
            green.set(Math.clamp(Math.round(light.g * 100f), 0, 100));
            blue.set(Math.clamp(Math.round(light.b * 100f), 0, 100));
            intensity.set(Math.clamp(Math.round(light.intensity * 10f), 0, 250));
            range.set(Math.clamp(Math.round(light.range), 1, 64));
            castsShadow.set(light.castsShadow);
            pitch.set(Math.clamp(Math.round(
                    (float) Math.toDegrees(Math.asin(Math.clamp(-light.directionY, -1.0, 1.0)))), -89, 89));
            yaw.set(Math.clamp(Math.round(
                    (float) Math.toDegrees(Math.atan2(-light.directionX, light.directionZ))), -180, 180));
            inner.set(Math.clamp(Math.round(
                    (float) Math.toDegrees(Math.acos(Math.clamp(light.cosInner, -1.0, 1.0)))), 1, 87));
            outer.set(Math.clamp(Math.round(
                    (float) Math.toDegrees(Math.acos(Math.clamp(light.cosOuter, -1.0, 1.0)))), 2, 88));
        } finally {
            loading = false;
        }
    }

    /**
     * The one edit effect. Every editor signal is read first — unconditionally, because an early
     * return before the reads would drop the effect's dependencies — then a user edit rebuilds the
     * light and swaps it on the client thread.
     */
    private void applyEdit() {
        int r = red.get();
        int g = green.get();
        int b = blue.get();
        int it = intensity.get();
        int rg = range.get();
        boolean shadow = castsShadow.get();
        int yw = yaw.get();
        int pt = pitch.get();
        int in = inner.get();
        int out = outer.get();
        if (loading || selected == null) return;

        Light current = selected;
        long worldId = selectedWorldId;
        Light next = build(current, r, g, b, it, rg, shadow, yw, pt, in, out);
        if (next == null || sameLight(current, next)) return;

        if (worldId >= 0) {
            Minecraft.getInstance().execute(() -> {
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) Lumos.update(level, worldId, next);
            });
        } else {
            // Optimistic: the same instance lands in the manager, so identity survives the pump.
            selected = next;
            selectedMirror = next;
            swapGrace = SWAP_GRACE_TICKS;
            Minecraft.getInstance().execute(() -> {
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) DemoLights.INSTANCE.replace(level, current, next);
            });
        }
    }

    @Nullable
    private static Light build(Light current, int r, int g, int b, int intensityTenths, int range,
                               boolean shadow, int yaw, int pitch, int inner, int outer) {
        float rf = r / 100f;
        float gf = g / 100f;
        float bf = b / 100f;
        float it = intensityTenths / 10f;
        try {
            Light built;
            if (current.type == LightType.POINT) {
                built = Light.point(current.x, current.y, current.z, rf, gf, bf, it, range);
            } else {
                float yawRad = (float) Math.toRadians(yaw);
                float pitchRad = (float) Math.toRadians(pitch);
                float xz = (float) Math.cos(pitchRad);
                float dx = (float) (-Math.sin(yawRad) * xz);
                float dy = (float) -Math.sin(pitchRad);
                float dz = (float) (Math.cos(yawRad) * xz);
                int innerClamped = Math.min(inner, outer - 1);
                built = current.type == LightType.GOBO
                        ? Light.gobo(current.x, current.y, current.z, dx, dy, dz, rf, gf, bf, it,
                                range, innerClamped, outer, current.goboTextureId)
                        : Light.spot(current.x, current.y, current.z, dx, dy, dz, rf, gf, bf, it,
                                range, innerClamped, outer);
            }
            return built.withShadow(shadow);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void addPoint() {
        spawnAhead(false);
    }

    private void addSpot() {
        spawnAhead(true);
    }

    private void spawnAhead(boolean spot) {
        Minecraft.getInstance().execute(() -> {
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
        });
    }

    /** World point light ahead of the player: saved, synced, op-gated — it appears once the server syncs. */
    private void placeWorldPoint() {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0f);
            Lumos.place(mc.player.level(), Light.point(
                    eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                    1.0f, 0.85f, 0.6f, 2.5f, 10.0f));
        });
    }

    /** World spot light ahead of the player: saved, synced, op-gated — it appears once the server syncs. */
    private void placeWorldSpot() {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0f);
            Lumos.place(mc.player.level(), Light.spot(eye.x, eye.y, eye.z,
                    (float) look.x, (float) look.y, (float) look.z,
                    1.0f, 0.95f, 0.85f, 3.0f, 22.0f, 20.0f, 32.0f));
        });
    }

    /** Repositions the selected light two blocks ahead, aimed along the view. */
    private void pullToView() {
        Light current = selected;
        long worldId = selectedWorldId;
        if (current == null) return;
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (mc.player == null || level == null) return;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0f);
            Light moved = current.at(eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                    (float) look.x, (float) look.y, (float) look.z);
            if (worldId >= 0) {
                Lumos.update(level, worldId, moved);
            } else {
                DemoLights.INSTANCE.replace(level, current, moved);
            }
        });
        if (worldId < 0) swapGrace = SWAP_GRACE_TICKS;
    }

    private void deleteSelected() {
        Light current = selected;
        long worldId = selectedWorldId;
        if (current == null) return;
        selected = null;
        selectedWorldId = -1;
        selectedMirror = null;
        rebuildRows();
        Minecraft.getInstance().execute(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            if (worldId >= 0) {
                Lumos.remove(level, worldId);
            } else {
                DemoLights.INSTANCE.remove(level, current);
            }
        });
    }

    private long worldIdOf(Light light) {
        for (Map.Entry<Long, Light> entry : world.entrySet()) {
            if (entry.getValue() == light) return entry.getKey();
        }
        return -1;
    }

    private static boolean containsIdentity(List<Light> list, Light light) {
        for (Light candidate : list) {
            if (candidate == light) return true;
        }
        return false;
    }

    /** The light's color normalized to full brightness, as an arbitrary-value Tailwind class. */
    private static String swatch(Light light) {
        float m = Math.max(light.r, Math.max(light.g, light.b));
        float r = m < 0.01f ? 1f : light.r / m;
        float g = m < 0.01f ? 1f : light.g / m;
        float b = m < 0.01f ? 1f : light.b / m;
        return String.format(Locale.ROOT, "bg-[#%02X%02X%02X]",
                Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    private static boolean sameLight(Light a, Light b) {
        return a.type == b.type
                && a.x == b.x && a.y == b.y && a.z == b.z
                && a.directionX == b.directionX && a.directionY == b.directionY
                && a.directionZ == b.directionZ
                && a.r == b.r && a.g == b.g && a.b == b.b
                && a.intensity == b.intensity && a.range == b.range
                && a.cosInner == b.cosInner && a.cosOuter == b.cosOuter
                && a.castsShadow == b.castsShadow;
    }
}
