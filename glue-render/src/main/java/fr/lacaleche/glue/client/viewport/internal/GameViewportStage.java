package fr.lacaleche.glue.client.viewport.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.client.viewport.GameViewport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Runtime for {@link GameViewport}: owns the offscreen world target, stands in for the main render
 * target across the world pass, composites the result back into the frame, and confines the HUD to
 * the same rectangle.
 *
 * <p>Bounds are snapshotted once at the top of the frame and every stage below reads that snapshot,
 * so the world, the composite and the HUD cannot disagree even though {@link GameViewport} is written
 * from the UI thread.</p>
 *
 * <p>The window reports viewport dimensions twice per frame, for different reasons. Across the world
 * pass only the framebuffer size is swapped, which is what the projection aspect, the screen-size
 * uniform and any consumer sizing buffers from the window read. Across the HUD pass the GUI-scaled
 * size is swapped too, so the HUD <em>lays out</em> against the viewport rather than being scaled onto
 * it after the fact; the GUI scale itself is left alone, so HUD text stays pixel-aligned.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameViewportStage {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("glue", "game_viewport");
    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("glue", "pipeline/game_viewport_composite"))
                    .withFragmentShader(ResourceLocation.fromNamespaceAndPath(
                            "glue", "internal/game_viewport_composite"))
                    .withoutBlend()
                    .build());

    private static RenderTarget target;
    private static RenderTarget blurTarget;
    private static RenderTarget blurTargetOverride;
    private static GameViewport.Bounds frame;
    private static GameViewport.Bounds composited;
    private static int worldStashedWidth;
    private static int worldStashedHeight;
    private static int guiStashedWidth;
    private static int guiStashedHeight;
    private static GameViewport.Bounds screenLayoutBounds;
    private static boolean redirecting;
    private static boolean guiScoped;
    private static int registeredTexture = -1;
    private static int registeredWidth;
    private static int registeredHeight;

    private GameViewportStage() {
    }

    /** The target every {@code Minecraft#getMainRenderTarget()} call resolves to during the world pass. */
    public static RenderTarget renderTargetOverride() {
        if (blurTargetOverride != null) return blurTargetOverride;

        return redirecting ? target : null;
    }

    /**
     * Runs vanilla's deferred screen blur over only the game viewport. A scissor can constrain the
     * pass's writes but not its samples, so the viewport is copied into an exact-sized target first;
     * this keeps both the blur and its sampling kernel away from the surrounding workspace.
     */
    public static void processBlurEffect(GameRenderer renderer) {
        GameViewport.Bounds bounds = frame;
        if (bounds == null) {
            renderer.processBlurEffect();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        long left = Math.max(0L, bounds.x());
        long top = Math.max(0L, bounds.y());
        long right = Math.min((long) mainTarget.width, (long) bounds.x() + bounds.width());
        long bottom = Math.min((long) mainTarget.height, (long) bounds.y() + bounds.height());
        if (right <= left || bottom <= top) return;

        int x = (int) left;
        int y = (int) top;
        int width = (int) (right - left);
        int height = (int) (bottom - top);
        if (x == 0 && y == 0 && width == mainTarget.width && height == mainTarget.height) {
            renderer.processBlurEffect();
            return;
        }

        if (blurTarget == null) {
            blurTarget = new TextureTarget("Glue game viewport blur", width, height, false);
        } else if (blurTarget.width != width || blurTarget.height != height) {
            blurTarget.resize(width, height);
        }

        int textureY = mainTarget.height - y - height;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainTarget.getColorTexture(), blurTarget.getColorTexture(),
                0, 0, 0, x, textureY, width, height);

        blurTargetOverride = blurTarget;
        try {
            renderer.processBlurEffect();
        } finally {
            blurTargetOverride = null;
        }

        encoder.copyTextureToTexture(blurTarget.getColorTexture(), mainTarget.getColorTexture(),
                0, x, textureY, 0, 0, width, height);
    }

    /**
     * Takes this frame's bounds snapshot and opens the world pass. Runs before anything derived from
     * the window size, so the screen-size uniform the world's shaders sample agrees with the target
     * the world lands in.
     *
     * @param renderLevel whether this frame draws a world at all; without one there is nothing to
     *                    redirect and nothing to composite.
     */
    public static void beginFrame(boolean renderLevel) {
        frame = GameViewport.bounds();
        syncScreenLayout();
        if (frame == null) {
            releaseTarget();
            return;
        }
        if (!renderLevel || redirecting) return;

        Window window = Minecraft.getInstance().getWindow();
        target = FramebufferHelper.resizeOrCreate(target, frame.width(), frame.height());
        FramebufferHelper.clear(target, 0f, 0f, 0f, 0f);
        worldStashedWidth = window.getWidth();
        worldStashedHeight = window.getHeight();
        window.setWidth(frame.width());
        window.setHeight(frame.height());
        composited = frame;
        redirecting = true;
    }

    public static void endWorld() {
        if (!redirecting) return;

        redirecting = false;
        Window window = Minecraft.getInstance().getWindow();
        window.setWidth(worldStashedWidth);
        window.setHeight(worldStashedHeight);
    }

    /**
     * Draws the world back into the frame as the bottom GUI element, under the HUD and any screen.
     *
     * <p>The blit is deliberately unblended and its fragment shader never discards alpha-zero texels.
     * Nothing treats the world target's alpha as coverage: vanilla clears that target to alpha zero
     * and presents it straight to the screen, so whatever the sky, fog, clouds and weather leave in
     * the alpha channel means nothing. The ordinary GUI fragment shader discards those pixels even
     * when blending is disabled; the viewport pipeline instead presents every world RGB value and
     * writes opaque output, which is what vanilla's final presentation accomplishes.</p>
     */
    public static void composite(GuiGraphics graphics) {
        GameViewport.Bounds bounds = composited;
        composited = null;
        if (bounds == null || target == null) return;

        int texture = FramebufferHelper.getColorTextureId(target);
        if (texture <= 0) return;

        Minecraft minecraft = Minecraft.getInstance();
        register(minecraft, texture, bounds.width(), bounds.height());
        // Draw in framebuffer units under an inverse GUI-scale transform. Integer GUI corners would
        // round away viewport origins and extents that are not divisible by the GUI scale.
        int scale = minecraft.getWindow().getGuiScale();
        int width = bounds.width();
        int height = bounds.height();
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) bounds.x() / scale, (float) bounds.y() / scale);
            graphics.pose().scale(1.0f / scale, 1.0f / scale);
            // Pipeline-taking overloads use source regions in texels: the vertical flip starts at the
            // bottom row and runs backwards.
            graphics.blit(COMPOSITE_PIPELINE, TEXTURE,
                    0, 0, 0f, height, width, height, width, -height, width, height);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Confines the HUD to the viewport rectangle. Reporting the viewport's GUI-scaled size makes the
     * HUD lay itself out against the viewport — the hotbar centres on it, chat wraps to it — and the
     * pose then carries that layout to where the world was drawn. Scissor rectangles are captured
     * through the pose, so clipped elements such as chat follow. Balanced by {@link #endHud}.
     */
    public static void beginHud(GuiGraphics graphics) {
        beginGuiScope(graphics);
    }

    public static void endHud(GuiGraphics graphics) {
        endGuiScope(graphics);
    }

    /**
     * Confines an open screen to the viewport rectangle exactly as {@link #beginHud} confines the
     * HUD: the screen was laid out against the viewport's GUI-scaled size, and the pose carries that
     * layout to where the world was drawn. Balanced by {@link #endScreen}.
     */
    public static void beginScreen(GuiGraphics graphics) {
        beginGuiScope(graphics);
    }

    public static void endScreen(GuiGraphics graphics) {
        endGuiScope(graphics);
    }

    /**
     * Reports the viewport's dimensions from the window and translates the pose to its origin, so a
     * GUI pass lays out against the viewport and lands on it. The HUD and screen passes never nest,
     * so one stash serves both. Setting the unchanged GUI scale is what recomputes the cached
     * GUI-scaled size from the swapped framebuffer size.
     */
    private static void beginGuiScope(GuiGraphics graphics) {
        graphics.pose().pushMatrix();

        GameViewport.Bounds bounds = frame;
        if (bounds == null) return;

        Window window = Minecraft.getInstance().getWindow();
        int scale = window.getGuiScale();
        guiStashedWidth = window.getWidth();
        guiStashedHeight = window.getHeight();
        window.setWidth(bounds.width());
        window.setHeight(bounds.height());
        window.setGuiScale(scale);
        guiScoped = true;
        graphics.pose().translate((float) bounds.x() / scale, (float) bounds.y() / scale);
    }

    private static void endGuiScope(GuiGraphics graphics) {
        graphics.pose().popMatrix();
        if (!guiScoped) return;

        guiScoped = false;
        Window window = Minecraft.getInstance().getWindow();
        window.setWidth(guiStashedWidth);
        window.setHeight(guiStashedHeight);
        window.setGuiScale(window.getGuiScale());
    }

    /**
     * Maps the raw cursor into the viewport's GUI space — the space the HUD and any open screen are
     * laid out in while the viewport is active. Computed directly from the raw position and the
     * full-window dimensions rather than from the window's cached GUI size, which the HUD and screen
     * scopes narrow to the viewport for their duration: a conversion built on the narrowed size
     * would compress any pointer polled from inside those scopes toward the viewport origin. Reads
     * the frame snapshot, so pointer coordinates agree with what the frame actually shows.
     */
    public static double viewportGuiX(Window window, double rawX) {
        GameViewport.Bounds bounds = frame;
        return ViewportMouse.toViewportGui(rawX, window.getScreenWidth(), fullFrameWidth(window),
                bounds == null ? 0 : bounds.x(), window.getGuiScale());
    }

    public static double viewportGuiY(Window window, double rawY) {
        GameViewport.Bounds bounds = frame;
        return ViewportMouse.toViewportGui(rawY, window.getScreenHeight(), fullFrameHeight(window),
                bounds == null ? 0 : bounds.y(), window.getGuiScale());
    }

    public static double viewportGuiDeltaX(Window window, double deltaX) {
        return ViewportMouse.deltaToViewportGui(deltaX, window.getScreenWidth(), fullFrameWidth(window),
                window.getGuiScale());
    }

    public static double viewportGuiDeltaY(Window window, double deltaY) {
        return ViewportMouse.deltaToViewportGui(deltaY, window.getScreenHeight(), fullFrameHeight(window),
                window.getGuiScale());
    }

    /** The GUI-scaled width screens must lay out against: the viewport's while one is active. */
    public static int effectiveGuiWidth(Window window) {
        GameViewport.Bounds bounds = frame;
        return bounds == null ? window.getGuiScaledWidth() : Math.ceilDiv(bounds.width(), window.getGuiScale());
    }

    public static int effectiveGuiHeight(Window window) {
        GameViewport.Bounds bounds = frame;
        return bounds == null ? window.getGuiScaledHeight() : Math.ceilDiv(bounds.height(), window.getGuiScale());
    }

    /** Whether GUI passes and pointer coordinates are currently confined to a viewport. */
    public static boolean confinesGui() {
        return frame != null;
    }

    /** Whether the current GUI pass has already entered the viewport transform. */
    public static boolean isGuiScoped() {
        return guiScoped;
    }

    /** The full-window framebuffer width, no matter which stage has narrowed the live window. */
    private static int fullFrameWidth(Window window) {
        if (guiScoped) return guiStashedWidth;
        if (redirecting) return worldStashedWidth;
        return window.getWidth();
    }

    private static int fullFrameHeight(Window window) {
        if (guiScoped) return guiStashedHeight;
        if (redirecting) return worldStashedHeight;
        return window.getHeight();
    }

    /**
     * Relays an open screen out whenever the rectangle it was laid out against changes — the viewport
     * moved or resized under it, appeared while it was open, or went away. A screen that opened while
     * the viewport was active is already laid out against it; this catches every change after that.
     */
    private static void syncScreenLayout() {
        GameViewport.Bounds previous = screenLayoutBounds;
        screenLayoutBounds = frame;
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null || Objects.equals(frame, previous)) return;

        Window window = minecraft.getWindow();
        screen.resize(minecraft, effectiveGuiWidth(window), effectiveGuiHeight(window));
    }

    private static void releaseTarget() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
        }
        if (blurTarget != null) {
            blurTarget.destroyBuffers();
            blurTarget = null;
        }
        blurTargetOverride = null;
        composited = null;
        registeredTexture = -1;
        registeredWidth = 0;
        registeredHeight = 0;
    }

    private static void register(Minecraft minecraft, int texture, int width, int height) {
        if (texture == registeredTexture && width == registeredWidth && height == registeredHeight) return;

        registeredTexture = texture;
        registeredWidth = width;
        registeredHeight = height;
        minecraft.getTextureManager().register(TEXTURE, new BorrowedTexture(texture, width, height));
    }

    /** Wraps a GL texture this class does not own, so the GUI blit can sample it like any other. */
    private static final class BorrowedTexture extends AbstractTexture {

        private BorrowedTexture(int id, int width, int height) {
            BorrowedGlTexture borrowed = new BorrowedGlTexture(id, width, height);
            this.texture = borrowed;
            this.textureView = new BorrowedGlTextureView(borrowed);
        }
    }

    private static final class BorrowedGlTexture extends GlTexture {

        private BorrowedGlTexture(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "glue game viewport", TextureFormat.RGBA8,
                    width, height, 1, 1, id);
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void removeViews() {
        }
    }

    private static final class BorrowedGlTextureView extends GlTextureView {

        private BorrowedGlTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
        }
    }
}
