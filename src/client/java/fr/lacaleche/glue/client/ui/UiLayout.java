package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

/**
 * Where a {@link UiScreen}'s parts go: a title bar on top, the Done button bottom right, and between
 * them the sidebar, the content and the description panel. The description panel is dropped when it
 * would leave the content narrower than the style's minimum.
 */
record UiLayout(
        @Nullable ScreenRectangle sidebar,
        ScreenRectangle content,
        @Nullable ScreenRectangle description,
        int titleY,
        ScreenRectangle done) {

    static UiLayout of(UiStyle style, int width, int height, boolean sidebar) {
        int padding = style.padding();
        int top = style.rowHeight();
        ScreenRectangle done = new ScreenRectangle(width - padding - style.controlWidth(),
                height - padding - style.controlHeight(), style.controlWidth(), style.controlHeight());
        int bodyHeight = Math.max(0, done.top() - padding - top);

        int left = padding;
        ScreenRectangle sidebarArea = null;
        if (sidebar) {
            sidebarArea = new ScreenRectangle(left, top, style.sidebarWidth(), bodyHeight);
            left += style.sidebarWidth() + padding;
        }

        int right = width - padding;
        ScreenRectangle descriptionArea = null;
        if (right - left - style.descriptionWidth() - padding >= style.minContentWidth()) {
            descriptionArea = new ScreenRectangle(right - style.descriptionWidth(), top, style.descriptionWidth(),
                    bodyHeight);
            right -= style.descriptionWidth() + padding;
        }

        ScreenRectangle content = new ScreenRectangle(left, top, Math.max(0, right - left), bodyHeight);
        return new UiLayout(sidebarArea, content, descriptionArea, (top - 8) / 2, done);
    }
}
