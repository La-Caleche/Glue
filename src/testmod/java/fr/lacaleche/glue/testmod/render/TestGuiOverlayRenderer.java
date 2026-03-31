package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders a gradient GUI overlay in the top-right corner of the screen.
 * <p>
 * Demonstrates the GUI shader pipeline ({@code TestShaders.GRADIENT_GUI}) by rendering
 * a small colored quad on the HUD using MC's standard pipeline system. Toggleable via keybind.
 * <p>
 * Uses {@link GuiGraphics#fill(com.mojang.blaze3d.pipeline.RenderPipeline, int, int, int, int, int)}
 * with the custom pipeline, which is the correct 1.21.8 approach for GUI rendering.
 */
public class TestGuiOverlayRenderer {

    private static boolean enabled = false;

    /**
     * Registers the HUD render callback.
     */
    public static void register() {
        HudRenderCallback.EVENT.register(TestGuiOverlayRenderer::onHudRender);
    }

    /**
     * Toggles the overlay on/off.
     *
     * @return The new state
     */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    private static void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Render a 48x48 quad in the top-right corner with 8px margin
        int size = 48;
        int margin = 8;
        int x = screenWidth - size - margin;
        int y = margin;

        // Animated color cycling
        float time = (System.currentTimeMillis() % 5000) / 5000f;
        int r = (int) (hsvComponent(time, 5f) * 255);
        int g = (int) (hsvComponent(time, 3f) * 255);
        int b = (int) (hsvComponent(time, 1f) * 255);
        int color = (0xD9 << 24) | (r << 16) | (g << 8) | b; // 85% alpha

        // Use the GRADIENT_GUI pipeline through MC's fill() API
        guiGraphics.fill(TestShaders.GRADIENT_GUI, x, y, x + size, y + size, color);

        // Draw a gradient below it using MC's built-in gradient method for comparison
        int color2 = (0xD9 << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
        guiGraphics.fillGradient(x, y + size + 4, x + size, y + size + 4 + 12, color, color2);
    }

    /**
     * HSV-to-RGB single component. Shift parameter selects R/G/B channel.
     */
    private static float hsvComponent(float hue, float shift) {
        hue = ((hue % 1f) + 1f) % 1f;
        float k = (shift + hue * 6f) % 6f;
        return 1f - Math.max(0, Math.min(Math.min(k, 4f - k), 1f));
    }
}
