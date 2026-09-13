package fr.lacaleche.glue.testmod.mcsx.expedition;

import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.reactive.ClientMirror;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.testmod.registries.TestItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Client-thread owner of the expedition lifecycle. Stage, configuration and progress live in
 * {@link ClientMirror}s: gameplay code reads and writes them on the client thread while the planner
 * and HUD bind their UI-thread {@link Value} views. The draft signals are UI-owned; the
 * client-thread reset crosses over with {@link Signal#postSet}.
 */
@Environment(EnvType.CLIENT)
public final class ExpeditionSession {

    public enum Stage {
        DRAFT,
        ACTIVE,
        COMPLETED,
        ABORTED
    }

    public record Configuration(
            String name,
            boolean travelEnabled,
            int travelTarget,
            boolean elevationEnabled,
            int elevationTarget,
            boolean suppliesEnabled,
            int suppliesTarget
    ) {

        public Configuration {
            name = Objects.requireNonNull(name, "name").trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Expedition name is blank");
            if (!travelEnabled && !elevationEnabled && !suppliesEnabled) {
                throw new IllegalArgumentException("At least one expedition objective is required");
            }
            if (travelEnabled && travelTarget <= 0) throw new IllegalArgumentException("Travel target must be positive");
            if (suppliesEnabled && suppliesTarget <= 0) throw new IllegalArgumentException("Supplies target must be positive");
            // The elevation target is deliberately unvalidated: any integer is a legal Y, negatives included.
        }

        int objectiveCount() {
            int count = 0;
            if (this.travelEnabled) count++;
            if (this.elevationEnabled) count++;
            if (this.suppliesEnabled) count++;
            return count;
        }
    }

    public record Progress(
            int travel,
            int elevation,
            int supplies,
            boolean travelComplete,
            boolean elevationComplete,
            boolean suppliesComplete
    ) {

        private static final Progress EMPTY = new Progress(0, 0, 0, false, false, false);

        int completedObjectives(Configuration configuration) {
            int count = 0;
            if (configuration.travelEnabled() && this.travelComplete) count++;
            if (configuration.elevationEnabled() && this.elevationComplete) count++;
            if (configuration.suppliesEnabled() && this.suppliesComplete) count++;
            return count;
        }
    }

    private static final Configuration DEFAULT_CONFIGURATION = new Configuration(
            "Highland Survey", true, 24, true, 96, true, 3
    );
    public static final ExpeditionSession INSTANCE = new ExpeditionSession();

    private final Signal<String> draftName = Signal.of(DEFAULT_CONFIGURATION.name());
    private final Signal<Boolean> draftTravelEnabled = Signal.of(DEFAULT_CONFIGURATION.travelEnabled());
    private final Signal<String> draftTravelTarget = Signal.of(Integer.toString(DEFAULT_CONFIGURATION.travelTarget()));
    private final Signal<Boolean> draftElevationEnabled = Signal.of(DEFAULT_CONFIGURATION.elevationEnabled());
    private final Signal<String> draftElevationTarget = Signal.of(Integer.toString(DEFAULT_CONFIGURATION.elevationTarget()));
    private final Signal<Boolean> draftSuppliesEnabled = Signal.of(DEFAULT_CONFIGURATION.suppliesEnabled());
    private final Signal<String> draftSuppliesTarget = Signal.of(Integer.toString(DEFAULT_CONFIGURATION.suppliesTarget()));
    private final ClientMirror<Stage> stage = ClientMirror.of(Stage.DRAFT);
    private final ClientMirror<Configuration> configuration = ClientMirror.of(DEFAULT_CONFIGURATION);
    private final ClientMirror<Progress> progress = ClientMirror.of(Progress.EMPTY);
    private final Value<String> progressSummary = Value.combine(
            this.configuration.value(),
            this.progress.value(),
            (current, measured) -> measured.completedObjectives(current) + " / " + current.objectiveCount()
    );
    private final Value<String> travelProgress = this.progressText(Progress::travel, Configuration::travelTarget);
    private final Value<String> elevationProgress = this.progressText(Progress::elevation, Configuration::elevationTarget);
    private final Value<String> suppliesProgress = this.progressText(Progress::supplies, Configuration::suppliesTarget);

    private Vec3 startPosition;
    private boolean initialized;

    private ExpeditionSession() {
    }

    void init() {
        if (this.initialized) return;

        this.initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    /**
     * Starts the expedition, or reports that it could not. The overlay slot is rechecked here and not
     * only where the planner opens: ownership can be taken in between &mdash; a command, a direct API
     * call, another mod's overlay &mdash; and this is the moment the HUD actually claims it. The
     * check precedes every state change, so a refused start leaves the session exactly as it was.
     *
     * @return whether the expedition started
     */
    boolean start(Configuration next) {
        this.checkClientThread();
        if (UiOverlay.isOccupied() && !ExpeditionDemo.INSTANCE.hud().isMounted()) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("Cannot start an expedition without a player");

        ExpeditionDemo.INSTANCE.hud().mount();
        this.configuration.set(Objects.requireNonNull(next, "configuration"));
        this.startPosition = player.position();
        Progress measured = this.measure(player, Progress.EMPTY);
        this.progress.set(measured);
        this.stage.set(this.isComplete(measured) ? Stage.COMPLETED : Stage.ACTIVE);
        return true;
    }

    void abort() {
        this.checkClientThread();
        if (this.stage.get() != Stage.ACTIVE) return;

        ExpeditionDemo.INSTANCE.hud().unmount();
        this.stage.set(Stage.ABORTED);
    }

    public void reset() {
        this.checkClientThread();
        this.startPosition = null;
        ExpeditionDemo.INSTANCE.hud().unmount();
        this.stage.set(Stage.DRAFT);
        this.configuration.set(DEFAULT_CONFIGURATION);
        this.progress.set(Progress.EMPTY);
        this.draftName.postSet(DEFAULT_CONFIGURATION.name());
        this.draftTravelEnabled.postSet(DEFAULT_CONFIGURATION.travelEnabled());
        this.draftTravelTarget.postSet(Integer.toString(DEFAULT_CONFIGURATION.travelTarget()));
        this.draftElevationEnabled.postSet(DEFAULT_CONFIGURATION.elevationEnabled());
        this.draftElevationTarget.postSet(Integer.toString(DEFAULT_CONFIGURATION.elevationTarget()));
        this.draftSuppliesEnabled.postSet(DEFAULT_CONFIGURATION.suppliesEnabled());
        this.draftSuppliesTarget.postSet(Integer.toString(DEFAULT_CONFIGURATION.suppliesTarget()));
    }

    public Stage stage() {
        return this.stage.get();
    }

    public Progress progress() {
        return this.progress.get();
    }

    Signal<String> draftName() {
        return this.draftName;
    }

    Signal<Boolean> draftTravelEnabled() {
        return this.draftTravelEnabled;
    }

    Signal<String> draftTravelTarget() {
        return this.draftTravelTarget;
    }

    Signal<Boolean> draftElevationEnabled() {
        return this.draftElevationEnabled;
    }

    Signal<String> draftElevationTarget() {
        return this.draftElevationTarget;
    }

    Signal<Boolean> draftSuppliesEnabled() {
        return this.draftSuppliesEnabled;
    }

    Signal<String> draftSuppliesTarget() {
        return this.draftSuppliesTarget;
    }

    Value<Stage> stageValue() {
        return this.stage.value();
    }

    Value<Configuration> configurationValue() {
        return this.configuration.value();
    }

    Value<String> progressSummary() {
        return this.progressSummary;
    }

    Value<String> travelProgress() {
        return this.travelProgress;
    }

    Value<String> elevationProgress() {
        return this.elevationProgress;
    }

    Value<String> suppliesProgress() {
        return this.suppliesProgress;
    }

    private void tick(Minecraft minecraft) {
        if (this.stage.get() != Stage.ACTIVE || minecraft.player == null || this.startPosition == null) return;

        Progress next = this.measure(minecraft.player, this.progress.get());
        this.progress.set(next);
        if (this.isComplete(next)) this.stage.set(Stage.COMPLETED);
    }

    private Progress measure(LocalPlayer player, Progress previous) {
        int travel = (int) Math.floor(player.position().subtract(this.startPosition).horizontalDistance());
        int elevation = player.getBlockY();
        int supplies = countSupplies(player.getInventory());
        Configuration active = this.configuration.get();
        return new Progress(
                travel,
                elevation,
                supplies,
                previous.travelComplete() || active.travelEnabled() && travel >= active.travelTarget(),
                previous.elevationComplete() || active.elevationEnabled() && elevation >= active.elevationTarget(),
                previous.suppliesComplete() || active.suppliesEnabled() && supplies >= active.suppliesTarget()
        );
    }

    private boolean isComplete(Progress current) {
        Configuration active = this.configuration.get();
        return current.completedObjectives(active) == active.objectiveCount();
    }

    private Value<String> progressText(
            ToIntFunction<Progress> measured,
            ToIntFunction<Configuration> target
    ) {
        return Value.combine(this.configuration.value(), this.progress.value(), (current, currentProgress) ->
                measured.applyAsInt(currentProgress) + " / " + target.applyAsInt(current));
    }

    private void checkClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Not called from client thread");
        }
    }

    /** Counts the showcase supply item across every slot of {@code inventory}. */
    public static int countSupplies(Inventory inventory) {
        int quantity = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(TestItems.TEST_COMPONENT_ITEM)) quantity += stack.getCount();
        }
        return quantity;
    }
}
