package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.mcsx.client.GameSurface;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.reactive.Values;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import icyllis.modernui.core.Core;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The live model behind the Glue Studio workspace: it mirrors the demo lights, the synced world
 * lights, the post-effect renderer and the player pose into reactive values, and applies studio edits
 * back to the world.
 *
 * <p>Two threads meet here. Everything the world owns is read and written on Minecraft's client
 * thread; every {@link Signal} is read and written on ModernUI's UI thread. Each direction crosses
 * exactly once, through {@code Minecraft#execute} and {@link Core#postOnUiThread}. Polling stops
 * while no workspace is mounted.</p>
 */
@Environment(EnvType.CLIENT)
public final class StudioSession {

    public static final StudioSession INSTANCE = new StudioSession();

    private static final float MIN_INTENSITY = 0.05f;
    private static final float MAX_INTENSITY = 16.0f;
    private static final float MIN_RANGE = 1.0f;
    private static final float MAX_RANGE = 64.0f;
    private static final int LOG_LIMIT = 40;
    private static final int MIN_SPAN = 16;
    private static final int MAX_SPAN = 192;
    /** First hotbar slot in {@code InventoryMenu}, which is what the creative-slot packet indexes. */
    private static final int HOTBAR_MENU_SLOT = 36;

    private final Signal<List<StudioLight>> lights = Signal.of(List.of());
    private final Signal<Light> selected = Signal.of(null);
    private final Signal<Pose> pose = Signal.of(Pose.UNKNOWN);
    private final Signal<Integer> worldLights = Signal.of(0);
    private final Signal<Set<String>> activeEffects = Signal.of(Set.of());
    private final Object worldClaimsLock = new Object();
    private final Signal<Boolean> stressRing = Signal.of(false);
    private final Signal<Boolean> flashlight = Signal.of(false);
    private final Signal<List<LogEntry>> log = Signal.of(List.of());
    private final Signal<String> status = Signal.of("Studio ready");
    private final Signal<Integer> radarSpan = Signal.of(48);
    private final Signal<String> draftRed = Signal.of("255");
    private final Signal<String> draftGreen = Signal.of("255");
    private final Signal<String> draftBlue = Signal.of("255");
    private final Signal<String> draftIntensity = Signal.of("2.5");
    private final Signal<String> draftRange = Signal.of("12");
    private final Signal<Boolean> draftShadow = Signal.of(true);
    private final List<StudioEffect> effects = StudioEffect.all();
    private final ViewportController viewport = new ViewportController();
    private final Value<Boolean> draftValid;
    private final Value<Integer> draftColor;
    private int nextLogId = 1;
    private boolean initialized;
    private volatile boolean mounted;
    private Object mountedWorkspace;
    private Object worldClaimsPublisher;
    private boolean worldClaimsOpen;

    private StudioSession() {
        this.draftValid = Values.all(
                this.draftRed.map(StudioSession::isChannel),
                this.draftGreen.map(StudioSession::isChannel),
                this.draftBlue.map(StudioSession::isChannel),
                this.draftIntensity.map(value -> isNumber(value, MIN_INTENSITY, MAX_INTENSITY)),
                this.draftRange.map(value -> isNumber(value, MIN_RANGE, MAX_RANGE))
        );
        this.draftColor = Value.combine(
                this.draftRed.map(StudioSession::channelOf),
                this.draftGreen.map(StudioSession::channelOf),
                this.draftBlue.map(StudioSession::channelOf),
                (red, green, blue) -> 0xff000000 | red << 16 | green << 8 | blue
        );
    }

    public void init() {
        if (this.initialized) return;

        this.initialized = true;
        this.viewport.init();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    ViewportController viewport() {
        return this.viewport;
    }

    Value<List<StudioLight>> lights() {
        return this.lights;
    }

    Value<Light> selected() {
        return this.selected;
    }

    Value<Pose> pose() {
        return this.pose;
    }

    Value<Integer> worldLights() {
        return this.worldLights;
    }

    Value<Set<String>> activeEffects() {
        return this.activeEffects;
    }

    Value<Boolean> stressRing() {
        return this.stressRing;
    }

    Value<Boolean> flashlight() {
        return this.flashlight;
    }

    Value<List<LogEntry>> log() {
        return this.log;
    }

    Value<String> status() {
        return this.status;
    }

    Value<Integer> radarSpan() {
        return this.radarSpan;
    }

    Signal<String> draftRed() {
        return this.draftRed;
    }

    Signal<String> draftGreen() {
        return this.draftGreen;
    }

    Signal<String> draftBlue() {
        return this.draftBlue;
    }

    Signal<String> draftIntensity() {
        return this.draftIntensity;
    }

    Signal<String> draftRange() {
        return this.draftRange;
    }

    Signal<Boolean> draftShadow() {
        return this.draftShadow;
    }

    List<StudioEffect> effects() {
        return this.effects;
    }

    void mount(Object workspace) {
        synchronized (this.worldClaimsLock) {
            this.mountedWorkspace = workspace;
            this.worldClaimsOpen = true;
            this.mounted = true;
        }
    }

    void unmount(Object workspace) {
        synchronized (this.worldClaimsLock) {
            if (this.mountedWorkspace != workspace) return;

            this.mounted = false;
            this.worldClaimsOpen = false;
            this.mountedWorkspace = null;
            this.worldClaimsPublisher = null;
        }
        this.viewport.release();
    }

    /**
     * Runs on the client thread as the workspace unmounts: the viewport pane's world and input claims
     * end on the same frame as the workspace, and the closed gate keeps a late UI-thread draw of the
     * still-attached pane from re-publishing them before the View detaches.
     */
    void closeWorldClaims(Object workspace) {
        synchronized (this.worldClaimsLock) {
            if (this.mountedWorkspace != workspace) return;

            this.worldClaimsOpen = false;
            this.worldClaimsPublisher = null;
            GameViewport.clear();
            GameSurface.clear();
        }
    }

    void publishWorldClaims(Object workspace, Object publisher,
                            GameViewport.Bounds viewportBounds, GameSurface.Bounds surfaceBounds) {
        Minecraft.getInstance().execute(() -> {
            synchronized (this.worldClaimsLock) {
                if (this.mountedWorkspace != workspace || !this.worldClaimsOpen) return;

                this.worldClaimsPublisher = publisher;
                GameViewport.set(viewportBounds);
                GameSurface.set(surfaceBounds);
            }
        });
    }

    void clearWorldClaims(Object workspace, Object publisher) {
        Minecraft.getInstance().execute(() -> {
            synchronized (this.worldClaimsLock) {
                if (this.mountedWorkspace != workspace || this.worldClaimsPublisher != publisher) return;

                this.worldClaimsPublisher = null;
                GameViewport.clear();
                GameSurface.clear();
            }
        });
    }

    void select(Light light) {
        this.selected.set(light);
        if (light == null) {
            this.status.set("No emitter selected");
            return;
        }

        this.draftRed.set(Integer.toString(StudioLight.channel(light.r)));
        this.draftGreen.set(Integer.toString(StudioLight.channel(light.g)));
        this.draftBlue.set(Integer.toString(StudioLight.channel(light.b)));
        this.draftIntensity.set(String.format(Locale.ROOT, "%.1f", light.intensity));
        this.draftRange.set(String.format(Locale.ROOT, "%.0f", light.range));
        this.draftShadow.set(light.castsShadow);
        this.status.set("Editing " + describe(light));
    }

    void selectPreset(int red, int green, int blue) {
        this.draftRed.set(Integer.toString(red));
        this.draftGreen.set(Integer.toString(green));
        this.draftBlue.set(Integer.toString(blue));
    }

    void stepIntensity(float delta) {
        this.draftIntensity.set(String.format(Locale.ROOT, "%.1f",
                Math.clamp(number(this.draftIntensity.get(), 1.0f) + delta, MIN_INTENSITY, MAX_INTENSITY)));
    }

    void stepRange(float delta) {
        this.draftRange.set(String.format(Locale.ROOT, "%.0f",
                Math.clamp(number(this.draftRange.get(), 8.0f) + delta, MIN_RANGE, MAX_RANGE)));
    }

    void zoom(int delta) {
        this.radarSpan.set(Math.clamp(this.radarSpan.get() + delta, MIN_SPAN, MAX_SPAN));
    }

    Value<Boolean> draftValid() {
        return this.draftValid;
    }

    Value<Integer> draftColor() {
        return this.draftColor;
    }

    void revertDraft() {
        this.select(this.selected.get());
    }

    /** Rebuilds the selected light from the draft. Despawn-then-spawn is what re-bakes its shadow map. */
    void applyDraft() {
        Light current = this.selected.get();
        if (current == null || !this.draftValid.get()) return;

        Light restyled = restyle(
                current,
                number(this.draftRed.get(), 255f) / 255f,
                number(this.draftGreen.get(), 255f) / 255f,
                number(this.draftBlue.get(), 255f) / 255f,
                number(this.draftIntensity.get(), 1f),
                number(this.draftRange.get(), 8f),
                this.draftShadow.get()
        );
        this.replace(current, restyled, "Restyled " + describe(restyled));
    }

    void moveSelectedToPlayer() {
        Light current = this.selected.get();
        if (current == null) return;

        this.withPlayer(player -> {
            Vec3 eye = player.getEyePosition();
            Vec3 view = player.getViewVector(0f);
            Light moved = current.at(eye.x, eye.y, eye.z, (float) view.x, (float) view.y, (float) view.z);
            this.replaceOnClientThread(player.level(), current, moved, "Moved " + describe(moved));
        });
    }

    void deleteSelected() {
        Light current = this.selected.get();
        if (current == null) return;

        this.withPlayer(player -> {
            DemoLights.INSTANCE.remove(player.level(), current);
            this.publish(() -> {
                this.select(null);
                this.appendLog("Removed " + describe(current));
            });
        });
    }

    void clearVisualLights() {
        this.withPlayer(player -> {
            DemoLights.INSTANCE.clear(player.level());
            this.publish(() -> {
                this.select(null);
                this.appendLog("Cleared every visual light");
            });
        });
    }

    void spawnPoint() {
        this.withPlayer(player -> {
            Vec3 position = player.position();
            Light light = Light.point(position.x, position.y + 1.2, position.z,
                    1.0f, 0.86f, 0.62f, 2.5f, 12.0f);
            this.spawnOnClientThread(player.level(), light);
        });
    }

    void spawnSpot() {
        this.withPlayer(player -> {
            Vec3 eye = player.getEyePosition();
            Vec3 view = player.getViewVector(0f);
            Light light = Light.spot(eye.x, eye.y, eye.z,
                    (float) view.x, (float) view.y, (float) view.z,
                    0.62f, 0.78f, 1.0f, 3.0f, 22.0f, 18.0f, 30.0f);
            this.spawnOnClientThread(player.level(), light);
        });
    }

    void spawnAtAimedBlock() {
        this.withPlayer(player -> {
            HitResult hit = player.pick(24.0, 0f, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                this.status.postSet("Look at a block within 24 blocks first");
                return;
            }

            BlockPos target = block.getBlockPos().relative(block.getDirection());
            Light light = Light.point(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    1.0f, 0.42f, 0.18f, 3.0f, 14.0f);
            this.spawnOnClientThread(player.level(), light);
        });
    }

    void toggleStressRing() {
        this.withPlayer(player -> {
            DemoLights.INSTANCE.toggleStaticLights();
            this.publish(() -> this.appendLog(DemoLights.INSTANCE.isStressRingEnabled()
                    ? "Spawned the unshadowed stress ring"
                    : "Cleared the stress ring"));
        });
    }

    void toggleFlashlight() {
        this.withPlayer(player -> {
            DemoLights.INSTANCE.toggleFlashlight();
            this.publish(() -> this.appendLog(DemoLights.INSTANCE.isFlashlightOn()
                    ? "Attached a spot to the player's eyes"
                    : "Detached the eye-attached spot"));
        });
    }

    void placeWorldLight() {
        this.withPlayer(player -> {
            Vec3 position = player.position();
            Lumos.place(player.level(), Light.point(position.x, position.y + 1.2, position.z,
                    0.55f, 0.92f, 1.0f, 3.0f, 16.0f));
            this.publish(() -> this.appendLog("Requested a world light from the server"));
        });
    }

    void clearWorldLights() {
        this.withPlayer(player -> {
            Level level = player.level();
            Map<Long, Light> placed = Lumos.lights(level);
            for (Long id : placed.keySet()) {
                Lumos.remove(level, id);
            }
            this.publish(() -> this.appendLog("Requested removal of " + placed.size() + " world lights"));
        });
    }

    void trigger(StudioEffect effect) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            effect.trigger().run();
            this.publish(() -> this.appendLog("Ran post effect " + effect.id()));
        });
    }

    /**
     * Creative fills the selected hotbar slot directly; otherwise the only route to a stack is the
     * {@code /give} command, which the server refuses without operator permission. Both outcomes are
     * reported, because a silent no-op reads as a broken button.
     */
    void give(StudioBlock block) {
        this.withPlayer(player -> {
            String name = BuiltInRegistries.ITEM.getKey(block.item()).toString();
            Minecraft minecraft = Minecraft.getInstance();
            if (player.hasInfiniteMaterials() && minecraft.gameMode != null) {
                int slot = HOTBAR_MENU_SLOT + player.getInventory().getSelectedSlot();
                minecraft.gameMode.handleCreativeModeItemAdd(new ItemStack(block.item()), slot);
                this.publish(() -> this.appendLog("Put " + name + " in the selected hotbar slot"));
                return;
            }

            player.connection.sendCommand("give @s " + name);
            this.publish(() -> this.appendLog("Ran /give for " + name + " (operator permission required)"));
        });
    }

    void clearLog() {
        this.log.set(List.of());
        this.status.set("Console cleared");
    }

    void status(String message) {
        this.status.set(message);
    }

    void appendLog(String message) {
        List<LogEntry> next = new ArrayList<>();
        next.add(new LogEntry(this.nextLogId++, message));
        next.addAll(this.log.get());
        while (next.size() > LOG_LIMIT) {
            next.removeLast();
        }
        this.log.set(List.copyOf(next));
        this.status.set(message);
    }

    private void tick(Minecraft minecraft) {
        if (!this.mounted) return;

        LocalPlayer player = minecraft.player;
        if (player == null) return;

        Vec3 position = player.position();
        List<Light> owned = DemoLights.INSTANCE.spawned();
        List<StudioLight> snapshot = new ArrayList<>(owned.size());
        for (int index = 0; index < owned.size(); index++) {
            Light light = owned.get(index);
            double distance = Math.sqrt(position.distanceToSqr(light.x, light.y, light.z));
            snapshot.add(new StudioLight(light, index + 1, Math.round(distance * 10.0) / 10.0));
        }

        Pose nextPose = new Pose(position.x, position.y, position.z, player.getYRot());
        int placed = Lumos.lights(player.level()).size();
        Set<String> running = new LinkedHashSet<>();
        for (StudioEffect effect : this.effects) {
            if (effect.active().getAsBoolean()) running.add(effect.id());
        }
        boolean ring = DemoLights.INSTANCE.isStressRingEnabled();
        boolean torch = DemoLights.INSTANCE.isFlashlightOn();

        this.publish(() -> {
            this.lights.set(Collections.unmodifiableList(snapshot));
            this.pose.set(nextPose);
            this.worldLights.set(placed);
            this.activeEffects.set(Collections.unmodifiableSet(running));
            this.stressRing.set(ring);
            this.flashlight.set(torch);
            this.dropStaleSelection(owned);
        });
    }

    private void dropStaleSelection(List<Light> owned) {
        Light current = this.selected.get();
        if (current == null) return;

        for (Light light : owned) {
            if (light == current) return;
        }
        this.select(null);
    }

    private void replace(Light current, Light replacement, String message) {
        this.withPlayer(player -> this.replaceOnClientThread(player.level(), current, replacement, message));
    }

    private void replaceOnClientThread(Level level, Light current, Light replacement, String message) {
        DemoLights.INSTANCE.replace(level, current, replacement);
        this.publish(() -> {
            this.select(replacement);
            this.appendLog(message);
        });
    }

    private void spawnOnClientThread(Level level, Light light) {
        DemoLights.INSTANCE.spawn(level, light);
        this.publish(() -> {
            this.select(light);
            this.appendLog("Spawned " + describe(light));
        });
    }

    private void withPlayer(Consumer<LocalPlayer> action) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LocalPlayer player = minecraft.player;
            if (player == null) return;

            action.accept(player);
        });
    }

    private void publish(Runnable action) {
        Core.postOnUiThread(action);
    }

    private static Light restyle(Light source, float red, float green, float blue,
                                 float intensity, float range, boolean shadow) {
        Light restyled = switch (source.type) {
            case POINT -> Light.point(source.x, source.y, source.z, red, green, blue, intensity, range);
            case SPOT -> Light.spot(source.x, source.y, source.z,
                    source.directionX, source.directionY, source.directionZ,
                    red, green, blue, intensity, range,
                    StudioLight.coneDegrees(source.cosInner), StudioLight.coneDegrees(source.cosOuter));
            case GOBO -> Light.gobo(source.x, source.y, source.z,
                    source.directionX, source.directionY, source.directionZ,
                    red, green, blue, intensity, range,
                    StudioLight.coneDegrees(source.cosInner), StudioLight.coneDegrees(source.cosOuter),
                    source.goboTextureId);
        };
        return restyled.withShadow(shadow);
    }

    private static String describe(Light light) {
        return String.format(Locale.ROOT, "%s at %.0f %.0f %.0f",
                light.type.name().toLowerCase(Locale.ROOT), light.x, light.y, light.z);
    }

    private static boolean isChannel(String value) {
        return isNumber(value, 0f, 255f);
    }

    private static boolean isNumber(String value, float minimum, float maximum) {
        try {
            float parsed = Float.parseFloat(value.trim());
            return parsed >= minimum && parsed <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int channelOf(String value) {
        return Math.clamp(Math.round(number(value, 255f)), 0, 255);
    }

    private static float number(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** The player transform the radar and footer follow. {@code yaw} is Minecraft's Y rotation. */
    record Pose(double x, double y, double z, float yaw) {

        static final Pose UNKNOWN = new Pose(0, 0, 0, 0f);

        String text() {
            return String.format(Locale.ROOT, "%.0f  %.0f  %.0f", this.x, this.y, this.z);
        }
    }

    record LogEntry(int id, String message) {
    }
}
