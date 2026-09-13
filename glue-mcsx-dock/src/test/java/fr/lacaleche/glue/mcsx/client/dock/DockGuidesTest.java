package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGuides;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DropTarget;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockGuidesTest {

    private static final DockRect STAGE = new DockRect(0, 0, 600, 500);
    private static final DockRect LEAF = new DockRect(100, 100, 300, 300);
    private static final DockTabs TABS = DockLayouts.tabs("one");

    @Test
    void leafGuidesClusterAroundTheLeafCenterWithTheAdvertisedOperations() {
        List<DockGuides.Guide> guides = DockGuides.leafGuides(TABS, LEAF, true, true);

        assertEquals(5, guides.size());
        assertEquals(rectAround(250, 250), rectOf(guides, DropTarget.Zone.CENTER));
        assertEquals(rectAround(250 - DockGuides.GUIDE_GAP, 250), rectOf(guides, DropTarget.Zone.LEFT));
        assertEquals(rectAround(250 + DockGuides.GUIDE_GAP, 250), rectOf(guides, DropTarget.Zone.RIGHT));
        assertEquals(rectAround(250, 250 - DockGuides.GUIDE_GAP), rectOf(guides, DropTarget.Zone.TOP));
        assertEquals(rectAround(250, 250 + DockGuides.GUIDE_GAP), rectOf(guides, DropTarget.Zone.BOTTOM));
        for (DockGuides.Guide guide : guides) {
            assertEquals(DropTarget.Kind.TABS, guide.target().kind());
            assertEquals(TABS, guide.target().tabs());
        }
    }

    @Test
    void everyPaintedGuideRectangleIsExactlyItsAcceptedHitRegion() {
        List<DockGuides.Guide> guides = DockGuides.combine(
                DockGuides.leafGuides(TABS, LEAF, true, true),
                DockGuides.rootGuides(STAGE, false)
        );

        for (DockGuides.Guide guide : guides) {
            DockRect rect = guide.rect();
            assertEquals(guide.target(), hitTarget(guides, rect.x() + rect.width() / 2,
                    rect.y() + rect.height() / 2), "guide center resolves its own operation");
            assertEquals(guide.target(), hitTarget(guides, rect.x(), rect.y()),
                    "guide top-left corner is inside its region");
            assertEquals(guide.target(), hitTarget(guides, rect.right() - 1, rect.bottom() - 1),
                    "guide bottom-right interior is inside its region");
        }
        // Just past a control's edge is not that control any more.
        DockRect left = rectOf(guides, DropTarget.Zone.LEFT);
        assertNull(DockGuides.hit(List.of(new DockGuides.Guide(
                new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.LEFT), left)),
                left.x() - 1, left.y()));
    }

    @Test
    void fractionalPointerCoordinatesRespectExactGuideEdges() {
        DropTarget target = new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.LEFT);
        List<DockGuides.Guide> guides = List.of(new DockGuides.Guide(target, new DockRect(10, 10, 30, 30)));

        assertNull(DockGuides.hit(guides, 9.999, 20.0));
        assertEquals(target, DockGuides.hit(guides, 10.0, 20.0).target());
        assertEquals(target, DockGuides.hit(guides, 39.999, 20.0).target());
        assertNull(DockGuides.hit(guides, 40.0, 20.0));
    }

    @Test
    void rootGuidesSitInsetOnTheStageEdges() {
        List<DockGuides.Guide> guides = DockGuides.rootGuides(STAGE, false);

        assertEquals(4, guides.size());
        int edgeCenter = DockGuides.ROOT_GUIDE_INSET + DockGuides.GUIDE_SIZE / 2;
        assertEquals(rectAround(edgeCenter, 250), rectOf(guides, DropTarget.Zone.LEFT));
        assertEquals(rectAround(600 - edgeCenter, 250), rectOf(guides, DropTarget.Zone.RIGHT));
        assertEquals(rectAround(300, edgeCenter), rectOf(guides, DropTarget.Zone.TOP));
        assertEquals(rectAround(300, 500 - edgeCenter), rectOf(guides, DropTarget.Zone.BOTTOM));
        for (DockGuides.Guide guide : guides) {
            assertEquals(DropTarget.Kind.ROOT, guide.target().kind());
        }
    }

    @Test
    void narrowStagesNeverEmitOverlappingControls() {
        List<DockGuides.Guide> guides = DockGuides.rootGuides(new DockRect(0, 0, 50, 50), false);

        for (int first = 0; first < guides.size(); first++) {
            for (int second = first + 1; second < guides.size(); second++) {
                assertTrue(!guides.get(first).rect().intersects(guides.get(second).rect()));
            }
        }
    }

    @Test
    void anEmptyTreeOffersOneCenterControlThatDocksTheTree() {
        List<DockGuides.Guide> guides = DockGuides.rootGuides(STAGE, true);

        assertEquals(1, guides.size());
        assertEquals(new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.CENTER),
                guides.getFirst().target());
        assertEquals(rectAround(300, 250), guides.getFirst().rect());
    }

    @Test
    void smallPanesOfferOnlyTheControlsThatFit() {
        // Wide enough for the horizontal cluster only: the vertical controls do not fit.
        List<DockGuides.Guide> flat = DockGuides.leafGuides(
                TABS, new DockRect(0, 0, 300, 40), true, true);
        assertEquals(List.of(DropTarget.Zone.CENTER, DropTarget.Zone.LEFT, DropTarget.Zone.RIGHT),
                flat.stream().map(guide -> guide.target().zone()).toList());

        // Too small for any control: no guides at all rather than overflowing ones.
        assertTrue(DockGuides.leafGuides(TABS, new DockRect(0, 0, 20, 20), true, true).isEmpty());
    }

    @Test
    void incompatiblePayloadsAreNeverOfferedACenterControl() {
        List<DockGuides.Guide> guides = DockGuides.leafGuides(TABS, LEAF, false, true);

        assertEquals(4, guides.size());
        assertTrue(guides.stream().noneMatch(guide -> guide.target().zone() == DropTarget.Zone.CENTER));
    }

    @Test
    void selfDropsThatCannotLeaveADestinationOfferOnlyTheCenter() {
        List<DockGuides.Guide> guides = DockGuides.leafGuides(TABS, LEAF, true, false);

        assertEquals(List.of(DropTarget.Zone.CENTER),
                guides.stream().map(guide -> guide.target().zone()).toList());
    }

    @Test
    void topmostEligibleLeafWinsAndTheDraggedWindowIsIgnored() {
        DockTabs lower = DockLayouts.tabs("lower");
        DockTabs upper = DockLayouts.tabs("upper");
        DockWindow upperWindow = DockLayouts.window(upper, 100, 100, 300, 300);
        List<DropTarget.TabsHit> hits = List.of(
                new DropTarget.TabsHit(upper, upperWindow, LEAF),
                new DropTarget.TabsHit(lower, null, LEAF)
        );

        assertEquals(upper, DockGuides.topHit(hits, 250, 250, STAGE, null).tabs());
        assertEquals(lower, DockGuides.topHit(hits, 250, 250, STAGE, upperWindow).tabs());
    }

    @Test
    void offStageFloatingLeavesAreNeverTargets() {
        DockTabs floating = DockLayouts.tabs("floating");
        DockWindow window = DockLayouts.window(floating, 650, 100, 200, 200);
        DropTarget.TabsHit hit = new DropTarget.TabsHit(floating, window, new DockRect(650, 100, 200, 200));

        assertNull(DockGuides.topHit(List.of(hit), 700, 150, STAGE, null));
    }

    @Test
    void insertionIndexFollowsTabMidpointsAndTheCaretMarksTheSlot() {
        List<DockRect> tabs = List.of(
                new DockRect(100, 0, 60, 20),
                new DockRect(160, 0, 40, 20),
                new DockRect(200, 0, 80, 20)
        );

        assertEquals(0, DockGuides.insertionIndex(tabs, 105));
        assertEquals(1, DockGuides.insertionIndex(tabs, 130));
        assertEquals(2, DockGuides.insertionIndex(tabs, 185));
        assertEquals(3, DockGuides.insertionIndex(tabs, 250));
        assertEquals(100, DockGuides.insertionX(tabs, 0, 100));
        assertEquals(160, DockGuides.insertionX(tabs, 1, 100));
        assertEquals(280, DockGuides.insertionX(tabs, 3, 100));
        assertEquals(100, DockGuides.insertionX(List.of(), 0, 100));
    }

    @Test
    void previewRectanglesShowTheAreaTheOperationWouldGrant() {
        int splitterSize = 8;
        DockTabs added = DockLayouts.tabs("added");
        assertEquals(LEAF, DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.CENTER),
                LEAF, STAGE, splitterSize, TABS, added));
        assertEquals(new DockRect(100, 100, 99, 300), DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.LEFT),
                LEAF, STAGE, splitterSize, TABS, added));
        assertEquals(new DockRect(301, 100, 99, 300), DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.RIGHT),
                LEAF, STAGE, splitterSize, TABS, added));
        assertEquals(new DockRect(0, 0, 600, 167), DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.TOP),
                null, STAGE, splitterSize, TABS, added));
        assertEquals(new DockRect(0, 333, 600, 167), DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.BOTTOM),
                null, STAGE, splitterSize, TABS, added));
        assertThrows(IllegalArgumentException.class, () -> DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.LEFT),
                null, STAGE, -1, TABS, added));
    }

    @Test
    void previewIncludesEverySplitterIntroducedBySameAxisFlattening() {
        DockSplit existing = DockLayouts.row(DockLayouts.tabs("one"), DockLayouts.tabs("two"));
        DockTabs added = DockLayouts.tabs("added");

        assertEquals(new DockRect(401, 0, 199, 500), DockGuides.previewRect(
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.RIGHT),
                null, STAGE, 8, existing, added));
    }

    @Test
    void dropTargetsValidateTheirInsertionIndex() {
        assertEquals(2, new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.CENTER, 2).index());
        assertEquals(DropTarget.APPEND, new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.CENTER).index());
        assertThrows(IllegalArgumentException.class,
                () -> new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.CENTER, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DropTarget(DropTarget.Kind.TABS, TABS, DropTarget.Zone.LEFT, 1));
    }

    private static DockRect rectAround(int centerX, int centerY) {
        int half = DockGuides.GUIDE_SIZE / 2;
        return new DockRect(centerX - half, centerY - half, DockGuides.GUIDE_SIZE, DockGuides.GUIDE_SIZE);
    }

    private static DockRect rectOf(List<DockGuides.Guide> guides, DropTarget.Zone zone) {
        return guides.stream()
                .filter(guide -> guide.target().zone() == zone)
                .findFirst()
                .orElseThrow()
                .rect();
    }

    private static DropTarget hitTarget(List<DockGuides.Guide> guides, double x, double y) {
        DockGuides.Guide guide = DockGuides.hit(guides, x, y);
        return guide == null ? null : guide.target();
    }

}
