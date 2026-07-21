package fr.lacaleche.glue.mcsx.core.layout;

import fr.lacaleche.glue.mcsx.core.layout.FlexEngine.Item;
import fr.lacaleche.glue.mcsx.core.layout.FlexEngine.Line;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexEngineTest {

    @Test
    void rigidItemsKeepTheirBasisAndSitEndToEnd() {
        Line line = FlexEngine.solve(
                List.of(Item.fixed(30), Item.fixed(50)), 200, true, 10, Justify.START);
        assertArrayEquals(new int[]{30, 50}, line.sizes());
        assertArrayEquals(new int[]{0, 40}, line.offsets());
        assertEquals(90, line.contentSize());
    }

    @Test
    void unboundedContainerNeitherGrowsNorShrinks() {
        Line line = FlexEngine.solve(
                List.of(Item.flexible(30, 1f, 1f), Item.flexible(50, 1f, 1f)), 999, false, 0, Justify.START);
        assertArrayEquals(new int[]{30, 50}, line.sizes());
        assertEquals(80, line.contentSize());
    }

    @Test
    void growSharesFreeSpaceByFactor() {
        Line line = FlexEngine.solve(
                List.of(Item.flexible(0, 1f, 0f), Item.flexible(0, 3f, 0f)), 100, true, 0, Justify.START);
        assertArrayEquals(new int[]{25, 75}, line.sizes());
        assertArrayEquals(new int[]{0, 25}, line.offsets());
    }

    @Test
    void growFillsExactlyEvenWhenTheSplitDoesNotDivideEvenly() {
        Line line = FlexEngine.solve(
                List.of(Item.flexible(0, 1f, 0f), Item.flexible(0, 1f, 0f), Item.flexible(0, 1f, 0f)),
                100, true, 0, Justify.START);
        assertEquals(100, line.sizes()[0] + line.sizes()[1] + line.sizes()[2],
                "rounding must not lose or invent pixels");
    }

    @Test
    void gapIsSubtractedFromTheSpaceAvailableToGrow() {
        Line line = FlexEngine.solve(
                List.of(Item.flexible(0, 1f, 0f), Item.flexible(0, 1f, 0f)), 100, true, 20, Justify.START);
        assertArrayEquals(new int[]{40, 40}, line.sizes());
        assertArrayEquals(new int[]{0, 60}, line.offsets());
    }

    @Test
    void shrinkIsWeightedByBasis() {
        // Overflow of 60 across bases 100 and 200 at equal shrink: the wide item gives up twice as much.
        Line line = FlexEngine.solve(
                List.of(Item.flexible(100, 0f, 1f), Item.flexible(200, 0f, 1f)), 240, true, 0, Justify.START);
        assertArrayEquals(new int[]{80, 160}, line.sizes());
    }

    @Test
    void anItemThatHitsItsMaxFreezesAndTheRestAbsorbTheRemainder() {
        Line line = FlexEngine.solve(
                List.of(new Item(0, 1f, 0f, 0, 20), Item.flexible(0, 1f, 0f)), 100, true, 0, Justify.START);
        assertArrayEquals(new int[]{20, 80}, line.sizes(),
                "the capped item must not strand the 30px it could not take");
    }

    @Test
    void anItemThatHitsItsMinFreezesWhileShrinking() {
        Line line = FlexEngine.solve(
                List.of(new Item(100, 0f, 1f, 90, Integer.MAX_VALUE), Item.flexible(100, 0f, 1f)),
                150, true, 0, Justify.START);
        assertArrayEquals(new int[]{90, 60}, line.sizes());
    }

    @Test
    void justifyPlacesTheLineWithinLeftoverSpace() {
        List<Item> two = List.of(Item.fixed(20), Item.fixed(20));
        assertArrayEquals(new int[]{0, 20}, FlexEngine.solve(two, 100, true, 0, Justify.START).offsets());
        assertArrayEquals(new int[]{30, 50}, FlexEngine.solve(two, 100, true, 0, Justify.CENTER).offsets());
        assertArrayEquals(new int[]{60, 80}, FlexEngine.solve(two, 100, true, 0, Justify.END).offsets());
        assertArrayEquals(new int[]{0, 80}, FlexEngine.solve(two, 100, true, 0, Justify.BETWEEN).offsets());
    }

    @Test
    void aroundAndEvenlyDistributeEdgeSpaceDifferently() {
        List<Item> two = List.of(Item.fixed(20), Item.fixed(20));
        // around: 60 free / 2 items = 30 each, half of it (15) on the leading edge.
        assertArrayEquals(new int[]{15, 65}, FlexEngine.solve(two, 100, true, 0, Justify.AROUND).offsets());
        // evenly: 60 free / 3 gutters = 20 each, a full gutter before the first item.
        assertArrayEquals(new int[]{20, 60}, FlexEngine.solve(two, 100, true, 0, Justify.EVENLY).offsets());
    }

    @Test
    void justifyIsInertOnceGrowHasEatenTheFreeSpace() {
        Line line = FlexEngine.solve(
                List.of(Item.flexible(0, 1f, 0f), Item.fixed(20)), 100, true, 0, Justify.BETWEEN);
        assertArrayEquals(new int[]{80, 20}, line.sizes());
        assertArrayEquals(new int[]{0, 80}, line.offsets());
    }

    @Test
    void aSingleItemUnderBetweenSitsAtTheStart() {
        Line line = FlexEngine.solve(List.of(Item.fixed(20)), 100, true, 0, Justify.BETWEEN);
        assertArrayEquals(new int[]{0}, line.offsets());
    }

    @Test
    void emptyLineIsWellDefined() {
        Line line = FlexEngine.solve(List.of(), 100, true, 8, Justify.EVENLY);
        assertEquals(0, line.contentSize());
        assertEquals(0, line.sizes().length);
    }

    @Test
    void overflowWithNoShrinkIsLeftOverflowing() {
        Line line = FlexEngine.solve(
                List.of(Item.fixed(80), Item.fixed(80)), 100, true, 0, Justify.START);
        assertArrayEquals(new int[]{80, 80}, line.sizes());
        assertEquals(160, line.contentSize());
        assertArrayEquals(new int[]{0, 80}, line.offsets(), "no free space to justify into");
    }

    @Test
    void breakLinesFillsEachLineBeforeStartingTheNext() {
        List<Item> items = List.of(Item.fixed(60), Item.fixed(60), Item.fixed(60));
        List<int[]> lines = FlexEngine.breakLines(items, 140, 10);
        assertEquals(2, lines.size());
        assertArrayEquals(new int[]{0, 1}, lines.get(0), "60 + 10 + 60 = 130 fits in 140");
        assertArrayEquals(new int[]{2}, lines.get(1));
    }

    /** The gap counts against the line: two 60s fit in 130 only if the 10px gap is ignored. */
    @Test
    void breakLinesCountsTheGapBetweenItems() {
        List<int[]> lines = FlexEngine.breakLines(List.of(Item.fixed(60), Item.fixed(60)), 125, 10);
        assertEquals(2, lines.size());
    }

    /** An oversized item takes a line of its own rather than leaving an empty line ahead of it. */
    @Test
    void breakLinesGivesAnOverlongItemItsOwnLine() {
        List<int[]> lines = FlexEngine.breakLines(List.of(Item.fixed(20), Item.fixed(500)), 100, 0);
        assertEquals(2, lines.size());
        assertArrayEquals(new int[]{0}, lines.get(0));
        assertArrayEquals(new int[]{1}, lines.get(1));
    }

    /** Breaking happens on the clamped basis, before flexing: a shrinkable row still wraps. */
    @Test
    void breakLinesIgnoresGrowAndShrink() {
        List<Item> items = List.of(Item.flexible(80, 1f, 1f), Item.flexible(80, 1f, 1f));
        assertEquals(2, FlexEngine.breakLines(items, 100, 0).size());
    }

    @Test
    void breakLinesRespectsTheMinimumClamp() {
        List<Item> items = List.of(new Item(10, 0f, 0f, 90, 200), new Item(10, 0f, 0f, 90, 200));
        assertEquals(2, FlexEngine.breakLines(items, 100, 0).size(), "min-width forces the break");
    }

    /** A container sizing to its content offers no width to break against, so nothing wraps. */
    @Test
    void breakLinesKeepsOneLineWhenNoWidthIsAvailable() {
        List<Item> items = List.of(Item.fixed(60), Item.fixed(60));
        assertArrayEquals(new int[]{0, 1}, FlexEngine.breakLines(items, 0, 0).get(0));
        assertEquals(1, FlexEngine.breakLines(items, 0, 0).size());
    }

    @Test
    void breakLinesHandlesNoItems() {
        List<int[]> lines = FlexEngine.breakLines(List.of(), 100, 0);
        assertEquals(1, lines.size());
        assertEquals(0, lines.get(0).length);
    }

    /** Each wrapped line then flexes independently: one item alone on line 2 grows to fill it. */
    @Test
    void eachBrokenLineSolvesAgainstTheFullContainerWidth() {
        List<Item> items = List.of(Item.flexible(60, 1f, 0f), Item.flexible(60, 1f, 0f));
        List<int[]> lines = FlexEngine.breakLines(items, 100, 0);
        assertEquals(2, lines.size());
        Line second = FlexEngine.solve(List.of(items.get(1)), 100, true, 0, Justify.START);
        assertArrayEquals(new int[]{100}, second.sizes());
    }

    @Test
    void rejectsIncoherentItems() {
        assertThrows(IllegalArgumentException.class, () -> new Item(-1, 0f, 0f, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Item(10, -1f, 0f, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Item(10, 0f, 0f, 20, 10));
    }

    /** The indices overload must solve exactly like a materialised sublist of the same items. */
    @Test
    void indicesOverloadMatchesSublistSolve() {
        List<Item> all = List.of(
                Item.fixed(30),
                Item.flexible(20, 1f, 1f),
                new Item(40, 2f, 0f, 10, 80),
                Item.flexible(60, 0f, 2f));
        List<int[]> lines = FlexEngine.breakLines(all, 90, 4);
        for (int[] line : lines) {
            List<Item> sublist = new java.util.ArrayList<>();
            for (int index : line) {
                sublist.add(all.get(index));
            }
            Line viaSublist = FlexEngine.solve(sublist, 90, true, 4, Justify.BETWEEN);
            Line viaIndices = FlexEngine.solve(all, line, 90, true, 4, Justify.BETWEEN);
            assertArrayEquals(viaSublist.sizes(), viaIndices.sizes());
            assertArrayEquals(viaSublist.offsets(), viaIndices.offsets());
            assertEquals(viaSublist.contentSize(), viaIndices.contentSize());
        }
    }
}
