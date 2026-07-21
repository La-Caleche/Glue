package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.controller.UIController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.util.List;

/**
 * Backs {@code assets/mcsx/ui/glass.mcsx}, the in-game port of {@code design-poc/index.html}.
 * Selection state drives styling through {@link Computed} class strings — bindings stay dumb, so
 * every "is this one active?" conditional lives here rather than in the markup.
 *
 * <p>Dialog, the anchored menu and the palette are all the {@code <overlay>} primitive; {@code Esc}
 * and {@code Ctrl+K} come from {@code <key>}. Toasts still need animation to be worth shipping.
 */
@UIController("mcsx:glass")
public final class GlassController extends ScreenController {

    private final Signal<Integer> progress = signal(62);
    private final Signal<String> username = signal("malo");
    private final Signal<String> server = signal("play::invalid");
    private final Signal<Boolean> autoSave = signal(true);
    private final Signal<Boolean> notifications = signal(true);
    private final Signal<String> difficulty = signal("b");
    private final Signal<String> mode = signal("Survival");
    private final List<String> modes = List.of("Survival", "Creative", "Adventure", "Spectator");
    private final Signal<Integer> volume = signal(65);
    private final Computed<String> volumeLabel = computed(() -> volume.get() + "%");
    private final Computed<Float> progressFraction = computed(() -> progress.get() / 100f);
    private final Computed<Float> volumeFraction = computed(() -> volume.get() / 100f);

    private final Signal<Boolean> dialogOpen = signal(false);
    private final Signal<Boolean> menuOpen = signal(false);
    private final Signal<Boolean> paletteOpen = signal(false);

    private final Signal<String> section = signal("Foundations");
    private final List<NavEntry> nav = List.of(
            new NavEntry("Foundations", "palette"), new NavEntry("Buttons", "box"),
            new NavEntry("Forms", "sliders"), new NavEntry("Disclosure", "layout"),
            new NavEntry("Feedback", "bell"), new NavEntry("Data", "terminal"));

    private final List<String> iconNames = List.of(
            "alert-triangle", "bell", "box", "check", "check-circle", "chevron-down",
            "chevron-left", "chevron-right", "chevron-up", "close", "copy", "download",
            "folder", "info", "layout", "loader", "minus", "moon", "palette", "plus",
            "search", "settings", "sliders", "sun", "terminal", "trash", "user");

    private final Signal<String> tab = signal("Overview");
    private final List<TabEntry> tabs = List.of(
            new TabEntry("Overview", "A retained-mode widget tree rendered over the world."),
            new TabEntry("Stats", "1,204 widgets · 60 fps · 1 draw call for all rounded shapes."),
            new TabEntry("Inventory", "36 slots · 8 hotbar · drag to reorder."));

    private final Signal<String> view = signal("Day");
    private final List<SegmentEntry> segments = List.of(
            new SegmentEntry("Day"), new SegmentEntry("Night"), new SegmentEntry("Auto"));

    private final Signal<String> openFaq = signal("What is Ignis Glass?");
    private final List<FaqEntry> faq = List.of(
            new FaqEntry("What is Ignis Glass?",
                    "A translucent, shadcn-structured design system for MCSX."),
            new FaqEntry("Does it support light mode?",
                    "Yes — Frost (light) and Obsidian (dark) share one token contract."),
            new FaqEntry("Is it accessible?",
                    "Focus borders, keyboard nav, and AA contrast on every control."));

    /** A sidebar row. */
    public final class NavEntry {

        private final String name;
        private final String icon;
        private final Computed<String> rowClasses;
        private final Computed<String> textClasses;

        NavEntry(String name, String icon) {
            this.name = name;
            this.icon = icon;
            this.rowClasses = computed(() -> selected() ? "bg-accent-subtle" : "hover:bg-hover");
            this.textClasses = computed(() -> selected() ? "text-accent font-medium" : "text-muted");
        }

        private boolean selected() {
            return name.equals(section.get());
        }

        public String name() {
            return name;
        }

        public String icon() {
            return icon;
        }

        public Computed<String> rowClasses() {
            return rowClasses;
        }

        public Computed<String> textClasses() {
            return textClasses;
        }
    }

    /** A tab, its panel copy, and the 2px underline that marks the active one. */
    public final class TabEntry {

        private final String name;
        private final String content;
        private final Computed<String> textClasses;
        private final Computed<String> underlineClasses;
        private final Computed<Boolean> active;

        TabEntry(String name, String content) {
            this.name = name;
            this.content = content;
            this.textClasses = computed(() -> selected() ? "text-default font-medium" : "text-muted");
            this.underlineClasses = computed(() -> selected() ? "bg-accent" : "");
            this.active = computed(this::selected);
        }

        private boolean selected() {
            return name.equals(tab.get());
        }

        public String name() {
            return name;
        }

        public String content() {
            return content;
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

    /** A segmented-control option: the active one lifts onto surface-2. */
    public final class SegmentEntry {

        private final String name;
        private final Computed<String> classes;

        SegmentEntry(String name) {
            this.name = name;
            this.classes = computed(() -> name.equals(view.get())
                    ? "bg-surface-2 text-default" : "text-muted hover:bg-hover");
        }

        public String name() {
            return name;
        }

        public Computed<String> classes() {
            return classes;
        }
    }

    /** One accordion row. Only one is open at a time. */
    public final class FaqEntry {

        private final String question;
        private final String answer;
        private final Computed<Boolean> open;

        FaqEntry(String question, String answer) {
            this.question = question;
            this.answer = answer;
            this.open = computed(() -> question.equals(openFaq.get()));
        }

        public String question() {
            return question;
        }

        public String answer() {
            return answer;
        }

        public Computed<Boolean> open() {
            return open;
        }
    }

    private void selectSection(NavEntry entry) {
        section.set(entry.name());
    }

    private void selectTab(TabEntry entry) {
        tab.set(entry.name());
    }

    private void selectView(SegmentEntry entry) {
        view.set(entry.name());
    }

    private void toggleFaq(FaqEntry entry) {
        openFaq.set(entry.open().get() ? "" : entry.question());
    }

    private void openDialog() {
        dialogOpen.set(true);
    }

    private void closeDialog() {
        dialogOpen.set(false);
    }

    private void toggleMenu() {
        menuOpen.update(open -> !open);
    }

    private void closeMenu() {
        menuOpen.set(false);
    }

    private void togglePalette() {
        paletteOpen.update(open -> !open);
    }

    private void closePalette() {
        paletteOpen.set(false);
    }

    private void bumpProgress() {
        progress.update(value -> value >= 100 ? 0 : value + 10);
    }
}
