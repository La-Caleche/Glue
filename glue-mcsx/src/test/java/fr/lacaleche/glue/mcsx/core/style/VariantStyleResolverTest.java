package fr.lacaleche.glue.mcsx.core.style;

import fr.lacaleche.glue.mcsx.core.bind.BindingResolver;
import fr.lacaleche.glue.mcsx.core.bind.McsxBindException;
import fr.lacaleche.glue.mcsx.core.bind.Scope;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Reactive;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.FontWeight;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantStyleResolverTest {

    record Row(String emphasis) {
    }

    static final class Demo extends ScreenController {
    }

    private final VariantStyleResolver styles = new VariantStyleResolver(new BindingResolver(new Demo()));

    private static McsxElement element(List<McsxAttribute> attributes, List<McsxContent> children) {
        return new McsxElement("div", attributes, children, 1, 1);
    }

    private static McsxAttribute attribute(String name, String value) {
        return new McsxAttribute(name, value, false);
    }

    @Test
    void parsesAPlainClassString() {
        StyleSpec spec = styles.resolveStyle(
                element(List.of(attribute("class", "font-bold")), List.of()), null);
        assertEquals(FontWeight.BOLD, spec.fontWeight());
    }

    @Test
    void invalidRuntimeClassFailsWithTheElementPosition() {
        McsxBindException failure = assertThrows(McsxBindException.class,
                () -> styles.resolveStyle(
                        element(List.of(attribute("class", "bg-slate-800")), List.of()), null));

        assertTrue(failure.getMessage().contains("line 1, column 1"));
    }

    /** A bare {@code {name}} hole is an optional scope prop — the caller-class merging path. */
    @Test
    void interpolatesACallerClassFromScope() {
        StyleSpec spec = styles.resolveStyle(
                element(List.of(attribute("class", "p-2 {class}")), List.of()),
                Scope.of("class", "font-bold", null));
        assertEquals(FontWeight.BOLD, spec.fontWeight());
    }

    /** An unpassed prop contributes nothing rather than leaking a literal into the class string. */
    @Test
    void anAbsentPropInterpolatesToNothing() {
        StyleSpec spec = styles.resolveStyle(
                element(List.of(attribute("class", "{class}")), List.of()), null);
        assertNull(spec.fontWeight());
    }

    /** A dotted name is a binding — on a {@code <for>} item here — which is the conditional path. */
    @Test
    void interpolatesADottedBinding() {
        StyleSpec spec = styles.resolveStyle(
                element(List.of(attribute("class", "{item.emphasis}")), List.of()),
                Scope.item("item", new Row("text-danger"), null));
        assertInstanceOf(StyleSpec.ColorValue.TokenRef.class, spec.textColor());
    }

    /** {@code <variants on="size">} selects the {@code <case>} matching the current prop value. */
    @Test
    void variantsSelectTheCaseMatchingTheProp() {
        McsxElement variants = new McsxElement("variants",
                List.of(attribute("on", "size"), attribute("default", "sm")),
                List.of(new McsxElement("case",
                                List.of(attribute("is", "sm"), attribute("class", "font-normal")),
                                List.of(), 2, 1),
                        new McsxElement("case",
                                List.of(attribute("is", "lg"), attribute("class", "font-bold")),
                                List.of(), 3, 1)),
                2, 1);
        McsxElement root = element(List.of(attribute("class", "{size}")), List.of(variants));

        assertEquals(FontWeight.BOLD, styles.resolveStyle(root, Scope.of("size", "lg", null)).fontWeight());
        assertEquals(FontWeight.NORMAL, styles.resolveStyle(root, Scope.of("size", "sm", null)).fontWeight());
    }

    /** With the dimension unset, the {@code default} names the case. */
    @Test
    void variantsFallBackToTheDefaultCase() {
        McsxElement variants = new McsxElement("variants",
                List.of(attribute("on", "size"), attribute("default", "lg")),
                List.of(new McsxElement("case",
                        List.of(attribute("is", "lg"), attribute("class", "font-bold")),
                        List.of(), 2, 1)),
                2, 1);
        McsxElement root = element(List.of(attribute("class", "{size}")), List.of(variants));
        assertEquals(FontWeight.BOLD, styles.resolveStyle(root, null).fontWeight());
    }

    @Test
    void optionalIntSkipsANonNumericPropValue() {
        assertEquals(12, VariantStyleResolver.optionalInt("12"));
        assertNull(VariantStyleResolver.optionalInt("sm"));
        assertNull(VariantStyleResolver.optionalInt(null));
    }

    /**
     * The real {@code checkbox.mcsx} shape. {@code resolveStyle} reads the {@code checked} prop through
     * the {@code <variants on>} lookup, so it is a TRACKED read: an enclosing effect subscribes to it.
     * That is exactly why a {@code <for>} gate must build its rows inside {@link Reactive#untracked} —
     * otherwise the gate subscribes to every row's signal. Untracking must change only the attribution,
     * never the resolved value.
     */
    @Test
    void resolveStyleIsATrackedReadAndIsSuppressedByUntracked() {
        Signal<Boolean> checked = new Signal<>(false);
        McsxElement root = checkbox();
        // Scope.of(name, Supplier) is what ElementResolver.propValue puts in a component's params
        // scope for a dotted prop such as checked={item.done}.
        Scope scope = Scope.of("checked", (Supplier<Object>) checked::get, null);

        List<StyleSpec> tracked = new ArrayList<>();
        Effect.of(() -> tracked.add(styles.resolveStyle(root, scope)));
        checked.set(true);
        assertEquals(2, tracked.size(), "resolveStyle over a <variants on> prop is a tracked read");
        assertBorder(Tokens.BORDER_STRONG, tracked.get(0));
        assertBorder(Tokens.ACCENT, tracked.get(1));

        checked.set(false);
        List<StyleSpec> untracked = new ArrayList<>();
        Effect.of(() -> untracked.add(Reactive.untracked(() -> styles.resolveStyle(root, scope))));
        checked.set(true);
        assertEquals(1, untracked.size(), "an untracked resolveStyle must not subscribe the gate");
        assertBorder(Tokens.BORDER_STRONG, untracked.get(0));

        assertBorder(Tokens.ACCENT, Reactive.untracked(() -> styles.resolveStyle(root, scope)),
                "untracking changes attribution, not the selected <case>");
    }

    /**
     * The shipped {@code glass.mcsx} tab-strip reproducer: a dotted {@code {item.underlineClasses}} hole
     * backed by a {@code Computed<String>}, which reaches the signal through {@code interpolatedValue}
     * rather than through the {@code <variants>} lookup. Same tracking, same suppression.
     */
    @Test
    void aDottedComputedHoleIsTrackedAndIsSuppressedByUntracked() {
        Signal<Boolean> active = new Signal<>(false);
        Tab tab = new Tab(new Computed<>(() -> active.get() ? "border-accent" : "border-strong"));
        McsxElement root = element(List.of(attribute("class", "h-[2px] {item.underlineClasses}")), List.of());
        Scope scope = Scope.item("item", tab, null);

        AtomicInteger tracked = new AtomicInteger();
        Effect.of(() -> {
            tracked.incrementAndGet();
            styles.resolveStyle(root, scope);
        });
        active.set(true);
        assertEquals(2, tracked.get(), "a dotted Computed hole is a tracked read");

        active.set(false);
        AtomicInteger untracked = new AtomicInteger();
        Effect.of(() -> {
            untracked.incrementAndGet();
            assertBorder(Tokens.BORDER_STRONG,
                    Reactive.untracked(() -> styles.resolveStyle(root, scope)));
        });
        active.set(true);
        assertEquals(1, untracked.get(), "an untracked dotted hole must not subscribe the gate");

        assertBorder(Tokens.ACCENT, Reactive.untracked(() -> styles.resolveStyle(root, scope)),
                "the Computed stays live for its own observers");
    }

    record Tab(Computed<String> underlineClasses) {
    }

    /**
     * A reactive class must be able to change how the parent lays the element out, not just how it is
     * painted. Core-side that means the resolved spec's flex/sizing properties really do change on a
     * signal write, and that the resolve is a tracked read so the effect owning the child's layout
     * params re-runs. The ModernUI half of the contract — re-deriving the params from that spec —
     * lives in {@code ViewTree.bindLayoutParams}.
     */
    @Test
    void aReactiveClassChangesTheLayoutPropertiesOfTheResolvedSpec() {
        Signal<Boolean> active = new Signal<>(false);
        Pane pane = new Pane(new Computed<>(() -> active.get() ? "grow w-full" : "shrink-0 w-8"));
        McsxElement root = element(List.of(attribute("class", "{item.sizeClasses}")), List.of());
        Scope scope = Scope.item("item", pane, null);

        List<StyleSpec> resolved = new ArrayList<>();
        Effect.of(() -> resolved.add(styles.resolveStyle(root, scope)));
        active.set(true);

        assertEquals(2, resolved.size(), "an effect deriving layout params must re-run on the class");
        assertFalse(resolved.get(0).grow());
        assertEquals(StyleSpec.Length.pixels(32), resolved.get(0).width());
        assertTrue(resolved.get(1).grow());
        assertEquals(StyleSpec.Length.fillParent(), resolved.get(1).width());
    }

    record Pane(Computed<String> sizeClasses) {
    }

    /** The {@code checkbox.mcsx} inner box: an interpolated class plus a {@code <variants on="checked">}. */
    private static McsxElement checkbox() {
        McsxElement variants = new McsxElement("variants",
                List.of(attribute("on", "checked"), attribute("default", "false")),
                List.of(new McsxElement("case",
                                List.of(attribute("is", "false"), attribute("class", "border-strong")),
                                List.of(), 3, 1),
                        new McsxElement("case",
                                List.of(attribute("is", "true"), attribute("class", "border-accent")),
                                List.of(), 4, 1)),
                2, 1);
        return element(List.of(attribute("class", "w-4 h-4 border {checked}")), List.of(variants));
    }

    private static void assertBorder(String token, StyleSpec spec) {
        assertBorder(token, spec, "border color");
    }

    private static void assertBorder(String token, StyleSpec spec, String message) {
        assertEquals(new StyleSpec.ColorValue.TokenRef(token), spec.borderColor(), message);
    }
}
