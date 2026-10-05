package fr.lacaleche.glue.client.ui;

import fr.lacaleche.glue.client.ui.internal.BorrowedSceneTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shows a GL texture another owner keeps alive, fitted to the view with its aspect kept, above its
 * caption. The texture is read from its supplier each frame and registered under one location of the
 * view's own, re-registered only when its id or size changes; {@link UiScreen} releases that location
 * when the view's page is hidden. Its storage is never freed by the view.
 */
public final class UiTextureView extends AbstractWidget {

    private static int nextLocation;

    private final Supplier<@Nullable Texture> source;
    private final BorrowedSceneTexture texture;

    public UiTextureView(Component caption, Supplier<@Nullable Texture> source) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlWidth(), caption);
        this.source = Objects.requireNonNull(source, "source");
        nextLocation++;
        this.texture = new BorrowedSceneTexture(
                ResourceLocation.fromNamespaceAndPath("glue", "ui/texture_view_" + nextLocation));
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle style = UiStyle.DEFAULT;
        Font font = Minecraft.getInstance().font;
        int imageBottom = this.getBottom() - font.lineHeight - 2;
        graphics.fill(this.getX(), this.getY(), this.getRight(), imageBottom, style.control());
        renderScrollingString(graphics, font, this.getMessage(), this.getX(), imageBottom + 2, this.getRight(),
                this.getBottom() + 1, style.muted());

        Texture shown = this.source.get();
        if (shown == null || shown.id() <= 0 || shown.width() <= 0 || shown.height() <= 0) return;

        this.texture.update(Minecraft.getInstance(), shown.id(), shown.width(), shown.height());
        int areaWidth = this.getWidth();
        int areaHeight = imageBottom - this.getY();
        float scale = Math.min((float) areaWidth / shown.width(), (float) areaHeight / shown.height());
        int width = Math.max(1, Math.round(shown.width() * scale));
        int height = Math.max(1, Math.round(shown.height() * scale));
        int x = this.getX() + (areaWidth - width) / 2;
        int y = this.getY() + (areaHeight - height) / 2;
        float top = shown.bottomUp() ? 1.0F : 0.0F;
        graphics.blit(this.texture.location(), x, y, x + width, y + height, 0.0F, 1.0F, top, 1.0F - top);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /** Drops this view's texture location; the view registers it again if it is drawn later. */
    public void release() {
        this.texture.release(Minecraft.getInstance());
    }

    /**
     * A borrowed texture.
     *
     * @param id the GL texture name
     * @param bottomUp whether row zero is the bottom row, as in a GL render target, so the view flips it
     */
    public record Texture(int id, int width, int height, boolean bottomUp) {
    }
}
