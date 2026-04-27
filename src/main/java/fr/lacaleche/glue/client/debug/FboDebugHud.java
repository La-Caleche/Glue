package fr.lacaleche.glue.client.debug;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

public class FboDebugHud {

    public static final FboDebugHud INSTANCE = new FboDebugHud();
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-debug-hud");
    private static final int THUMB_SIZE = 256;
    private final List<CapturedTexture> irisCapturedTextures = new ArrayList<>();
    private final DepthLinearizer depthLinearizer = new DepthLinearizer();
    private final Set<String> hiddenBuffers = new LinkedHashSet<>();
    private final Set<Integer> keysDown = new HashSet<>();
    private boolean active;
    private boolean useIris;
    private RenderTarget depthCopyTarget;
    private int snapColorId = -1;
    private int snapW = -1, snapH = -1;
    private int currentPage;
    private int gridSize = 2;
    private FilterMode filterMode = FilterMode.ALL;
    private boolean hideAlt = true;
    private int sidebarCursor;
    private int sidebarScroll;
    private List<String> allBufferNames = List.of();

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    public void toggle() {
        active = !active;
        if (active) {
            irisCapturedTextures.clear();
            hiddenBuffers.clear();
            currentPage = 0;
            sidebarCursor = 0;
            sidebarScroll = 0;
            filterMode = FilterMode.ALL;
            hideAlt = true;
            useIris = captureIrisTargets();
            LOGGER.info("[Glue-FBO-HUD] Activated — mode: {}, buffers: {}",
                    useIris ? "Iris" : "Vanilla", irisCapturedTextures.size());
        } else {
            irisCapturedTextures.clear();
            useIris = false;
            allBufferNames = List.of();
            cleanup();
            LOGGER.info("[Glue-FBO-HUD] Deactivated");
        }
    }

    public boolean isActive() {
        return active;
    }

    public void captureDepthNow() {
        if (!active) return;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getDepthTexture() == null) return;

        int irisDepthId = RenderCompat.getIrisSceneDepthGlId();
        if (irisDepthId != -1) {
            depthLinearizer.capture(irisDepthId,
                    main.getDepthTexture().getWidth(0),
                    main.getDepthTexture().getHeight(0));
            return;
        }

