package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.ui.UiButton;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiStyle;
import fr.lacaleche.glue.client.ui.UiToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The developer menu's Framebuffers page: the viewer's settings and a toggle per buffer on the left,
 * the grid of buffers on the right. Showing the page takes the grid off the HUD.
 */
public final class FramebuffersPage implements UiPage {

    private static final int ROWS_WIDTH = 180;

    private @Nullable FramebufferGrid grid;

    @Override
    public Component title() {
        return text("title");
    }

    @Override
    public void build(UiPageBuilder builder) {
        Framebuffers framebuffers = Framebuffers.INSTANCE;
        framebuffers.setPageShown(true);
        framebuffers.setOnHud(false);

        ScreenRectangle area = builder.area();
        int padding = UiStyle.DEFAULT.padding();
        int rowsWidth = Math.min(ROWS_WIDTH, area.width() / 2);
        UiRowList rows = builder.rows(new ScreenRectangle(area.left(), area.top(), rowsWidth, area.height()));
        builder.section(text("view"));
        builder.cycle(text("filter"), text("filter.description"), List.of(Framebuffers.Filter.values()),
                Framebuffers.Filter::title, framebuffers::filter, framebuffers::setFilter);
        if (framebuffers.hasAlternates()) {
            builder.toggle(text("alternates"), text("alternates.description"), framebuffers::showsAlternates,
                    framebuffers::setShowsAlternates);
        }
        builder.slider(text("grid"), text("grid.description"), 1, Framebuffers.MAX_GRID_SIZE, 1,
                framebuffers::gridSize, size -> framebuffers.setGridSize((int) Math.round(size)));
        builder.label(text("page"), text("page.description"),
                () -> Component.literal((framebuffers.gridPage() + 1) + " / " + framebuffers.pageCount()));
        rows.wide(new UiButton(text("previous"), () -> framebuffers.turnPage(-1)));
        rows.wide(new UiButton(text("next"), () -> framebuffers.turnPage(1)));
        rows.wide(new UiButton(text("show_on_hud"), () -> {
            framebuffers.setOnHud(true);
            Minecraft.getInstance().setScreen(null);
        }));

        builder.section(text("buffers"));
        for (Framebuffers.Buffer buffer : framebuffers.buffers()) {
            Component name = Component.literal(buffer.name());
            rows.row(name, null, new UiToggle(name, () -> framebuffers.isShown(buffer.name()),
                    shown -> framebuffers.setShown(buffer.name(), shown)));
        }

        this.grid = builder.add(new FramebufferGrid(framebuffers));
        int gridLeft = area.left() + rowsWidth + padding;
        this.grid.setRectangle(area.right() - padding - gridLeft, area.height() - 2 * padding, gridLeft,
                area.top() + padding);
    }

    @Override
    public void close() {
        if (this.grid != null) this.grid.release();
        this.grid = null;
        Framebuffers.INSTANCE.setPageShown(false);
    }

    private static Component text(String key) {
        return Component.translatable("glue.developer_menu.framebuffers." + key);
    }
}
