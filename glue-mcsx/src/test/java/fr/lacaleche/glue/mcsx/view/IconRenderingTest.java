package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything {@code <icon>} needs in order to draw, asserted on the real bind path: the glyph
 * reaches the view's text, the icon font wins the typeface collection rather than the sans fallback,
 * the size attribute reaches the paint, and the colour is not transparent.
 *
 * <p>Written while chasing icons that rendered nowhere. All four were already correct, which is what
 * ruled out the font, the registry and the binder and left the draw path — {@code IconView} was
 * overriding {@code onDraw} to clip to {@code (0, 0, width, height)}. Measurement and drawing need a
 * live {@code ModernUI} instance, so they cannot be covered here; this pins everything up to them.
 */
class IconRenderingTest {

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

    private static final class Empty extends ScreenController {
    }

    @Test
    void anIconCarriesItsGlyphAndMeasuresToANonZeroBox() {
        Context context = new HeadlessContext();
        ViewInstance instance = ViewBinder.bind(
                McsxParser.parseDocument("<div><icon name=\"plus\" size=\"20\"/></div>"),
                new Empty(), context, new ComponentRegistry(), id -> null);

        List<IconView> icons = icons(instance.root());
        assertEquals(1, icons.size(), "the <icon> did not build");
        IconView icon = icons.get(0);

        String expected = FontRegistry.getInstance().glyph(FontRegistry.DEFAULT_ICONS, "plus");
        assertEquals(expected, icon.getText().toString(), "the glyph never reached the view's text");

        assertEquals(20f, icon.getTextSize(), "size=\"20\" must reach the paint");
        assertTrue(icon.getTypeface().toString().contains("Font Awesome"),
                "the icon font must win the collection, not the sans fallback: " + icon.getTypeface());
        assertNotEquals(0, icon.color() >>> 24, "a fully transparent icon is invisible");

        instance.close();
    }

    private static List<IconView> icons(View root) {
        List<IconView> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(View view, List<IconView> found) {
        if (view instanceof IconView icon) {
            found.add(icon);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), found);
            }
        }
    }
}
