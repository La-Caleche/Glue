package fr.lacaleche.glue.client.options;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsOptionsPageTest {

    private record Settings(int budget, boolean glow) {

        Settings withBudget(int value) {
            return new Settings(value, glow);
        }

        Settings withGlow(boolean value) {
            return new Settings(budget, value);
        }
    }

    private static GraphicsOptionsPage.Builder<Settings> builder(AtomicReference<Settings> saved) {
        return GraphicsOptionsPage.builder("test.options", saved::get, saved::set);
    }

    @Test
    void sectionsKeepTheirOrderAndRowsTheirSection() {
        AtomicReference<Settings> saved = new AtomicReference<>(new Settings(8, false));
        GraphicsOptionsPage<Settings> page = builder(saved)
                .section()
                .level("test.options.preset", settings -> Component.literal("x"), (settings, back) -> settings)
                .section(Component.literal("Particles"))
                .slider("test.options.budget", 0, 16, 4, value -> Component.literal(""), Settings::budget,
                        Settings::withBudget, OptionImpact.HIGH)
                .toggle("test.options.glow", Settings::glow, Settings::withGlow, OptionImpact.LOW)
                .build();

        assertEquals(2, page.sections().size());
        assertFalse(page.sections().get(0).title().isPresent());
        assertEquals(List.of("test.options.preset"), keys(page.sections().get(0)));
        assertTrue(page.sections().get(1).title().isPresent());
        assertEquals(List.of("test.options.budget", "test.options.glow"), keys(page.sections().get(1)));
    }

    @Test
    void halfMarksOnlyTheRowBeforeIt() {
        GraphicsOptionsPage<Settings> page = builder(new AtomicReference<>(new Settings(8, false)))
                .section()
                .toggle("a", Settings::glow, Settings::withGlow, OptionImpact.LOW).half()
                .toggle("b", Settings::glow, Settings::withGlow, OptionImpact.LOW)
                .build();

        List<OptionRow<Settings>> rows = page.sections().getFirst().rows();
        assertTrue(rows.get(0).half());
        assertFalse(rows.get(1).half());
    }

    @Test
    void keysHangUnderThePrefix() {
        GraphicsOptionsPage<Settings> page = builder(new AtomicReference<>(new Settings(8, false)))
                .section().toggle("a", Settings::glow, Settings::withGlow, OptionImpact.LOW).build();

        assertEquals("test.options.title", page.key("title"));
        assertEquals("test.options.button.tooltip", page.key("button.tooltip"));
    }

    @Test
    void savedAndApplyGoThroughTheModsValue() {
        AtomicReference<Settings> saved = new AtomicReference<>(new Settings(8, false));
        GraphicsOptionsPage<Settings> page = builder(saved)
                .section().toggle("a", Settings::glow, Settings::withGlow, OptionImpact.LOW).build();
        OptionRow.Toggle<Settings> glow = (OptionRow.Toggle<Settings>) page.sections().getFirst().rows().getFirst();

        page.apply(glow.with().apply(page.saved(), true));

        assertEquals(new Settings(8, true), saved.get());
    }

    @Test
    void sectionsWithoutRowsAreLeftOut() {
        GraphicsOptionsPage<Settings> page = builder(new AtomicReference<>(new Settings(8, false)))
                .section(Component.literal("Empty"))
                .section().toggle("a", Settings::glow, Settings::withGlow, OptionImpact.LOW)
                .build();

        assertEquals(1, page.sections().size());
        assertFalse(page.sections().getFirst().title().isPresent());
    }

    @Test
    void misuseIsRefused() {
        AtomicReference<Settings> saved = new AtomicReference<>(new Settings(8, false));
        assertThrows(IllegalStateException.class,
                () -> builder(saved).toggle("a", Settings::glow, Settings::withGlow, OptionImpact.LOW));
        assertThrows(IllegalStateException.class, () -> builder(saved).section().half());
        assertThrows(IllegalStateException.class, () -> builder(saved).section().build());
        assertThrows(IllegalStateException.class, () -> builder(saved).section()
                .level("p", settings -> Component.literal("x"), (settings, back) -> settings).half());
    }

    @Test
    void aSliderRangeIsAWholeNumberOfSteps() {
        assertThrows(IllegalArgumentException.class, () -> new OptionRow.Slider<Settings>("s", 0, 10, 4,
                value -> Component.literal(""), Settings::budget, Settings::withBudget, OptionImpact.LOW, false));
        assertThrows(IllegalArgumentException.class, () -> new OptionRow.Slider<Settings>("s", 8, 8, 1,
                value -> Component.literal(""), Settings::budget, Settings::withBudget, OptionImpact.LOW, false));
    }

    @Test
    void aSliderPositionCountsStepsFromItsMinimum() {
        OptionRow.Slider<Settings> slider = new OptionRow.Slider<>("s", 64, 1024, 64,
                value -> Component.literal(""), Settings::budget, Settings::withBudget, OptionImpact.LOW, false);

        assertEquals(15, slider.positions());
        assertEquals(64, slider.valueAt(0));
        assertEquals(1024, slider.valueAt(15));
        assertEquals(1, slider.positionOf(128));
        assertEquals(2, slider.positionOf(170));
        assertEquals(0, slider.positionOf(0));
        assertEquals(15, slider.positionOf(4096));
    }

    private static List<String> keys(GraphicsOptionsPage.Section<Settings> section) {
        return section.rows().stream().map(OptionRow::key).toList();
    }
}
