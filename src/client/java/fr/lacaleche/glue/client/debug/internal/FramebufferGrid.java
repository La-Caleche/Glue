package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.ui.UiStyle;
import fr.lacaleche.glue.client.ui.UiTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid of buffers the framebuffer viewer shows, laid out again each frame from its settings. The
 * wheel over it turns pages; it never takes focus or clicks.
 */
final class FramebufferGrid extends AbstractWidget {

    private static final int GAP = 4;

    private final Framebuffers framebuffers;
    private final List<UiTextureView> views = new ArrayList<>();
    private final List<Framebuffers.Buffer> shown = new ArrayList<>();

    FramebufferGrid(Framebuffers framebuffers) {
        super(0, 0, 0, 0, Component.empty());
        this.framebuffers = framebuffers;
        int cells = Framebuffers.MAX_GRID_SIZE * Framebuffers.MAX_GRID_SIZE;
        for (int i = 0; i < cells; i++) {
            int cell = i;
            this.views.add(new UiTextureView(Component.empty(), () -> this.textureAt(cell)));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.shown.clear();
        this.shown.addAll(this.framebuffers.onGrid());
        if (this.shown.isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("glue.developer_menu.framebuffers.empty"), this.getX(), this.getY(),
                    UiStyle.DEFAULT.muted());
            return;
        }

        int size = this.framebuffers.gridSize();
        int cellWidth = (this.getWidth() - (size - 1) * GAP) / size;
        int cellHeight = (this.getHeight() - (size - 1) * GAP) / size;
        if (cellWidth <= 0 || cellHeight <= 0) return;

        for (int i = 0; i < this.shown.size(); i++) {
            UiTextureView view = this.views.get(i);
            view.setMessage(Component.literal(this.shown.get(i).name()));
            view.setRectangle(cellWidth, cellHeight, this.getX() + i % size * (cellWidth + GAP),
                    this.getY() + i / size * (cellHeight + GAP));
            view.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0.0) return false;

        this.framebuffers.turnPage(vertical > 0.0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /** Drops the texture locations of the grid's views. */
    void release() {
        this.views.forEach(UiTextureView::release);
    }

    private UiTextureView.@Nullable Texture textureAt(int cell) {
        return cell < this.shown.size() ? this.shown.get(cell).texture().get() : null;
    }
}
