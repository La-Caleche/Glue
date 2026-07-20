package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.TailwindParser;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.TextView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * CSS text inheritance is reactive. The segmented control in {@code glass.mcsx} — and the editor's
 * tab strip — put the colour on the container ({@code text-default} / {@code text-muted}, bound) and
 * leave the label colourless, so the label's colour can only arrive through {@link InheritedText}.
 *
 * <p>Nothing rebuilds the subtree when the container's class flips: the enclosing {@code <for>} gate
 * subscribes to its list alone and rebuilds untracked. The inherited defaults therefore have to be a
 * live input the label's own restyle effect re-reads, not a value baked at build time.
 */
class InheritedTextPropagationTest {

    /**
     * A whole View tree, unlike the detached {@code View} of {@code ViewStylesTest}: every
     * {@code ViewGroup} constructor reads a styleable off the theme, so the stub has to hand out the
     * system resources and an (empty) theme of their own.
     */
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

    /** The shape of {@code GlassController}'s segmented control, reduced to what colours the label. */
    private static final class SegmentedController extends ScreenController {

        private final Signal<String> active = signal("list");
        private final List<Segment> segments = List.of(new Segment("list"), new Segment("grid"));

        public List<Segment> segments() {
            return segments;
        }

        public final class Segment {

            private final String name;
            private final Computed<String> classes;

            Segment(String name) {
                this.name = name;
                this.classes = computed(() -> name.equals(active.get())
                        ? "bg-surface-2 text-default" : "text-muted");
            }

            public String name() {
                return name;
            }

            public Computed<String> classes() {
                return classes;
            }
        }
    }

    private static final String SEGMENTED_CONTROL = """
            <div class="flex-row">
                <for each={segments} as="seg">
                    <div class="rounded px-4 py-1 {seg.classes}">
                        <text class="text-sm">{{seg.name}}</text>
                    </div>
                </for>
            </div>
            """;

    @Test
    void aContainersReactiveTextClassRecoloursItsColourlessLabel() {
        Context context = new HeadlessContext();
        SegmentedController controller = new SegmentedController();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument(SEGMENTED_CONTROL), controller, context,
                new ComponentRegistry(), id -> null);

        long selected = colorOf(context, "text-default");
        long deselected = colorOf(context, "text-muted");
        assertNotEquals(selected, deselected,
                "the test is meaningless if the two tokens resolve to the same colour");

        List<TextView> labels = labels(instance.root());
        assertEquals(2, labels.size());
        assertEquals(selected, labels.get(0).getCurrentTextColor());
        assertEquals(deselected, labels.get(1).getCurrentTextColor());

        controller.active.set("grid");

        assertEquals(deselected, labels.get(0).getCurrentTextColor(),
                "the deselected segment's label must follow its container's text-* class");
        assertEquals(selected, labels.get(1).getCurrentTextColor());

        instance.close();
    }

    /** {@code TextView} packs its colour, so the expectation goes through the same setter the binder
     *  uses rather than reproducing the packing. */
    private static long colorOf(Context context, String textClass) {
        StyleSpec spec = TailwindParser.parse(textClass);
        TextView reference = new TextView(context);
        reference.setTextColor(ViewStyles.resolve(spec.textColor()));
        return reference.getCurrentTextColor();
    }

    private static List<TextView> labels(View root) {
        List<TextView> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(View view, List<TextView> found) {
        if (view instanceof TextView text) {
            found.add(text);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), found);
            }
        }
    }
}
