package fr.lacaleche.glue.mcsx.view.debug;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The inspector reported positions as if nothing were scrolled: clicking inside a scrolled
 * container picked whatever sat at that point at scroll 0, and the highlight was drawn there too.
 * Children of a scrolled container are drawn shifted by its scroll offset, and both the hit test
 * and the reported box have to follow.
 *
 * <p>Geometry only — no measure, no padding — so it runs without a live {@code ModernUI}.
 */
class ViewProbeScrollTest {

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

    /** A viewport 100×50 whose content is taller than it is, with one row at the very top. */
    private record Fixture(FrameLayout root, FrameLayout viewport, View row) {
    }

    private static Fixture build() {
        Context context = new HeadlessContext();
        FrameLayout root = new FrameLayout(context);
        FrameLayout viewport = new FrameLayout(context);
        View row = new View(context);
        viewport.addView(row);
        root.addView(viewport);

        root.layout(0, 0, 100, 100);
        viewport.layout(0, 0, 100, 50);
        row.layout(0, 0, 100, 20);
        return new Fixture(root, viewport, row);
    }

    @Test
    void unscrolledTheRowIsHitAtItsOwnPosition() {
        Fixture fixture = build();

        assertSame(fixture.row(), ViewProbe.deepestAt(fixture.root(), 10, 5));
        assertArrayEquals(new int[]{0, 0, 100, 20},
                ViewProbe.boundsIn(fixture.root(), fixture.row()));
    }

    @Test
    void aScrolledRowMovesUpAndStopsBeingHitWhereItUsedToBe() {
        Fixture fixture = build();
        fixture.viewport().setScrollY(30);

        assertArrayEquals(new int[]{0, -30, 100, 20},
                ViewProbe.boundsIn(fixture.root(), fixture.row()),
                "the row is drawn 30px above the viewport once scrolled past");
        // the point still lands inside the viewport — just no longer on the row, which is the bug:
        // before the fix the row was picked here, because its position ignored the scroll
        assertSame(fixture.viewport(), ViewProbe.deepestAt(fixture.root(), 10, 5),
                "a row scrolled out of view must not be picked at its old position");
    }

    @Test
    void aRowScrolledIntoViewIsHitAtItsNewPosition() {
        Fixture fixture = build();
        // the row sits at content y 60..80; scrolling by 50 brings it to viewport y 10..30
        fixture.row().layout(0, 60, 100, 80);
        fixture.viewport().setScrollY(50);

        assertArrayEquals(new int[]{0, 10, 100, 20},
                ViewProbe.boundsIn(fixture.root(), fixture.row()));
        assertSame(fixture.row(), ViewProbe.deepestAt(fixture.root(), 10, 15),
                "the point the row now occupies must pick it");
    }

    @Test
    void theDumpReportsScrolledChildrenWhereTheyAreDrawn() {
        Fixture fixture = build();
        fixture.row().layout(0, 60, 100, 80);
        fixture.viewport().setScrollY(50);

        ViewProbe.Node root = ViewProbe.probe(fixture.root());
        ViewProbe.Node row = root.children().get(0).children().get(0);
        assertEquals(10, row.y(),
                "the dump must agree with the highlight: " + ViewProbe.dump(root));
    }
}
