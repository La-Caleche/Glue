package fr.lacaleche.glue.mcsx.view.debug;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import fr.lacaleche.glue.mcsx.view.ComponentRegistry;
import fr.lacaleche.glue.mcsx.view.ViewBinder;
import fr.lacaleche.glue.mcsx.view.ViewInstance;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dumps the icon-and-label row that renders correctly from {@code .mcsx}, so its structure can be
 * compared against the dock's hand-built equivalent. Layout itself needs a live {@code ModernUI},
 * so this asserts the parts that survive headlessly — structure, box model and container config —
 * and prints the tree for eyeballing the rest.
 */
class ProbeComparisonTest {

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

    /** The shape the dock's tab is trying to reproduce: icon, label, close, in a gapped row. */
    private static final String ICON_ROW = """
            <div class="flex-row items-center gap-2 px-2">
                <icon name="layout" size="13"/>
                <text class="text-sm">Viewport</text>
                <icon name="close" size="9"/>
            </div>
            """;

    @Test
    void theWorkingIconRowHasNoMarginsAndAGapInstead() {
        Context context = new HeadlessContext();
        ViewInstance instance = ViewBinder.bind(McsxParser.parseDocument(ICON_ROW),
                new Empty(), context, new ComponentRegistry(), id -> null);

        ViewProbe.Node root = ViewProbe.probe(instance.root());
        String dump = ViewProbe.dump(root);
        System.out.println("=== .mcsx icon row ===\n" + dump);

        ViewProbe.Node row = root.children().get(0);
        assertTrue(row.detail().contains("gap="),
                "the row must space its children with a gap: " + row.detail());
        assertEquals(3, row.children().size(), "icon, label, close");
        // the row itself sits in a FrameLayout and so has margin params; its children must not
        for (ViewProbe.Node child : row.children()) {
            assertFalse(child.layoutParams().contains("margins="),
                    "a flex child carries no margins — the gap does the spacing: "
                            + child.type() + " " + child.layoutParams());
        }
        assertTrue(dump.contains("glyph=U+F0DB"), "the layout icon must resolve its codepoint:\n" + dump);
        assertTrue(dump.contains("glyph=U+F00D"), "the close icon must resolve its codepoint:\n" + dump);

        instance.close();
    }
}
