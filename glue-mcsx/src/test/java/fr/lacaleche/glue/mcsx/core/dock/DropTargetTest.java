package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.Kind;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.LeafHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DropTargetTest {

    private static final DockRect STAGE = new DockRect(0, 0, 1000, 600);

    private static final List<LeafHit> ONE_LEAF =
            List.of(new LeafHit("leaf", null, new DockRect(100, 100, 400, 300)));

    @Test
    void outsideTheStageThereIsNoTarget() {
        assertNull(DropTarget.hitTest(-5, 50, STAGE, false, ONE_LEAF, null));
    }

    @Test
    void anEmptyTreeOffersOnlyTheRootCenter() {
        DropTarget target = DropTarget.hitTest(500, 300, STAGE, true, List.of(), null);
        assertEquals(new DropTarget(Kind.ROOT, null, DropZone.CENTER), target);
    }

    @Test
    void stageEdgeBandsWinOverLeavesBeneathThem() {
        List<LeafHit> fullBleed = List.of(new LeafHit("leaf", null, STAGE));
        assertEquals(new DropTarget(Kind.ROOT, null, DropZone.LEFT),
                DropTarget.hitTest(10, 300, STAGE, false, fullBleed, null));
        assertEquals(new DropTarget(Kind.ROOT, null, DropZone.RIGHT),
                DropTarget.hitTest(990, 300, STAGE, false, fullBleed, null));
        assertEquals(new DropTarget(Kind.ROOT, null, DropZone.TOP),
                DropTarget.hitTest(500, 10, STAGE, false, fullBleed, null));
        assertEquals(new DropTarget(Kind.ROOT, null, DropZone.BOTTOM),
                DropTarget.hitTest(500, 590, STAGE, false, fullBleed, null));
    }

    @Test
    void theCenterWindowIsTheMiddleThirdOnBothAxes() {
        // leaf spans x 100..500, y 100..400; center = fx,fy within (0.34, 0.66)
        assertEquals(DropZone.CENTER,
                DropTarget.hitTest(300, 250, STAGE, false, ONE_LEAF, null).zone());
        assertEquals(DropZone.LEFT,
                DropTarget.hitTest(150, 250, STAGE, false, ONE_LEAF, null).zone());
        assertEquals(DropZone.RIGHT,
                DropTarget.hitTest(460, 250, STAGE, false, ONE_LEAF, null).zone());
        assertEquals(DropZone.TOP,
                DropTarget.hitTest(300, 130, STAGE, false, ONE_LEAF, null).zone());
        assertEquals(DropZone.BOTTOM,
                DropTarget.hitTest(300, 370, STAGE, false, ONE_LEAF, null).zone());
    }

    @Test
    void betweenLeavesThereIsNoTarget() {
        assertNull(DropTarget.hitTest(700, 500, STAGE, false, ONE_LEAF, null));
    }

    @Test
    void floatingLeavesHitBeforeDockedOnesAndTheDraggedWindowIsSkipped() {
        DockRect area = new DockRect(200, 150, 300, 200);
        List<LeafHit> stacked = List.of(
                new LeafHit("floatLeaf", "win1", area),
                new LeafHit("docked", null, area));
        assertEquals("floatLeaf",
                DropTarget.hitTest(350, 250, STAGE, false, stacked, null).leafId());
        assertEquals("docked",
                DropTarget.hitTest(350, 250, STAGE, false, stacked, "win1").leafId());
    }
}
