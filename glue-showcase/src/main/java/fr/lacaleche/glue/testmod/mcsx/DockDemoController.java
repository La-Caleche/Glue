package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.theme.Themes;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the four dock demo panes ({@code assets/mcsx/ui/dock/*.mcsx}). Besides dock gestures, the
 * panes exercise focus, two-way inputs, disclosure, scrolling and continuous control updates while
 * the game viewport is embedded.
 */
public final class DockDemoController extends ScreenController {

    /** One row of the mock scene tree; depth is baked into {@code classes} as left padding. */
    public record SceneRow(String name, String classes, String color) {
    }

    /** One mock console line. */
    public record LogLine(String time, String tag, String message, String color) {
    }

    /** One label/value stat row (inspector transform, profiler counters). */
    public record Stat(String label, String value) {
    }

    private final List<SceneRow> sceneRows = List.of(
            new SceneRow("Scene", "pl-2", "text-default"),
            new SceneRow("Sun · Directional", "pl-5", "text-muted"),
            new SceneRow("Terrain", "pl-5", "text-muted"),
            new SceneRow("Chunk 0,0", "pl-8 bg-accent-subtle", "text-accent"),
            new SceneRow("Chunk 0,1", "pl-8", "text-muted"),
            new SceneRow("Water Volume", "pl-5", "text-subtle"),
            new SceneRow("Player", "pl-5", "text-muted"),
            new SceneRow("Camera", "pl-8", "text-muted"),
            new SceneRow("Particle System", "pl-5", "text-muted"));

    /** Mutable on purpose: Add/Remove exercise keyed {@code <for>} reuse — pin a row, add a line,
     *  the pin must survive. Rows are value-keyed, so appended lines must be distinct. */
    private final Signal<List<LogLine>> logLines = signal(List.of(
            new LogLine("12:03:21", "[Renderer]", "Vulkan 1.3 device initialized", "text-muted"),
            new LogLine("12:03:21", "[Assets]", "Loaded 1,204 textures (84 MB)", "text-success"),
            new LogLine("12:03:22", "[World]", "Streamed 36 chunks around player", "text-muted"),
            new LogLine("12:03:22", "[Physics]", "Mesh collider missing on \"Water Volume\"", "text-warning"),
            new LogLine("12:03:23", "[Net]", "Listening on 0.0.0.0:25565", "text-muted"),
            new LogLine("12:03:24", "[Script]", "NullReferenceException — PlayerController.cs:42", "text-danger"),
            new LogLine("12:03:25", "[Scene]", "Saved Overworld.level (2.1 MB)", "text-success")));

    private int nextLine = 26;

    private final List<Stat> transformRows = List.of(
            new Stat("Position", "0.00 · 64.0 · 0.00"),
            new Stat("Rotation", "0.0 · 0.0 · 0.0"),
            new Stat("Scale", "1.00 · 1.00 · 1.00"),
            new Stat("Material", "Emerald.mat"),
            new Stat("Opacity", "92%"));

    private final List<Stat> stats = List.of(
            new Stat("CPU", "41%"),
            new Stat("GPU", "57%"),
            new Stat("Memory", "38%"),
            new Stat("Draw calls", "1"),
            new Stat("Triangles", "1.24 M"),
            new Stat("Entities", "1,204"),
            new Stat("Chunks loaded", "36"));

    private final Signal<String> search = signal("");
    private final Signal<String> objectName = signal("Chunk 0,0");
    private final Signal<String> command = signal("");
    private final Signal<Boolean> transformOpen = signal(true);
    private final Signal<Boolean> rendererOpen = signal(true);
    private final Signal<Boolean> advancedOpen = signal(false);
    private final Signal<Boolean> objectEnabled = signal(true);
    private final Signal<Boolean> castShadows = signal(true);
    private final Signal<Boolean> autoScroll = signal(true);
    private final Signal<Integer> opacity = signal(92);
    private final Signal<Integer> sampleWindow = signal(60);
    private final Computed<Float> opacityFraction = computed(() -> opacity.get() / 100f);
    private final Computed<Float> sampleFraction = computed(() -> sampleWindow.get() / 120f);
    private final Computed<String> opacityLabel = computed(() -> opacity.get() + "%");
    private final Computed<String> sampleLabel = computed(() -> sampleWindow.get() + " frames");
    private final Computed<String> messageCount = computed(() -> logLines.get().size() + " messages");

    private void toggleTheme() {
        Themes.toggle();
    }

    private void addLine() {
        List<LogLine> next = new ArrayList<>(logLines.get());
        next.add(new LogLine("12:03:" + nextLine++, "[Console]", "Manual log line", "text-muted"));
        logLines.set(List.copyOf(next));
    }

    private void removeLine() {
        List<LogLine> current = logLines.get();
        if (!current.isEmpty()) {
            logLines.set(List.copyOf(current.subList(0, current.size() - 1)));
        }
    }

    private void toggleTransform() {
        transformOpen.update(open -> !open);
    }

    private void toggleRenderer() {
        rendererOpen.update(open -> !open);
    }

    private void toggleAdvanced() {
        advancedOpen.update(open -> !open);
    }

    private void clearSearch() {
        search.set("");
    }

    private void clearCommand() {
        command.set("");
    }
}
