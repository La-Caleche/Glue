package fr.lacaleche.glue.mcsx.core.bind;

import fr.lacaleche.glue.mcsx.core.controller.OnClick;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.VariantStyleResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementResolverTest {

    record Row(String name) {
    }

    static final class Demo extends ScreenController {
        final Signal<Boolean> checked = signal(false);
        final Signal<String> choice = signal("a");
        /** A plain field: readable, but not writable — resolveSignal yields null for it. */
        final String title = "hello";
        Object lastArgument = "none";
        int noArgCalls;

        void reset() {
            noArgCalls++;
        }

        void remove(Object what) {
            lastArgument = what;
        }

        @OnClick("save")
        void onSave() {
            noArgCalls++;
        }
    }

    private final Demo controller = new Demo();
    private final BindingResolver bindings = new BindingResolver(controller);
    private final ElementResolver elements =
            new ElementResolver(bindings, new VariantStyleResolver(bindings));

    private static McsxAttribute binding(String name, String ref) {
        return new McsxAttribute(name, ref, true);
    }

    private static McsxAttribute literal(String name, String value) {
        return new McsxAttribute(name, value, false);
    }

    private static McsxElement element(McsxAttribute... attributes) {
        return new McsxElement("div", List.of(attributes), List.of(), 1, 1);
    }

    private static McsxElement withChildren(List<McsxContent> children) {
        return new McsxElement("div", List.of(), children, 1, 1);
    }

    @Test
    void resolvesANoArgControllerHandler() {
        elements.handlerOrNull("reset", null).run();
        assertEquals(1, controller.noArgCalls);
    }

    @Test
    void anUnknownHandlerIsNullRatherThanAnError() {
        assertNull(elements.handlerOrNull("nope", null));
    }

    /**
     * The rule that is easiest to get wrong: a one-arg handler binds to the nearest LOOP item, so a
     * {@code <state>} or {@code select=} scope pushed between the row and the click must not shadow it.
     */
    @Test
    void aOneArgHandlerBindsTheNearestLoopItemNotTheInnermostScope() {
        Scope row = Scope.item("item", new Row("first"), null);
        Scope shadowing = Scope.of("open", new Signal<>(true), row);

        elements.handlerOrNull("remove", shadowing).run();

        assertEquals(new Row("first"), controller.lastArgument);
    }

    @Test
    void outsideAnyLoopAOneArgHandlerGetsNull() {
        elements.handlerOrNull("remove", null).run();
        assertNull(controller.lastArgument);
    }

    /** A component forwards a caller's handler as a prop; the name in scope names the real method. */
    @Test
    void aScopePropHoldingAMethodNameForwardsToThatMethod() {
        elements.handlerOrNull("onClick", Scope.of("onClick", "reset", null)).run();
        assertEquals(1, controller.noArgCalls);
    }

    /** An already-bound Runnable prop is used as-is — it carries the caller's scope with it. */
    @Test
    void anAlreadyBoundRunnablePropIsForwardedUnchanged() {
        Runnable bound = () -> controller.lastArgument = "from-caller";
        elements.handlerOrNull("onClick", Scope.of("onClick", bound, null)).run();
        assertEquals("from-caller", controller.lastArgument);
    }

    /**
     * A prop naming a handler binds at the CALL SITE, so a one-arg method still receives the enclosing
     * loop item — the component replaces the scope with its own props, and a name resolved later would
     * pick up a prop instead of the row.
     */
    @Test
    void propValueBindsAHandlerAtTheCallSite() {
        Scope row = Scope.item("item", new Row("second"), null);

        Object forwarded = elements.propValue("remove", row, element());

        assertNotNull(forwarded);
        ((Runnable) forwarded).run();
        assertEquals(new Row("second"), controller.lastArgument);
    }

    @Test
    void clickPrefersAnExplicitOnClickOverTheAnnotation() {
        elements.clickAction(element(binding("onClick", "reset"), literal("id", "save")), null).run();
        assertEquals(1, controller.noArgCalls);
    }

    @Test
    void anUnfilledExplicitClickDoesNotFallThroughToAnotherAction() {
        assertNull(elements.clickAction(
                element(binding("onClick", "missing"), literal("id", "save")), null));
    }

    @Test
    void clickFallsBackToTheOnClickAnnotationForTheElementId() {
        elements.clickAction(element(literal("id", "save")), null).run();
        assertEquals(1, controller.noArgCalls);
    }

    @Test
    void clickFallsBackToTheStateWriteWhenNothingElseMatches() {
        elements.clickAction(element(binding("toggle", "checked")), null).run();
        assertTrue(controller.checked.get());
    }

    @Test
    void anElementThatBindsNothingHasNoClickAction() {
        assertNull(elements.clickAction(element(), null));
    }

    @Test
    void toggleFlipsTheBooleanBothWays() {
        Runnable toggle = elements.stateWriter(element(binding("toggle", "checked")), null);
        toggle.run();
        assertTrue(controller.checked.get());
        toggle.run();
        assertFalse(controller.checked.get());
    }

    /** Both may sit on one element: a select option picks its value AND closes the menu. */
    @Test
    void selectAndToggleOnOneElementBothFire() {
        elements.stateWriter(
                element(binding("select", "choice"), literal("value", "b"), binding("toggle", "checked")),
                null).run();

        assertEquals("b", controller.choice.get());
        assertTrue(controller.checked.get());
    }

    @Test
    void selectWithoutAValueIsAnAuthoringError() {
        McsxElement bad = element(binding("select", "choice"));
        assertThrows(McsxBindException.class, () -> elements.stateWriter(bad, null));
    }

    /** A component can read a caller's plain field, but it cannot write one — say so at bind time. */
    @Test
    void writingAReadOnlyBindingIsAnAuthoringError() {
        McsxElement bad = element(binding("toggle", "title"));
        assertThrows(McsxBindException.class, () -> elements.stateWriter(bad, null));
    }

    @Test
    void stateDeclaresASignalScopedToTheSubtree() {
        McsxElement state = new McsxElement("state",
                List.of(literal("name", "open"), literal("initial", "true")), List.of(), 2, 1);

        Scope scoped = elements.stateScope(withChildren(List.of(state)), null);

        assertNotNull(scoped);
        assertEquals("open", scoped.name());
        assertEquals(Boolean.TRUE, ((Signal<?>) scoped.value()).get());
    }

    /** {@code initial} is a Boolean only when it reads true/false — those are what toggle= writes. */
    @Test
    void aNonBooleanInitialStaysAString() {
        McsxElement state = new McsxElement("state",
                List.of(literal("name", "tab"), literal("initial", "general")), List.of(), 2, 1);

        Scope scoped = elements.stateScope(withChildren(List.of(state)), null);

        assertEquals("general", ((Signal<?>) scoped.value()).get());
    }

    /**
     * Every build mints a FRESH signal — which is precisely what a spurious gate rebuild destroys: a
     * {@code <for>} that re-runs because it wrongly subscribed to one row's signal silently resets the
     * {@code <state>} of every other row. Guards against "fixing" that by caching state instead of
     * fixing the subscription.
     */
    @Test
    void stateScopeMintsAFreshSignalPerBuild() {
        McsxElement state = new McsxElement("state",
                List.of(literal("name", "open"), literal("initial", "false")), List.of(), 2, 1);
        McsxElement owner = withChildren(List.of(state));

        Signal<?> first = (Signal<?>) elements.stateScope(owner, null).value();
        Signal<?> second = (Signal<?>) elements.stateScope(owner, null).value();

        assertNotSame(first, second);
    }

    @Test
    void stateWithoutANameIsAnAuthoringError() {
        McsxElement state = new McsxElement("state", List.of(), List.of(), 2, 1);
        McsxElement owner = withChildren(List.of(state));
        assertThrows(McsxBindException.class, () -> elements.stateScope(owner, null));
    }

    /** {@code selected} lets a radio restyle itself without the caller computing anything. */
    @Test
    void selectionScopeExposesSelectedAgainstTheLiveSignal() {
        McsxElement radio = element(binding("select", "choice"), literal("value", "b"));

        Scope scoped = elements.selectionScope(radio, null);
        assertEquals("selected", scoped.name());

        @SuppressWarnings("unchecked")
        java.util.function.Supplier<Object> selected =
                (java.util.function.Supplier<Object>) scoped.value();
        assertEquals(false, selected.get());

        controller.choice.set("b");
        assertEquals(true, selected.get(), "tracks the signal — it is not a snapshot");
    }

    @Test
    void anElementWithNoSelectIntroducesNoScope() {
        assertNull(elements.selectionScope(element(), null));
    }

    @Test
    void dismissWriterClearsTheOpenSignalSoTheGateCanFireAgain() {
        controller.checked.set(true);
        elements.dismissWriter(binding("open", "checked"), null, element()).run();
        assertFalse(controller.checked.get());
    }

    @Test
    void aReadOnlyOpenBindingHasNoDismissWriter() {
        assertNull(elements.dismissWriter(binding("open", "title"), null, element()));
    }

    @Test
    void stringAttributePrefersTheLiteralAndFallsBackToTheBinding() {
        assertEquals("hi", elements.stringAttribute(element(literal("tooltip", "hi")), "tooltip", null));
        assertEquals("forwarded", elements.stringAttribute(
                element(binding("tooltip", "label")), "tooltip", Scope.of("label", "forwarded", null)));
        assertNull(elements.stringAttribute(element(), "tooltip", null));
    }

    @Test
    void bindingAttributeIgnoresALiteralOfTheSameName() {
        assertNull(bindingAttributeOf(element(literal("w", "40")), "w"));
        assertNotNull(bindingAttributeOf(element(binding("w", "width")), "w"));
    }

    private static McsxAttribute bindingAttributeOf(McsxElement element, String name) {
        return ElementResolver.bindingAttribute(element, name);
    }

    @Test
    void variantConfigTagsAreNotRenderedContent() {
        assertTrue(ElementResolver.isVariantConfig(new McsxElement("variants", List.of(), List.of(), 1, 1)));
        assertTrue(ElementResolver.isVariantConfig(new McsxElement("case", List.of(), List.of(), 1, 1)));
        assertTrue(ElementResolver.isVariantConfig(new McsxElement("state", List.of(), List.of(), 1, 1)));
        assertFalse(ElementResolver.isVariantConfig(element()));
    }

    @Test
    void composeJoinsLiteralsAndLiveBindings() {
        List<Object> parts = List.of("n=", (java.util.function.Supplier<Object>) () -> controller.choice.get());
        assertEquals("n=a", ElementResolver.compose(parts).toString());
        controller.choice.set("z");
        assertEquals("n=z", ElementResolver.compose(parts).toString());
        assertTrue(ElementResolver.isDynamic(parts));
        assertFalse(ElementResolver.isDynamic(List.of("static")));
    }
}
