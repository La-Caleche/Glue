package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutTest {

    private static final UiStyle STYLE = UiStyle.DEFAULT;

    @Test
    void wideScreenHasSidebarContentAndDescriptionSideBySide() {
        UiLayout layout = UiLayout.of(STYLE, 640, 360, true);

        ScreenRectangle sidebar = layout.sidebar();
        ScreenRectangle description = layout.description();
        assertNotNull(sidebar);
        assertNotNull(description);
        assertEquals(sidebar.right() + STYLE.padding(), layout.content().left());
        assertEquals(layout.content().right() + STYLE.padding(), description.left());
        assertEquals(640 - STYLE.padding(), description.right());
    }

    @Test
    void narrowScreenDropsTheDescriptionPanelInsteadOfSqueezingTheContent() {
        UiLayout layout = UiLayout.of(STYLE, 320, 240, true);

        assertNull(layout.description());
        assertTrue(layout.content().width() > 0);
    }

    @Test
    void contentKeepsAtLeastTheMinimumWidthBesideADescriptionPanel() {
        for (int width = 200; width <= 1000; width++) {
            UiLayout layout = UiLayout.of(STYLE, width, 300, true);
            if (layout.description() != null) assertTrue(layout.content().width() >= STYLE.minContentWidth());
        }
    }

    @Test
    void bodyEndsAboveTheDoneButton() {
        UiLayout layout = UiLayout.of(STYLE, 480, 270, false);

        assertNull(layout.sidebar());
        assertEquals(layout.done().top() - STYLE.padding(), layout.content().bottom());
        assertEquals(270 - STYLE.padding(), layout.done().bottom());
    }
}
