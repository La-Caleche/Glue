package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The kit's one look: ARGB colours and metrics in GUI pixels. Widgets and {@link UiScreen} read
 * {@link #DEFAULT}; nothing swaps it at runtime.
 *
 * @param backdrop the dimming drawn over the world or panorama behind a {@link UiScreen}
 * @param panel the sidebar, content and description panels
 * @param control a control's background
 * @param hover a control's background under the pointer or focus, and a hovered row
 * @param focus the one-pixel outline of the focused control
 * @param text labels and values
 * @param muted section headers, descriptions and secondary text
 * @param accent a switched-on value, a slider's fill and the selected page
 * @param disabled the text of an inactive control
 * @param rowHeight the height of a row; its control is four pixels shorter
 * @param padding the gap between panels and around a row's content
 * @param controlWidth the width of the control on the right of a row
 * @param sidebarWidth the width of the page sidebar
 * @param descriptionWidth the width of the description panel
 * @param minContentWidth the narrowest content area beside a description panel; below it, descriptions
 *        become tooltips
 */
public record UiStyle(
        int backdrop,
        int panel,
        int control,
        int hover,
        int focus,
        int text,
        int muted,
        int accent,
        int disabled,
        int rowHeight,
        int padding,
        int controlWidth,
        int sidebarWidth,
        int descriptionWidth,
        int minContentWidth) {

    public static final UiStyle DEFAULT = new UiStyle(
            0x90000000,
            0xA0101010,
            0xA0303030,
            0xC0505050,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFA0A0A0,
            0xFF7FB2F0,
            0xFF707070,
            24,
            6,
            110,
            120,
            150,
            200);

    public int controlHeight() {
        return this.rowHeight - 4;
    }

    /** Draws a control's background, lighter when hovered or focused, and its focus outline. */
    public void drawControl(GuiGraphics graphics, AbstractWidget widget) {
        int x = widget.getX();
        int y = widget.getY();
        boolean lit = widget.active && widget.isHoveredOrFocused();
        graphics.fill(x, y, widget.getRight(), widget.getBottom(), lit ? this.hover : this.control);
        if (widget.isFocused()) graphics.renderOutline(x, y, widget.getWidth(), widget.getHeight(), this.focus);
    }
}
