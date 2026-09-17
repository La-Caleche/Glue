package fr.lacaleche.glue.web.bridge;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws native content into a slot the page reserved. It runs on the client thread while the host
 * draws the surface, clipped to the surface; pose changes are restored afterwards.
 */
@FunctionalInterface
public interface WebSlotRenderer {

    void render(GuiGraphics graphics, WebSlot slot);
}
