package fr.lacaleche.glue.web.host;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerPlacementTest {

    @Test
    void coversTheWholeGuiWithoutAnAnchor() {
        assertEquals(new ScreenRectangle(0, 0, 427, 240), LayerPlacement.FULL.bounds(427, 240));
        assertEquals(new ScreenRectangle(0, 0, 1, 1), LayerPlacement.FULL.bounds(0, 0));
    }

    @Test
    void anchorsSizedLayersAndOffsetsThemInward() {
        LayerPlacement corner = new LayerPlacement(WebAnchor.TOP_RIGHT, 100, 80, 0, 0).withOffset(6, 4);
        assertEquals(new ScreenRectangle(321, 4, 100, 80), corner.bounds(427, 240));

        LayerPlacement bottom = new LayerPlacement(WebAnchor.BOTTOM, 182, 40, 0, 0);
        assertEquals(new ScreenRectangle(122, 200, 182, 40), bottom.bounds(427, 240));

        LayerPlacement centered = new LayerPlacement(WebAnchor.CENTER, 100, 100, 0, 0).withOffset(-10, 5);
        assertEquals(new ScreenRectangle(153, 75, 100, 100), centered.bounds(427, 240));
    }

    @Test
    void shrinksLayersThatExceedTheGui() {
        LayerPlacement large = new LayerPlacement(WebAnchor.BOTTOM_LEFT, 800, 600, 0, 0);
        assertEquals(new ScreenRectangle(0, 0, 427, 240), large.bounds(427, 240));
    }
}
