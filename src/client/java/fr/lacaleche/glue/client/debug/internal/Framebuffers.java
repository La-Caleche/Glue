package fr.lacaleche.glue.client.debug.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.render.gl.SavedGlState;
import fr.lacaleche.glue.client.ui.UiStyle;
import fr.lacaleche.glue.client.ui.UiTextureView;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The framebuffer viewer behind the developer menu's Framebuffers page: the buffers it can show, the
 * settings that pick which, and the copies of the main target it takes at the end of the world pass.
 * It captures only while the page or its HUD view is shown, and frees its copies when neither is.
 * Settings last until the game closes. Render thread only.
 */
public final class Framebuffers {

    public static final Framebuffers INSTANCE = new Framebuffers();

    static final int MAX_GRID_SIZE = 4;
    private static final Component HUD_HINT = Component.translatable("glue.developer_menu.framebuffers.hud_hint");
    private static final List<Extra> EXTRAS = new ArrayList<>();

    private final DepthPreview depthPreview = new DepthPreview();
    private final Set<String> hidden = new HashSet<>();
    private List<Buffer> buffers = List.of();
    private boolean iris;
    private @Nullable RenderTarget depthCopy;
    private int colorCopy = -1;
    private int colorCopyWidth;
    private int colorCopyHeight;
    private Filter filter = Filter.ALL;
    private boolean alternates;
    private int gridSize = 2;
    private int gridPage;
    private boolean pageShown;
    private boolean onHud;
    private @Nullable FramebufferGrid hudGrid;

    private Framebuffers() {
    }

    /** Adds a texture another owner keeps alive, listed after the main target's buffers. */
    public static void registerTexture(String name, IntSupplier id) {
        EXTRAS.add(new Extra(Objects.requireNonNull(name, "name"), Objects.requireNonNull(id, "id")));
    }

    /** Hooks the captures to the end of the world pass and the HUD view to the HUD; called once by Glue. */
    public void register() {
        RenderEvents.POST_WORLD_RENDER.register(this::capture);
        RenderEvents.RENDER_HUD.register(this::renderHud);
    }

    /** Every buffer, found when the viewer last started capturing. */
    public List<Buffer> buffers() {
        return this.buffers;
    }

    /** Whether some buffer is an Iris alternate, which the alternates setting shows or hides. */
    public boolean hasAlternates() {
        return this.buffers.stream().anyMatch(Buffer::alternate);
    }

    /** The visible buffers on the grid's current page. */
    public List<Buffer> onGrid() {
        List<Buffer> visible = this.visible();
        int perPage = this.gridSize * this.gridSize;
        int start = Math.min(this.gridPage(visible.size()) * perPage, visible.size());
        return visible.subList(start, Math.min(start + perPage, visible.size()));
    }

    public int gridPage() {
        return this.gridPage(this.visible().size());
    }

    public int pageCount() {
        return this.pageCount(this.visible().size());
    }

    /** Moves the grid by a number of pages, staying between the first and the last. */
    public void turnPage(int pages) {
        this.gridPage = Math.clamp((long) this.gridPage() + pages, 0, this.pageCount() - 1);
    }

    public Filter filter() {
        return this.filter;
    }

    public void setFilter(Filter filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
        this.gridPage = 0;
    }

    public boolean showsAlternates() {
        return this.alternates;
    }

    public void setShowsAlternates(boolean alternates) {
        this.alternates = alternates;
        this.gridPage = 0;
    }

    public int gridSize() {
        return this.gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = Math.clamp(gridSize, 1, MAX_GRID_SIZE);
        this.gridPage = 0;
    }

    public boolean isShown(String name) {
        return !this.hidden.contains(name);
    }

    public void setShown(String name, boolean shown) {
        if (shown) {
            this.hidden.remove(name);
        } else {
            this.hidden.add(name);
        }
        this.gridPage = Math.min(this.gridPage, this.pageCount() - 1);
    }

    /** Called as the page is shown and hidden. */
    public void setPageShown(boolean shown) {
        boolean wasActive = this.isActive();
        this.pageShown = shown;
        this.update(wasActive);
    }

    /** Shows or hides the grid on the HUD, without input, while the menu is closed. */
    public void setOnHud(boolean onHud) {
        boolean wasActive = this.isActive();
        this.onHud = onHud;
        if (!onHud && this.hudGrid != null) {
            this.hudGrid.release();
            this.hudGrid = null;
        }
        this.update(wasActive);
    }

    private boolean isActive() {
        return this.pageShown || this.onHud;
    }

    private void update(boolean wasActive) {
        if (!wasActive && this.isActive()) {
            this.buffers = this.findBuffers();
        } else if (wasActive && !this.isActive()) {
            this.free();
        }
    }

    private List<Buffer> visible() {
        return this.buffers.stream()
                .filter(buffer -> this.passesFilter(buffer) && !this.hidden.contains(buffer.name()))
                .toList();
    }

    private boolean passesFilter(Buffer buffer) {
        if (buffer.alternate() && !this.alternates) return false;

        return switch (this.filter) {
            case ALL -> true;
            case COLOUR -> !buffer.depth();
            case DEPTH -> buffer.depth();
        };
    }

    private int gridPage(int visible) {
        return Math.min(this.gridPage, this.pageCount(visible) - 1);
    }

    private int pageCount(int visible) {
        int perPage = this.gridSize * this.gridSize;
        return Math.max(1, (visible + perPage - 1) / perPage);
    }

