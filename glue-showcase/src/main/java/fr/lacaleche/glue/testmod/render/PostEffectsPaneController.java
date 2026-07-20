package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.TimedPostEffect;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the "Post FX" debug pane ({@code assets/mcsx/ui/debug/effects.mcsx}): every registered
 * toggle and timed effect as a row &mdash; click fires or flips it, the ✕ stops a running timed
 * effect. Statuses are gathered on the client thread by {@link GlueDebugDock}'s tick pump and
 * applied to the row signal on the UI thread; the actions hop the other way, because the effect
 * machinery is client-thread state.
 */
@Environment(EnvType.CLIENT)
public final class PostEffectsPaneController extends ScreenController {

    /** One pane row. Display values only, so the keyed {@code <for>} reuses unchanged rows. */
    public record EffectRow(String tag, String name, String status, String statusColor, int index) {
    }

    private enum Kind { TOGGLE, TIMED, TIMED_REGISTRY }

    private record Entry(String name, Kind kind, @Nullable PostShaderHandle handle,
                         @Nullable TimedPostEffect timed, @Nullable ResourceLocation registryId) {
    }

    /** The catalogue the old HUD registered: toggles, JSON/registry effects, Java lambdas. */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("Blur", Kind.TOGGLE, TestShaders.BLUR, null, null),
            new Entry("Grayscale", Kind.TOGGLE, TestShaders.GRAYSCALE, null, null),
            new Entry("Departure Vortex (JSON)", Kind.TIMED_REGISTRY, null, null,
                    TestmodClient.id("departure_vortex")),
            new Entry("Denial Pulse (JSON)", Kind.TIMED_REGISTRY, null, null,
                    TestmodClient.id("denial_pulse")),
            new Entry("Chromatic (registry def)", Kind.TIMED_REGISTRY, null, null,
                    TestmodClient.id("chromatic")),
            new Entry("Chromatic Aberration (Java)", Kind.TIMED, null, TestPostShaderHandler.CHROMATIC, null),
            new Entry("Shattered Screen (Java)", Kind.TIMED, null, TestPostShaderHandler.SHATTERED, null),
            new Entry("Impact Frame (Java)", Kind.TIMED, null, TestPostShaderHandler.IMPACT, null));

    /** Written on the UI thread at pane bind, read by the client-thread tick pump. */
    @Nullable
    private static volatile PostEffectsPaneController active;

    private final Signal<List<EffectRow>> rows = signal(buildRows());

    public PostEffectsPaneController() {
        active = this;
    }

    @Nullable
    static PostEffectsPaneController active() {
        return active;
    }

    static void deactivate() {
        active = null;
    }

    /** Client thread: statuses are read where they live. */
    static List<EffectRow> snapshotRows() {
        return buildRows();
    }

    /** UI thread: the keyed {@code <for>} only rebuilds rows whose status actually changed. */
    void applySnapshot(List<EffectRow> next) {
        if (!next.equals(rows.get())) {
            rows.set(next);
        }
    }

    private static List<EffectRow> buildRows() {
        List<EffectRow> result = new ArrayList<>(ENTRIES.size());
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            String status = status(entry);
            result.add(new EffectRow(entry.kind == Kind.TOGGLE ? "[T]" : "[▶]", entry.name,
                    status, statusColor(status), i));
        }
        return List.copyOf(result);
    }

    private static String status(Entry entry) {
        return switch (entry.kind) {
            case TOGGLE -> TestPostShaderHandler.INSTANCE.isToggled(entry.handle) ? "ON" : "OFF";
            case TIMED -> entry.timed != null && entry.timed.isActive() ? "PLAYING" : "READY";
            case TIMED_REGISTRY ->
                    TestPostShaderHandler.INSTANCE.isRegistryActive(entry.registryId) ? "PLAYING" : "READY";
        };
    }

    private static String statusColor(String status) {
        return switch (status) {
            case "ON" -> "text-success";
            case "OFF" -> "text-subtle";
            case "PLAYING" -> "text-warning";
            default -> "text-muted";
        };
    }

    private void fire(EffectRow row) {
        Entry entry = ENTRIES.get(row.index());
        Minecraft.getInstance().execute(() -> {
            switch (entry.kind) {
                case TOGGLE -> TestPostShaderHandler.INSTANCE.toggleByHandle(entry.handle);
                case TIMED -> {
                    if (entry.timed != null) entry.timed.trigger();
                }
                case TIMED_REGISTRY -> {
                    // Don't restart while already playing — re-triggering mid-flight leaks nothing
                    // but restarts the curve, which reads as a glitch.
                    if (entry.registryId != null
                            && !TestPostShaderHandler.INSTANCE.isRegistryActive(entry.registryId)) {
                        TestPostShaderHandler.INSTANCE.triggerFromRegistry(entry.registryId);
                    }
                }
            }
        });
    }

    private void stopEffect(EffectRow row) {
        Entry entry = ENTRIES.get(row.index());
        Minecraft.getInstance().execute(() -> {
            switch (entry.kind) {
                case TIMED -> {
                    if (entry.timed != null) entry.timed.stop();
                }
                case TIMED_REGISTRY -> {
                    if (entry.registryId != null) {
                        TestPostShaderHandler.INSTANCE.stopRegistry(entry.registryId);
                    }
                }
                default -> {
                }
            }
        });
    }
}
