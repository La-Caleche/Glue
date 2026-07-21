package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.controller.UIController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.util.List;

/**
 * Backs {@code assets/mcsx/ui/editor.mcsx}, the shell of {@code editor-poc/index.html}: a fixed
 * inspector, a growing preview, and a fixed forge column. The preview's hint chips are
 * {@code absolute}-positioned children, which is what this screen exists to exercise.
 *
 * <p>The preview area is a placeholder. Filling it means implementing a {@code SurfaceSource} that
 * renders the particle system to an offscreen target — {@code surface.ExternalSurfaceView} already
 * reserves the space, punches the hole and routes the gestures.
 */
@UIController("mcsx:editor")
public final class EditorController extends ScreenController {

    private final Signal<String> leftTab = signal("History");
    private final List<TabEntry> leftTabs = List.of(
            new TabEntry("History", leftTab), new TabEntry("Assets", leftTab),
            new TabEntry("Debug", leftTab));

    private final Signal<String> phase = signal("INIT");
    private final List<PhaseEntry> phases = List.of(
            new PhaseEntry("SPAWN", 1), new PhaseEntry("INIT", 6),
            new PhaseEntry("UPDATE", 1), new PhaseEntry("OUTPUT", 0));

    private final Signal<Boolean> playing = signal(true);
    private final Computed<String> playGlyph = computed(() -> playing.get() ? "❚❚" : "▶");

    /** A pill tab in the left column. */
    public final class TabEntry {

        private final String name;
        private final Computed<String> classes;
        private final Computed<Boolean> active;

        TabEntry(String name, Signal<String> selection) {
            this.name = name;
            this.classes = computed(() -> name.equals(selection.get())
                    ? "bg-accent-subtle text-accent border border-accent"
                    : "text-muted hover:bg-hover");
            this.active = computed(() -> name.equals(selection.get()));
        }

        public String name() {
            return name;
        }

        public Computed<String> classes() {
            return classes;
        }

        public Computed<Boolean> active() {
            return active;
        }
    }

    /** A phase tab in the forge, underlined when active. {@code UPDATE} carries its own amber accent. */
    public final class PhaseEntry {

        private final String name;
        private final int blocks;
        private final Computed<String> textClasses;
        private final Computed<String> underlineClasses;
        private final Computed<Boolean> active;

        PhaseEntry(String name, int blocks) {
            this.name = name;
            this.blocks = blocks;
            String accent = "UPDATE".equals(name) ? "text-warning" : "text-accent";
            String underline = "UPDATE".equals(name) ? "bg-warning" : "bg-accent";
            this.textClasses = computed(() -> selected() ? accent + " font-bold" : "text-subtle");
            this.underlineClasses = computed(() -> selected() ? underline : "");
            this.active = computed(this::selected);
        }

        private boolean selected() {
            return name.equals(phase.get());
        }

        public String name() {
            return name;
        }

        public int blocks() {
            return blocks;
        }

        public Computed<String> textClasses() {
            return textClasses;
        }

        public Computed<String> underlineClasses() {
            return underlineClasses;
        }

        public Computed<Boolean> active() {
            return active;
        }
    }

    private void selectLeftTab(TabEntry entry) {
        leftTab.set(entry.name());
    }

    private void selectPhase(PhaseEntry entry) {
        phase.set(entry.name());
    }

    private void togglePlay() {
        playing.update(value -> !value);
    }
}