    private List<Buffer> findBuffers() {
        List<Buffer> found = new ArrayList<>();
        Object[] targets = RenderCompat.getIrisRenderTargetArray();
        if (targets != null) {
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] == null) continue;

                String name = "colortex" + i;
                int[] textures = RenderCompat.getIrisTargetTextures(targets[i], name);
                if (textures == null) continue;

                found.add(new Buffer(name, false, false, fixed(textures[0], textures[2], textures[3])));
                found.add(new Buffer(name + " alt", true, false, fixed(textures[1], textures[2], textures[3])));
            }
        }
        this.iris = !found.isEmpty();
        if (!this.iris) found.add(new Buffer("Main colour", false, false, this::colorCopyTexture));
        found.add(new Buffer("Main depth", false, true, this.depthPreview::texture));
        for (Extra extra : EXTRAS) {
            found.add(new Buffer(extra.name(), false, false, () -> mainSized(extra.id().getAsInt())));
        }
        return List.copyOf(found);
    }

    private void capture() {
        if (!this.isActive()) return;

        List<Buffer> shown = this.onGrid();
        boolean color = !this.iris && shown.stream().anyMatch(buffer -> "Main colour".equals(buffer.name()));
        boolean depth = shown.stream().anyMatch(Buffer::depth);
        if (!color && !depth) return;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        SavedGlState saved = SavedGlState.save();
        try {
            if (color) this.copyColor(main);
            if (depth) this.captureDepth(main);
        } finally {
            saved.restore();
        }
    }

    private void copyColor(RenderTarget main) {
        if (!(main.getColorTexture() instanceof GlTexture source)) return;

        if (this.colorCopy == -1 || this.colorCopyWidth != main.width || this.colorCopyHeight != main.height) {
            if (this.colorCopy != -1) GL11.glDeleteTextures(this.colorCopy);
            this.colorCopy = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.colorCopy);
            GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8, main.width, main.height);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            this.colorCopyWidth = main.width;
            this.colorCopyHeight = main.height;
        }
        GL43.glCopyImageSubData(source.glId(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                this.colorCopy, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, main.width, main.height, 1);
    }

    private void captureDepth(RenderTarget main) {
        GpuTexture mainDepth = main.getDepthTexture();
        if (mainDepth == null) return;

        int irisDepth = RenderCompat.getIrisSceneDepthGlId();
        if (irisDepth != -1) {
            this.depthPreview.capture(irisDepth, mainDepth.getWidth(0), mainDepth.getHeight(0));
            return;
        }

        if (this.depthCopy == null || this.depthCopy.width != main.width || this.depthCopy.height != main.height) {
            if (this.depthCopy != null) this.depthCopy.destroyBuffers();
            this.depthCopy = new TextureTarget("Glue framebuffer viewer depth", main.width, main.height, true);
        }
        this.depthCopy.copyDepthFrom(main);
        if (this.depthCopy.getDepthTexture() instanceof GlTexture copy) {
            this.depthPreview.capture(copy.glId(), this.depthCopy.width, this.depthCopy.height);
        }
    }

    private void free() {
        if (this.colorCopy != -1) GL11.glDeleteTextures(this.colorCopy);
        this.colorCopy = -1;
        this.depthPreview.free();
        if (this.depthCopy != null) this.depthCopy.destroyBuffers();
        this.depthCopy = null;
        this.buffers = List.of();
        this.iris = false;
    }

    private void renderHud(GuiGraphics graphics) {
        if (!this.onHud) return;

        if (this.hudGrid == null) this.hudGrid = new FramebufferGrid(this);
        Font font = Minecraft.getInstance().font;
        int padding = UiStyle.DEFAULT.padding();
        int top = 2 * padding + font.lineHeight;
        graphics.drawString(font, HUD_HINT, padding, padding, UiStyle.DEFAULT.text());
        this.hudGrid.setRectangle(graphics.guiWidth() - 2 * padding, graphics.guiHeight() - top - padding, padding, top);
        this.hudGrid.render(graphics, -1, -1, 0.0F);
    }

    private UiTextureView.@Nullable Texture colorCopyTexture() {
        if (this.colorCopy == -1) return null;

        return new UiTextureView.Texture(this.colorCopy, this.colorCopyWidth, this.colorCopyHeight, true);
    }

    private static Supplier<UiTextureView.@Nullable Texture> fixed(int id, int width, int height) {
        UiTextureView.Texture texture = new UiTextureView.Texture(id, width, height, true);
        return () -> texture;
    }

    private static UiTextureView.@Nullable Texture mainSized(int id) {
        if (id <= 0) return null;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        return new UiTextureView.Texture(id, main.width, main.height, true);
    }

    /**
     * A buffer the viewer can show.
     *
     * @param alternate whether it is an Iris alternate, hidden unless the alternates setting is on
     * @param depth whether it is the depth preview, which is captured only while it is on the grid
     * @param texture its texture this frame, or null while there is none
     */
    public record Buffer(String name, boolean alternate, boolean depth, Supplier<UiTextureView.@Nullable Texture> texture) {
    }

    /** Which buffers the grid shows. */
    public enum Filter {
        ALL, COLOUR, DEPTH;

        public Component title() {
            return Component.translatable("glue.developer_menu.framebuffers.filter." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    private record Extra(String name, IntSupplier id) {
    }
}
