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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A structural gate ({@code <if>}/{@code <for>}/{@code <overlay>}) subscribes to its own condition and
 * to nothing else. It rebuilds a whole subtree inside its effect, and every style read on that build
 * path — here a row's {@code class="{row.cls}"} — would otherwise become a dependency of the gate: one
 * row ticking its checkbox would tear down and rebuild every sibling, minting fresh {@code <state>}
 * signals for all of them. The subtree's own restyle effects, created <em>inside</em> the rebuild, must
 * keep tracking their own reads, so the flipped row still repaints.
 *
 * <p>Identity is the instrument: a gate that re-ran calls {@code removeAllViews} and builds fresh
 * Views, so a child that survived a flip by reference is a gate that did not re-run.
 */
class GateSubscriptionTest {

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
     * The reachable shape: a row owns a signal, and a {@link Computed} turns it into the class string
     * the markup interpolates. {@code {row.cls}} is a dotted path, so it reads the computed through the
     * loop scope — exactly what a selected/checked row does in a real list.
     */
    private record Row(Signal<Boolean> selected, Computed<String> cls) {

        static Row create() {
            Signal<Boolean> selected = new Signal<>(false);
            return new Row(selected,
                    new Computed<>(() -> Boolean.TRUE.equals(selected.get()) ? "bg-surface" : ""));
        }
    }

    private static final class GateController extends ScreenController {

        private final Signal<List<Row>> rows = signal(List.of(Row.create(), Row.create()));
        private final Signal<Boolean> visible = signal(true);
        private final Signal<Boolean> open = signal(false);
        private final Row panel = Row.create();

        Row row(int index) {
            return rows.get().get(index);
        }
    }

    private static final String LOOP = """
            <div>
                <for each={rows} as="row">
                    <div class="{row.cls}"/>
                </for>
            </div>
            """;

    private static final String CONDITIONAL = """
            <div>
                <if cond={visible}>
                    <div class="{panel.cls}"/>
                </if>
            </div>
            """;

    private static final String OVERLAY = """
            <div>
                <overlay open={open} placement="center" class="p-2">
                    <div class="{panel.cls}"/>
                </overlay>
            </div>
            """;

    private GateController controller;
    private ViewInstance instance;

    /** The document root's own view: the host {@code FrameLayout} holds it, then the {@link OverlayHost}. */
    private ViewGroup bind(String document) {
        controller = new GateController();
        instance = ViewBinder.bind(
                McsxParser.parseDocument(document), controller, new HeadlessContext(),
                new ComponentRegistry(), id -> null);
        return (ViewGroup) ((ViewGroup) instance.root()).getChildAt(0);
    }

    @Test
    void aForGateDoesNotSubscribeToTheRowsItBuilds() {
        ViewGroup loop = (ViewGroup) bind(LOOP).getChildAt(0);
        assertEquals(2, loop.getChildCount());
        View first = loop.getChildAt(0);
        View second = loop.getChildAt(1);
        assertNull(first.getBackground());

        controller.row(0).selected().set(true);

        assertSame(first, loop.getChildAt(0),
                "ticking one row must not rebuild the loop — the rebuild resets every sibling's <state>");
        assertSame(second, loop.getChildAt(1));
        assertNotNull(first.getBackground(),
                "the row's own restyle effect still tracks its own reads and must repaint it");
        assertNull(second.getBackground(), "an untouched row must not restyle");

        controller.rows.set(List.of(Row.create()));

        assertEquals(1, loop.getChildCount(), "the list itself is still the gate's dependency");
    }

    @Test
    void anIfGateDoesNotSubscribeToTheBodyItBuilds() {
        ViewGroup gate = (ViewGroup) bind(CONDITIONAL).getChildAt(0);
        View body = gate.getChildAt(0);
        assertNull(body.getBackground());

        controller.panel.selected().set(true);

        assertSame(body, gate.getChildAt(0),
                "a signal read while building the body must not become the <if>'s dependency");
        assertNotNull(body.getBackground());

        controller.visible.set(false);

        assertEquals(0, gate.getChildCount(), "cond= is still the gate's dependency");
    }

    /** {@code <overlay>} rebuilds by closing and reopening its layer, so its panel is the identity to watch. */
    @Test
    void anOverlayGateDoesNotSubscribeToThePanelItBuilds() {
        bind(OVERLAY);
        controller.open.set(true);
        View panel = openPanel();
        View body = ((ViewGroup) panel).getChildAt(0);
        assertNull(body.getBackground());

        controller.panel.selected().set(true);

        assertSame(panel, openPanel(),
                "a signal read while building the panel must not become the <overlay>'s dependency");
        assertNotNull(body.getBackground());

        controller.open.set(false);
        controller.open.set(true);

        assertNotSame(panel, openPanel(), "open= is still the gate's dependency");
        instance.close();
    }

    /** The one open layer's panel: root → {@link OverlayHost} → layer → scrim, then the panel. */
    private View openPanel() {
        ViewGroup host = (ViewGroup) ((ViewGroup) instance.root()).getChildAt(1);
        assertEquals(1, host.getChildCount(), "exactly one layer must be open");
        return ((ViewGroup) host.getChildAt(0)).getChildAt(1);
    }
}