        ensureDepthCopyTarget(main.width, main.height);
        depthCopyTarget.copyDepthFrom(main);
        GpuTexture copied = depthCopyTarget.getDepthTexture();
        if (copied instanceof GlTexture gl) {
            depthLinearizer.capture(gl.glId(), depthCopyTarget.width, depthCopyTarget.height);
        }
    }

    public boolean handleScroll(double delta) {
        if (!active) return false;
        currentPage += (delta > 0) ? -1 : 1;
        currentPage = Math.max(0, currentPage);
        return true;
    }

    public void tick() {
        if (!active) return;
        long win = Minecraft.getInstance().getWindow().getWindow();

        if (edge(win, GLFW.GLFW_KEY_LEFT)) currentPage = Math.max(0, currentPage - 1);
        if (edge(win, GLFW.GLFW_KEY_RIGHT)) currentPage++;
        if (edge(win, GLFW.GLFW_KEY_UP)) sidebarCursor--;
        if (edge(win, GLFW.GLFW_KEY_DOWN)) sidebarCursor++;
        if (edge(win, GLFW.GLFW_KEY_LEFT_BRACKET)) {
            filterMode = filterMode.prev();
            currentPage = 0;
        }
        if (edge(win, GLFW.GLFW_KEY_RIGHT_BRACKET)) {
            filterMode = filterMode.next();
            currentPage = 0;
        }
        if (edge(win, GLFW.GLFW_KEY_MINUS) || edge(win, GLFW.GLFW_KEY_KP_SUBTRACT)) {
            gridSize = Math.max(1, gridSize - 1);
            currentPage = 0;
        }
        if (edge(win, GLFW.GLFW_KEY_EQUAL) || edge(win, GLFW.GLFW_KEY_KP_ADD)) {
            gridSize = Math.min(4, gridSize + 1);
            currentPage = 0;
        }
        if (edge(win, GLFW.GLFW_KEY_GRAVE_ACCENT)) {
            hideAlt = !hideAlt;
            currentPage = 0;
        }

        if (edge(win, GLFW.GLFW_KEY_ENTER) || edge(win, GLFW.GLFW_KEY_KP_ENTER)) {
            if (sidebarCursor >= 0 && sidebarCursor < allBufferNames.size()) {
                String name = allBufferNames.get(sidebarCursor);
                if (!hiddenBuffers.remove(name)) hiddenBuffers.add(name);
            }
        }

        if (!allBufferNames.isEmpty())
            sidebarCursor = Math.max(0, Math.min(allBufferNames.size() - 1, sidebarCursor));
        else
            sidebarCursor = 0;
    }

    private boolean edge(long window, int key) {
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        if (down && keysDown.add(key)) return true;
        if (!down) keysDown.remove(key);
        return false;
    }

    public void render(GuiGraphics graphics) {
        if (!active) return;

        List<CapturedTexture> allTextures = useIris ? buildIrisTextureList() : captureVanillaFrame();
        if (allTextures.isEmpty()) return;

        allBufferNames = allTextures.stream().map(CapturedTexture::name).toList();

        List<CapturedTexture> visible = allTextures.stream()
                .filter(t -> passesFilter(t) && !hiddenBuffers.contains(t.name))
                .toList();

        int perPage = gridSize * gridSize;
        int totalPages = Math.max(1, (int) Math.ceil((double) visible.size() / perPage));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        int start = currentPage * perPage;
        int end = Math.min(start + perPage, visible.size());

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int headerH = 24, sidebarW = 110;

        graphics.fill(0, 0, screenW, screenH, 0xCC000000);
        renderHeader(graphics, screenW, headerH, currentPage + 1, totalPages, visible.size(), allTextures.size());
        renderSidebar(graphics, sidebarW, headerH, screenH, allTextures);
        if (start < end) {
            renderGrid(graphics, sidebarW, headerH, screenW - sidebarW, screenH - headerH, visible.subList(start, end));
        }
    }

    private boolean passesFilter(CapturedTexture t) {
        if (hideAlt && t.isAlt) return false;
        return switch (filterMode) {
            case ALL -> true;
            case COLOR -> !t.isDepth;
            case DEPTH -> t.isDepth;
        };
    }

    private void renderHeader(GuiGraphics g, int w, int h, int page, int totalPages, int shown, int total) {
        g.fill(0, 0, w, h, 0xFF222222);
        Font font = Minecraft.getInstance().font;
        String info = (useIris ? "Iris" : "Vanilla")
                + " | Page " + page + "/" + totalPages
                + " | Filter: " + filterMode.label
                + " | Grid: " + gridSize + "x" + gridSize
                + " | Alt: " + (hideAlt ? "Hidden" : "Shown")
                + " | " + shown + "/" + total + " buffers";
        g.drawString(font, info, 4, 2, 0xFFFFFFFF);
        g.drawString(font, "[<>|Scroll] Page  [^v] Select  [Enter] Toggle  [-=] Grid  [[] ]] Filter  [`] Alt",
                4, 13, 0xFF888888);
    }

    private void renderSidebar(GuiGraphics g, int w, int topY, int screenH, List<CapturedTexture> all) {
        g.fill(0, topY, w, screenH, 0xFF1A1A1A);
        g.fill(w - 1, topY, w, screenH, 0xFF333333);

        Font font = Minecraft.getInstance().font;
        int lineH = 11;
        int maxVisible = (screenH - topY - 4) / lineH;

        sidebarCursor = Math.max(0, Math.min(all.size() - 1, sidebarCursor));
        if (sidebarCursor < sidebarScroll) sidebarScroll = sidebarCursor;
        if (sidebarCursor >= sidebarScroll + maxVisible) sidebarScroll = sidebarCursor - maxVisible + 1;
        sidebarScroll = Math.max(0, sidebarScroll);
        if (all.size() > maxVisible) sidebarScroll = Math.min(sidebarScroll, all.size() - maxVisible);

        int y = topY + 2;
        for (int i = sidebarScroll; i < all.size() && i < sidebarScroll + maxVisible; i++) {
            CapturedTexture t = all.get(i);
            boolean sel = (i == sidebarCursor);
            boolean hidden = hiddenBuffers.contains(t.name);
            boolean filtered = !passesFilter(t);
            if (sel) g.fill(0, y - 1, w - 1, y + lineH - 2, 0xFF333355);

            String icon = hidden ? "[ ]" : filtered ? "[-]" : "[x]";
            String label = (sel ? "> " : "  ") + icon + " " + truncate(t.name, 12);
            int color = (!hidden && !filtered) ? 0xFFFFFFFF : hidden ? 0xFF666666 : 0xFF444444;
            g.drawString(font, label, 2, y, color);
            y += lineH;
        }

        if (all.size() > maxVisible) {
            int barH = screenH - topY;
            int thumbH = Math.max(8, barH * maxVisible / all.size());
            int range = all.size() - maxVisible;
            int thumbY = topY + (range > 0 ? (barH - thumbH) * sidebarScroll / range : 0);
            g.fill(w - 3, topY, w - 1, screenH, 0xFF2A2A2A);
            g.fill(w - 3, thumbY, w - 1, thumbY + thumbH, 0xFF777777);
        }
    }

    private void renderGrid(GuiGraphics g, int x, int y, int w, int h, List<CapturedTexture> textures) {
        int cellW = w / gridSize, cellH = h / gridSize, pad = 2;
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < textures.size(); i++) {
            CapturedTexture t = textures.get(i);
            int cx = x + (i % gridSize) * cellW + pad;
            int cy = y + (i / gridSize) * cellH + pad;
            int cw = cellW - pad * 2, ch = cellH - pad * 2;

            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("glue_debug", "tex_" + t.id);
            AbstractTexture existing = tm.getTexture(loc);
            if (!(existing instanceof ExternalTexture et) || et.wrappedId != t.id) {
                tm.register(loc, new ExternalTexture(t.id, t.width, t.height));
            }

            g.blit(loc, cx, cy, cx + cw, cy + ch, 0f, 1f, 1f, 0f);
            g.drawString(font, t.name + (t.isDepth ? " [DEPTH]" : ""), cx + 2, cy + 2, 0xFFFFFFFF);
            g.drawString(font, t.width + "x" + t.height + " (id:" + t.id + ")", cx + 2, cy + 12, 0xFFAAAAAA);
        }
    }

    private boolean captureIrisTargets() {
        Object[] targets = RenderCompat.getIrisRenderTargetArray();
        if (targets == null) return false;

        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == null) continue;
            String name = "C" + i;
            int[] data = RenderCompat.getIrisTargetTextures(targets[i], name);
            if (data != null) {
                irisCapturedTextures.add(new CapturedTexture(data[0], name, data[2], data[3], false, false));
                irisCapturedTextures.add(new CapturedTexture(data[1], name + "_alt", data[2], data[3], true, false));
            }
        }
        return !irisCapturedTextures.isEmpty();
    }

    private List<CapturedTexture> buildIrisTextureList() {
        List<CapturedTexture> out = new ArrayList<>(irisCapturedTextures);
        int depthId = depthLinearizer.getResult();
        if (depthId != -1) {
            out.add(new CapturedTexture(depthId, "Depth (Linear)", THUMB_SIZE, THUMB_SIZE, false, true));
        }
        return out;
    }

    private List<CapturedTexture> captureVanillaFrame() {
        List<CapturedTexture> out = new ArrayList<>();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return out;

        int w = main.width, h = main.height;
        GpuTexture color = main.getColorTexture();
        ensureColorSnapshot(w, h);

        if (color instanceof GlTexture gl) {
            GL43.glCopyImageSubData(gl.glId(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    snapColorId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            out.add(new CapturedTexture(snapColorId, "Main Color", w, h, false, false));
        }

        int depthId = depthLinearizer.getResult();
        if (depthId != -1) {
            out.add(new CapturedTexture(depthId, "Main Depth (Linear)", THUMB_SIZE, THUMB_SIZE, false, true));
        }
        return out;
    }

    private void cleanup() {
        if (snapColorId != -1) {
            GL11.glDeleteTextures(snapColorId);
            snapColorId = -1;
        }
        snapW = snapH = -1;
        depthLinearizer.cleanup();
        if (depthCopyTarget != null) {
            depthCopyTarget.destroyBuffers();
            depthCopyTarget = null;
        }
    }

    private void ensureDepthCopyTarget(int w, int h) {
        if (depthCopyTarget != null && depthCopyTarget.width == w && depthCopyTarget.height == h) return;
        if (depthCopyTarget != null) depthCopyTarget.destroyBuffers();
        depthCopyTarget = new TextureTarget("Glue Debug Depth Copy", w, h, true);
    }

    private void ensureColorSnapshot(int w, int h) {
        if (snapW == w && snapH == h) return;
        if (snapColorId != -1) GL11.glDeleteTextures(snapColorId);
        snapColorId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapColorId);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8, w, h);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        snapW = w;
        snapH = h;
    }

    enum FilterMode {
        ALL("All"), COLOR("Color"), DEPTH("Depth");
        final String label;

        FilterMode(String l) {
            this.label = l;
        }

        FilterMode next() {
            FilterMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        FilterMode prev() {
            FilterMode[] v = values();
            return v[(ordinal() - 1 + v.length) % v.length];
        }
    }

    private record CapturedTexture(int id, String name, int width, int height, boolean isAlt, boolean isDepth) {
    }

    private static class DepthLinearizer {
        private int textureId = -1;
        private boolean captured;

        private static void resetPack() {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        }

        private static void resetUnpack() {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        }

        void capture(int srcDepthGlId, int srcW, int srcH) {
            if (textureId == -1) {
                textureId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                resetUnpack();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, THUMB_SIZE, THUMB_SIZE,
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }

            resetPack();
            FloatBuffer raw = MemoryUtil.memAllocFloat(srcW * srcH);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcDepthGlId);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, raw);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                float min = 1f, max = 0f;
                float[] samples = new float[THUMB_SIZE * THUMB_SIZE];
                for (int y = 0; y < THUMB_SIZE; y++) {
                    int sy = y * srcH / THUMB_SIZE;
                    for (int x = 0; x < THUMB_SIZE; x++) {
                        float z = raw.get(sy * srcW + x * srcW / THUMB_SIZE);
                        samples[y * THUMB_SIZE + x] = z;
                        if (z < 1f) {
                            min = Math.min(min, z);
                            max = Math.max(max, z);
                        }
                    }
                }
                if (max <= min) {
                    min = 0f;
                    max = 1f;
                }

                ByteBuffer rgba = MemoryUtil.memAlloc(THUMB_SIZE * THUMB_SIZE * 4);
                try {
                    float range = max - min;
                    for (int i = 0; i < THUMB_SIZE * THUMB_SIZE; i++) {
                        byte c = (samples[i] < 1f) ? (byte) ((1f - (samples[i] - min) / range) * 255f) : 0;
                        int off = i * 4;
                        rgba.put(off, c).put(off + 1, c).put(off + 2, c).put(off + 3, (byte) 255);
                    }
                    resetUnpack();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, THUMB_SIZE, THUMB_SIZE,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                } finally {
                    MemoryUtil.memFree(rgba);
                }
            } finally {
                MemoryUtil.memFree(raw);
            }
            captured = true;
        }

        int getResult() {
            return captured ? textureId : -1;
        }

        void cleanup() {
            if (textureId != -1) GL11.glDeleteTextures(textureId);
            textureId = -1;
            captured = false;
        }
    }

    private static class ExternalTexture extends AbstractTexture {
        final int wrappedId;

        ExternalTexture(int id, int width, int height) {
            this.wrappedId = id;
            ExternalGlTexture gl = new ExternalGlTexture(id, width, height);
            this.texture = gl;
            this.textureView = new ExternalTextureView(gl);
        }
    }

    private static class ExternalGlTexture extends GlTexture {
        ExternalGlTexture(int id, int w, int h) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "glue debug hud", TextureFormat.RGBA8, w, h, 1, 1, id);
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void removeViews() {
        }
    }

    private static class ExternalTextureView extends GlTextureView {
        ExternalTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
