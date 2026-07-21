package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Looper;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code <for>} keys rows by item VALUE: a list mutation reuses every row whose item survives it —
 * same View instances, same effects, same {@code <state>} signals — and rebuilds only what actually
 * changed. Before this, any append tore down and rebuilt ALL rows, and because each rebuild mints
 * fresh {@code <state>} signals, adding one row to a list of Selects closed every open menu.
 *
 * <p>Identity is the instrument, as in {@code GateSubscriptionTest}: a rebuilt row is a fresh View,
 * so a child that survived a mutation by reference is a row that was reused.
 */
class KeyedForTest {

    /** {@code ScrollView}/{@code ValueAnimator} paths reach for the singleton; installing it opens no window. */
    @BeforeEach
    void prepareRuntime() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /** {@link ViewGroup}'s constructor reads a styleable off the theme, so the stub must supply one. */
    private static final class HeadlessContext extends Context {

        private final Resources resources = Resources.getSystem();
        private final Resources.Theme theme = resources.newTheme();

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public void setTheme(ResourceId resId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return theme;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }

    /**
     * A row item owning reactive state, as a selectable list row does. Records compare by component,
     * and {@link Signal}/{@link Computed} compare by identity — so two {@code create()}d items are
     * distinct keys while the same instance re-listed is an equal one.
     */
    private record Item(Signal<Boolean> selected, Computed<String> cls) {

        static Item create() {
            Signal<Boolean> selected = new Signal<>(false);
            return new Item(selected,
                    new Computed<>(() -> Boolean.TRUE.equals(selected.get()) ? "bg-surface" : ""));
        }
    }

    private static final class KeyedController extends ScreenController {

        private final Signal<List<?>> items = signal(List.of());
    }

    private static final String BOUND_ROWS = """
            <div>
                <for each={items} as="it">
                    <div class="{it.cls}"/>
                </for>
            </div>
            """;

    private static final String STATEFUL_ROWS = """
            <div>
                <for each={items} as="it">
                    <div>
                        <state name="open" initial="false"/>
                        <button toggle={open} class="p-1"/>
                        <if cond={open}><text>menu</text></if>
                    </div>
                </for>
            </div>
            """;

    private static final String PLAIN_ROWS = """
            <div>
                <for each={items} as="it">
                    <div class="p-1"/>
                </for>
            </div>
            """;

    private KeyedController controller;

    /** The {@code <for>} container: root FrameLayout → document root div → loop. */
    private ViewGroup bind(String document, List<?> items) {
        controller = new KeyedController();
        controller.items.set(items);
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(document), controller, new HeadlessContext(),
                new ComponentRegistry(), id -> null);
        ViewGroup content = (ViewGroup) ((ViewGroup) instance.root()).getChildAt(0);
        return (ViewGroup) content.getChildAt(0);
    }

    @Test
    void anAppendReusesTheExistingRowsAndTheirEffects() {
        Item first = Item.create();
        Item second = Item.create();
        ViewGroup loop = bind(BOUND_ROWS, List.of(first, second));
        View firstRow = loop.getChildAt(0);
        View secondRow = loop.getChildAt(1);

        controller.items.set(List.of(first, second, Item.create()));

        assertEquals(3, loop.getChildCount());
        assertSame(firstRow, loop.getChildAt(0), "an unchanged item's row must survive the append");
        assertSame(secondRow, loop.getChildAt(1));

        first.selected().set(true);

        assertNotNull(firstRow.getBackground(),
                "a reused row's restyle effect survived the append and must still repaint it");
        assertNull(secondRow.getBackground(), "an untouched row must not restyle");
    }

    @Test
    void aReusedRowsStateSurvivesTheAppend() {
        ViewGroup loop = bind(STATEFUL_ROWS, List.of("a", "b"));
        ViewGroup firstRow = (ViewGroup) loop.getChildAt(0);
        View toggle = firstRow.getChildAt(0);
        ViewGroup menuGate = (ViewGroup) firstRow.getChildAt(1);
        assertEquals(0, menuGate.getChildCount());

        toggle.performClick();
        assertEquals(1, menuGate.getChildCount(), "the toggle must open this row's menu");
        View menu = menuGate.getChildAt(0);

        controller.items.set(List.of("a", "b", "c"));

        assertSame(firstRow, loop.getChildAt(0));
        assertEquals(1, menuGate.getChildCount(),
                "appending an item must not re-mint the sibling rows' <state> — the menu stays open");
        assertSame(menu, menuGate.getChildAt(0));
        assertEquals(3, loop.getChildCount());
    }

    @Test
    void removingAMiddleItemDropsOnlyItsRow() {
        ViewGroup loop = bind(PLAIN_ROWS, List.of("a", "b", "c"));
        View first = loop.getChildAt(0);
        View middle = loop.getChildAt(1);
        View last = loop.getChildAt(2);

        controller.items.set(List.of("a", "c"));

        assertEquals(2, loop.getChildCount());
        assertSame(first, loop.getChildAt(0), "the rows around the removed one must survive");
        assertSame(last, loop.getChildAt(1), "the survivor must move up to keep the list's order");
        assertNull(middle.getParent(), "the removed item's row must leave the tree");
    }

    /** Equal items cannot be told apart, so matching is greedy in order: duplicates degrade to
     *  positional reuse rather than double-claiming one row. */
    @Test
    void duplicateItemsReusePositionally() {
        ViewGroup loop = bind(PLAIN_ROWS, List.of("x", "x"));
        assertEquals(2, loop.getChildCount());
        View first = loop.getChildAt(0);
        View second = loop.getChildAt(1);

        controller.items.set(List.of("x", "x", "x"));

        assertEquals(3, loop.getChildCount());
        assertSame(first, loop.getChildAt(0));
        assertSame(second, loop.getChildAt(1));
    }
}
