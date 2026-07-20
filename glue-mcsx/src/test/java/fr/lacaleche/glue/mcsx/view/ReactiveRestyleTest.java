package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.text.FontPaint;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bound {@code class} is a whole class string, not a patch: whatever the previous one painted, the
 * one that replaces it fully determines the view. Every channel a reactive restyle writes therefore
 * has to have a reset baseline, and each of the ones asserted here was missing it — an {@code <input>}
 * kept a {@code border-danger} it had dropped, a {@code <text>} stayed bold and centred, a
 * presentational {@code <div>} stayed clickable and went on swallowing its ancestors' clicks.
 *
 * <p>Exercised through the real {@code ViewBinder.bind} path, so the ownership rules that make the
 * fix subtle are live too: the click wiring runs once at build while the restyle effect re-runs
 * without it.
 */
class ReactiveRestyleTest {

    /**
     * {@code new ScrollView(context)} enables its scrollbars, which resolves padding, which asks the
     * {@code ModernUI} singleton whether RTL is supported — so a {@code <scroll>} needs one to exist.
     * The no-arg constructor only installs the instance and its system resources; it opens no window.
     */
    @BeforeAll
    static void installModernUi() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
    }

    /** Every {@code ViewGroup} constructor reads a styleable off the theme — see the equivalent stub
     *  in {@code InheritedTextPropagationTest}. */
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
     * The reachable shape: {@code <Input class={…}>} is handed a class string that a validation state
     * drives — {@code "border-danger"} one moment, {@code ""} the next. A bare {@code {name}} hole is
     * an optional scope prop, so reaching the controller from a {@code class} takes a dotted path,
     * exactly as {@code glass.mcsx}'s {@code {seg.classes}} does.
     */
    private static final class RestyleController extends ScreenController {

        private final Signal<String> classes = signal(
                "bg-surface rounded border font-bold text-center hover:bg-surface-2");
        private final Field field = new Field();

        public Field field() {
            return field;
        }

        public void ping() {
        }

        private final class Field {

            public Signal<String> classes() {
                return classes;
            }
        }
    }

    private static final String DOCUMENT = """
            <div>
                <input class="{field.classes}"/>
                <scroll class="{field.classes}"><text>row</text></scroll>
                <text class="{field.classes}">label</text>
                <div class="{field.classes}"/>
                <div onClick={ping} class="{field.classes}"/>
            </div>
            """;

    private ViewGroup content;
    private RestyleController controller;

    private void bind() {
        Context context = new HeadlessContext();
        controller = new RestyleController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(DOCUMENT), controller, context,
                new ComponentRegistry(), id -> null);
        content = (ViewGroup) ((ViewGroup) instance.root()).getChildAt(0);
    }

    private View child(int index) {
        return content.getChildAt(index);
    }

    /**
     * The blocker: {@code <input>} and {@code <scroll>} were restyled with the leave-alone overload,
     * on the theory that they had a ModernUI default box to preserve. They have none —
     * {@code EditText(Context)} only makes itself focusable and clickable, {@code ScrollView(Context)}
     * only sets up scrolling — so nothing was being preserved and everything was being kept.
     */
    @Test
    void anInputAndAScrollDropTheBoxTheirNewClassNoLongerDeclares() {
        bind();
        EditText input = (EditText) child(0);
        ScrollView scroll = (ScrollView) child(1);
        assertNotNull(input.getBackground());
        assertNotNull(scroll.getBackground());

        controller.classes.set("");

        assertNull(input.getBackground(), "a dropped border/bg must stop painting on an <input>");
        assertNull(scroll.getBackground(), "a dropped bg must stop painting on a <scroll>");
    }

    /** An {@code <input>} layers its class over the inherited text defaults and never reset them, so a
     *  dropped {@code font-bold} stayed bold — the same staleness as its box, one channel over. */
    @Test
    void anInputResetsTheTextDefaultsItsNewClassNoLongerDeclares() {
        bind();
        EditText input = (EditText) child(0);
        assertEquals(FontPaint.BOLD, input.getTextStyle());

        controller.classes.set("");

        assertEquals(FontPaint.NORMAL, input.getTextStyle(), "a dropped font-bold must stop rendering bold");
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.START, input.getGravity(),
                "text-* owns the horizontal bits only — an EditText centres its text vertically itself");
    }

    /** Weight and alignment were written only when the spec declared them, so once a class had set
     *  them the view could never be told to stop. Weight falls back to what the subtree inherits;
     *  alignment is not inherited, so it falls back to start. */
    @Test
    void aTextResetsTheWeightAndAlignmentItsNewClassNoLongerDeclares() {
        bind();
        TextView text = (TextView) child(2);
        assertEquals(FontPaint.BOLD, text.getTextStyle());
        assertEquals(Gravity.TOP | Gravity.CENTER_HORIZONTAL, text.getGravity());

        controller.classes.set("");

        assertEquals(FontPaint.NORMAL, text.getTextStyle(), "a dropped font-bold must stop rendering bold");
        assertEquals(Gravity.TOP | Gravity.START, text.getGravity(),
                "a dropped text-center must stop centring");
    }

    /**
     * The clickable flag has two owners. A {@code hover:} utility raises it so the state variant can
     * fire at all, and dropping that utility has to give it back — a presentational div left clickable
     * eats every click aimed at its ancestors. But an element that wires a handler of its own must
     * keep it: {@code wireClick} runs once at build and never again, while this effect re-runs on
     * every restyle, so a naive clear would kill the handler on its first tick.
     */
    @Test
    void droppingAHoverVariantGivesTheClickableFlagBackUnlessAHandlerOwnsIt() {
        bind();
        View presentational = child(3);
        View handled = child(4);
        assertTrue(presentational.isClickable());
        assertTrue(handled.isClickable());

        controller.classes.set("");

        assertFalse(presentational.isClickable(),
                "a div that dropped its hover: utilities must stop swallowing its ancestors' clicks");
        assertTrue(handled.isClickable(),
                "an onClick={} element stays clickable across a restyle — the effect must not disown it");
    }

    /** {@code focus:} is the focusable flag's only owner, so it tracks the class string exactly. */
    @Test
    void droppingAFocusVariantGivesTheFocusableFlagBack() {
        bind();
        controller.classes.set("focus:border-accent");
        View presentational = child(3);
        assertTrue(presentational.isFocusable());

        controller.classes.set("");

        assertFalse(presentational.isFocusable());
    }

    /** A {@code <scroll>} gives a transient {@code hover:}'s clickable back, but its focusable is the
     *  constructor's — keyboard scrolling — and must survive a restyle that declares no {@code focus:}. */
    @Test
    void aScrollGivesTheClickableFlagBackButKeepsItsOwnFocusable() {
        bind();
        ScrollView scroll = (ScrollView) child(1);
        assertTrue(scroll.isClickable());
        assertTrue(scroll.isFocusable());

        controller.classes.set("");

        assertFalse(scroll.isClickable(), "a dropped hover: must stop the scroll swallowing clicks");
        assertTrue(scroll.isFocusable(), "focusable belongs to the ScrollView, not the class string");
    }

    /**
     * The flag's third owner: a native component that reuses {@code buildStyledContainer} and wires
     * its own listener, which no markup-derived check can see. The listener raises clickable itself;
     * the restyle effect must not take it back.
     */
    @Test
    void aListenerWiredOutsideTheMarkupSurvivesARestyle() {
        bind();
        View reused = child(3);
        reused.setOnClickListener(v -> {
        });
        assertTrue(reused.isClickable());

        controller.classes.set("");

        assertTrue(reused.isClickable(),
                "a native's own click listener must survive the markup-blind restyle");
    }

    /** The document root was the one element a reactive class could restyle but never resize:
     *  its params were derived once at bind. */
    @Test
    void aReactiveRootClassResizesTheRoot() {
        Context context = new HeadlessContext();
        controller = new RestyleController();
        controller.classes.set("w-96");
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument("<div class=\"{field.classes}\"><text>x</text></div>"),
                controller, context, new ComponentRegistry(), id -> null);
        View root = ((ViewGroup) instance.root()).getChildAt(0);
        assertEquals(384, root.getLayoutParams().width);

        controller.classes.set("w-64");

        assertEquals(256, root.getLayoutParams().width,
                "a reactive root class must republish the root's layout params");
        instance.close();
    }
}
