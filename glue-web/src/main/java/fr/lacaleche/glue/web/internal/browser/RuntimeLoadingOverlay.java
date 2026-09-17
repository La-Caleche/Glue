package fr.lacaleche.glue.web.internal.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Noninteractive GUI-layer status, independent of browser surfaces and the gameplay HUD. */
public final class RuntimeLoadingOverlay {

    private RuntimeLoadingOverlay() {
    }

    public static void render(Minecraft client, GuiRenderState renderState) {
        RuntimeStartup.Snapshot progress = CefRuntime.progress();
        long now = System.nanoTime();
        float opacity = progress.opacity(now);
        if (opacity <= 0.03f) return;

        GuiGraphics graphics = new GuiGraphics(client, renderState);
        boolean textReady = client.isGameLoadFinished();
        int width = Math.min(180, graphics.guiWidth() - 16);
        if (width < 32) return;
        int height = textReady ? 36 : 6;
        int x = graphics.guiWidth() - width - 8;
        int y = Math.max(0, graphics.guiHeight() - height - 8);
        int accent = switch (progress.stage()) {
            case READY -> 0xFF6DCAA5;
            case FAILED -> 0xFFE39C86;
            default -> 0xFF8AB7ED;
        };

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().identity();
            graphics.fill(x, y, x + width, y + height, fade(0xDE151C28, opacity));
            if (textReady) drawText(graphics, client.font, progress, x, y, width, opacity);

            int left = x + 4;
            int right = x + width - 4;
            int bottom = y + height - 3;
            graphics.fill(left, bottom - 2, right, bottom, fade(0xFF344051, opacity));
            if (progress.stage() == RuntimeStartup.Stage.FAILED || progress.percent() >= 0) {
                int percent = progress.stage() == RuntimeStartup.Stage.FAILED ? 100 : progress.percent();
                graphics.fill(left, bottom - 2, left + (right - left) * percent / 100, bottom, fade(accent, opacity));
            } else {
                int segment = Math.min(36, (right - left) / 3);
                int travel = right - left - segment;
                int step = (int) (Math.floorMod(now / 12_000_000L, (long) travel * 2));
                int offset = step <= travel ? step : travel * 2 - step;
                graphics.fill(left + offset, bottom - 2, left + offset + segment, bottom, fade(accent, opacity));
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, RuntimeStartup.Snapshot progress,
                                 int x, int y, int width, float opacity) {
        String percentage = progress.stage().isActive() && progress.percent() >= 0 ? progress.percent() + "%" : "";
        int percentageWidth = font.width(percentage);
        String title = Component.translatable("gui.glue-web.startup.title").getString();
        int titleWidth = width - 16 - (percentage.isEmpty() ? 0 : percentageWidth + 8);
        graphics.drawString(font, font.plainSubstrByWidth(title, Math.max(0, titleWidth)),
                x + 8, y + 6, fade(0xFFEAF0F8, opacity), false);
        if (!percentage.isEmpty()) {
            graphics.drawString(font, percentage, x + width - 8 - percentageWidth, y + 6,
                    fade(0xFFB8C9DD, opacity), false);
        }
        String key = "gui.glue-web.startup." + progress.stage().name().toLowerCase(Locale.ROOT);
        String label = Component.translatable(key).getString();
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 16), x + 8, y + 20,
                fade(0xFFB8C9DD, opacity), false);
    }

    private static int fade(int color, float opacity) {
        return ((int) ((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }
}
