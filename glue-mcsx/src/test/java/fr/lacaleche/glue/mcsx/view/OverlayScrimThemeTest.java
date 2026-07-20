package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.theme.Theme;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Looper;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A modal overlay's scrim is painted from a theme token, so it must follow a theme switch for as
 * long as the overlay is open. Nothing rebuilds it: the {@code <overlay>} gate subscribes to
 * {@code open=} alone and rebuilds untracked, so reading the token on the build path would freeze it
 * — Obsidian's dark wash left lying over a Frost UI.
 */
class OverlayScrimThemeTest {

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

    /** The shape of {@code dialog.mcsx}: an overlay whose visibility is one signal. */
    private static final class DialogController extends ScreenController {

        private final Signal<Boolean> open = signal(false);
        private final Signal<String> cls = signal("w-96");
        private final Style style = new Style();

        public Signal<Boolean> open() {
            return open;
        }

        public Style style() {
            return style;
        }

        /** A bare {@code {cls}} hole is a scope prop; reaching the controller takes a dotted path. */
        private final class Style {

            public Signal<String> cls() {
                return cls;
            }
        }
    }

    private static final String MODAL_DIALOG = """
            <div class="flex-col">
                <overlay open={open} modal="true" placement="center" class="p-4 bg-surface-2">
                    <text>Delete?</text>
                </overlay>
            </div>
            """;

    private static final String POPOVER = """
            <div class="flex-col">
                <overlay open={open} placement="center" class="p-4 bg-surface-2">
                    <text>Menu</text>
                </overlay>
            </div>
            """;

    /** {@code open()} plays the panel's entrance, and a {@code ValueAnimator} refuses a loop-less thread. */
    @BeforeEach
    void prepareLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    @AfterEach
    void restoreTheme() {
        Themes.active(Themes.OBSIDIAN);
    }

    @Test
    void anOpenModalScrimFollowsAThemeSwitch() {
        Themes.active(Themes.OBSIDIAN);
        Context context = new HeadlessContext();
        DialogController controller = new DialogController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(MODAL_DIALOG), controller, context,
                new ComponentRegistry(), id -> null);

        long obsidian = scrimColor(Themes.OBSIDIAN);
        long frost = scrimColor(Themes.FROST);
        assertNotEquals(obsidian, frost,
                "the test is meaningless if both themes scrim to the same colour");

        controller.open.set(true);
        View scrim = openScrim(instance.root());
        assertEquals(obsidian, colorOf(scrim));

        Themes.toggle();

        assertEquals(frost, colorOf(scrim),
                "the scrim of an open modal must repaint when the theme changes");

        instance.close();
    }

    @Test
    void aNonModalScrimStaysTransparent() {
        Context context = new HeadlessContext();
        DialogController controller = new DialogController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(POPOVER), controller, context,
                new ComponentRegistry(), id -> null);

        controller.open.set(true);
        assertNull(openScrim(instance.root()).getBackground());

        Themes.toggle();

        assertNull(openScrim(instance.root()).getBackground(),
                "a popover has no wash to repaint");
        instance.close();
    }

    /** An open panel's size was snapshotted when the layer opened, so a reactive class could restyle
     *  it but never resize it. The params now live in the panel's own restyle effect. */
    @Test
    void anOpenPanelResizesWhenItsReactiveClassChanges() {
        Context context = new HeadlessContext();
        DialogController controller = new DialogController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument("""
                        <div class="flex-col">
                            <overlay open={open} modal="true" placement="center" class="{style.cls}">
                                <text>Delete?</text>
                            </overlay>
                        </div>
                        """),
                controller, context, new ComponentRegistry(), id -> null);

        controller.open.set(true);
        View panel = openPanel(instance.root());
        assertEquals(384, panel.getLayoutParams().width);

        controller.cls.set("w-64");

        assertEquals(256, panel.getLayoutParams().width,
                "a reactive class on an open <overlay> must resize the panel, not just restyle it");
        instance.close();
    }

    /**
     * A detached host is a torn-down workspace. Its modal layers hold effects subscribed to the
     * global theme signal, so leaving them behind retains the whole screen; detach must close them.
     */
    @Test
    void detachingTheHostClosesItsLayersAndStopsTheScrimTracking() {
        Themes.active(Themes.OBSIDIAN);
        Context context = new HeadlessContext();
        DialogController controller = new DialogController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(MODAL_DIALOG), controller, context,
                new ComponentRegistry(), id -> null);

        controller.open.set(true);
        View scrim = openScrim(instance.root());
        long obsidian = colorOf(scrim);

        OverlayHost host = (OverlayHost) ((ViewGroup) instance.root()).getChildAt(1);
        host.onDetachedFromWindow();

        assertTrue(host.isEmpty(), "detach must close every layer");
        Themes.toggle();
        assertEquals(obsidian, colorOf(scrim),
                "a closed layer's scrim effect is disposed — the theme write must not reach it");
        instance.close();
    }

    /** The panel of the one open layer: the layer's second child, above the scrim. */
    private static View openPanel(View root) {
        ViewGroup host = (ViewGroup) ((ViewGroup) root).getChildAt(1);
        assertInstanceOf(OverlayHost.class, host);
        assertEquals(1, host.getChildCount(), "exactly one layer must be open");
        return ((ViewGroup) host.getChildAt(0)).getChildAt(1);
    }

    /** The scrim of the one open layer: root → OverlayHost → layer → scrim, the layer's first child. */
    private static View openScrim(View root) {
        ViewGroup host = (ViewGroup) ((ViewGroup) root).getChildAt(1);
        assertInstanceOf(OverlayHost.class, host);
        assertEquals(1, host.getChildCount(), "exactly one layer must be open");
        return ((ViewGroup) host.getChildAt(0)).getChildAt(0);
    }

    private static long colorOf(View scrim) {
        Drawable background = scrim.getBackground();
        assertNotNull(background, "a modal layer paints its scrim");
        return assertInstanceOf(ColorDrawable.class, background).getColor();
    }

    /** {@code ColorDrawable} packs its colour, so the expectation goes through the same constructor. */
    private static long scrimColor(Theme theme) {
        return new ColorDrawable(theme.color(Tokens.SCRIM)).getColor();
    }
}
