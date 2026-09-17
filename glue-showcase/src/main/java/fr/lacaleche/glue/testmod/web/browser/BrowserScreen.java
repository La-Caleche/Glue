package fr.lacaleche.glue.testmod.web.browser;

import fr.lacaleche.glue.testmod.web.WebDemos;

import fr.lacaleche.glue.web.WebMetrics;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.host.WebWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * A single-tab browser: vanilla toolbar widgets around a {@link WebWidget}. Any address can be
 * opened, so no page gets the bridge, not even this mod's own pages. F3 expands delivery metrics, F9
 * switches the frame cap, F5 reloads and Ctrl+L focuses the address.
 */
public final class BrowserScreen extends Screen {

    public static final String HOME = "https://lacaleche.cc/";
    public static final int PAGE_TOP = 40;

    private final Screen parent;
    private final WebWidget page;
    private EditBox address;
    private Button back;
    private Button forward;
    private Button reload;
    private String typedAddress;
    private String observedUrl = "";
    private String addressError = "";
    private boolean detailed;

    public BrowserScreen(String url, Screen parent) {
        super(Component.literal("Glue Web browser"));
        this.parent = parent;
        this.typedAddress = normalize(url);
        this.page = WebWidget.builder(URI.create(this.typedAddress))
                .title(Component.literal("Web page"))
                .withoutBridge()
                .build(0, PAGE_TOP, 1, 1);
    }

    public static BrowserScreen open(String url) {
        Minecraft client = Minecraft.getInstance();
        BrowserScreen screen = new BrowserScreen(url, client.screen);
        client.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        this.back = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.surface().ifPresent(WebSurface::back))
                .bounds(4, 4, 20, 20).build());
        this.forward = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.surface().ifPresent(WebSurface::forward))
                .bounds(26, 4, 20, 20).build());
        this.reload = this.addRenderableWidget(Button.builder(Component.literal("Reload"), button -> this.surface().ifPresent(surface -> {
            if (surface.isLoading()) surface.stopLoading();
            else surface.reload();
        })).bounds(48, 4, 42, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Lab"),
                button -> this.navigate(WebDemos.app().page("lab.html").toString())).bounds(92, 4, 30, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Google"),
                button -> this.navigate("https://www.google.com/")).bounds(124, 4, 44, 20).build());
        this.address = new EditBox(this.font, 172, 4, Math.max(20, this.width - 208), 20, Component.literal("Address or search"));
        this.address.setMaxLength(4096);
        this.address.setValue(this.typedAddress);
        this.address.setResponder(value -> this.typedAddress = value);
        this.addRenderableWidget(this.address);
        this.addRenderableWidget(Button.builder(Component.literal("Go"), button -> this.navigate(this.address.getValue()))
                .bounds(this.width - 32, 4, 28, 20).build());

        this.page.setRectangle(this.width, this.pageHeight(), 0, PAGE_TOP);
        this.addRenderableWidget(this.page);
        this.setInitialFocus(this.page);
    }

    @Override
    public void tick() {
        WebSurface surface = this.surface().filter(open -> !open.isClosed()).orElse(null);
        this.back.active = surface != null && surface.canBack();
        this.forward.active = surface != null && surface.canForward();
        this.reload.active = surface != null;
        if (surface == null) return;

        this.reload.setMessage(Component.literal(surface.isLoading() ? "Stop" : "Reload"));
        if (!surface.url().equals(this.observedUrl)) {
            this.observedUrl = surface.url();
            if (!this.address.isFocused()) this.address.setValue(this.observedUrl);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, PAGE_TOP, 0xFF16302E);
        graphics.fill(0, PAGE_TOP - 1, this.width, PAGE_TOP, 0xFFC9A45C);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.status(), this.width - 8), 4, 28, 0xFFF1EBDD, false);
        this.surface().ifPresent(surface -> this.drawMetrics(graphics, surface));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Pages paint their own background; outside a world the menu panorama stays visible.
        if (this.minecraft.level == null) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F3) {
            this.detailed = !this.detailed;
            this.page.setRectangle(this.width, this.pageHeight(), 0, PAGE_TOP);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F9) {
            this.surface().ifPresent(surface -> surface.setFpsLimit(surface.fpsLimit() == 60 ? 30 : 60));
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            this.surface().filter(surface -> !surface.isClosed()).ifPresent(WebSurface::reload);
            return true;
        }
        if (key == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            this.setFocused(this.address);
            this.address.moveCursorToEnd(false);
            this.address.setHighlightPos(0);
            return true;
        }
        if (this.address.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            this.navigate(this.address.getValue());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    public Optional<WebSurface> surface() {
        return this.page.surface();
    }

    public WebWidget page() {
        return this.page;
    }

    public int pageHeight() {
        return Math.max(1, this.height - PAGE_TOP - this.metricsHeight());
    }

    public void navigate(String input) {
        WebSurface surface = this.surface().filter(WebSurface::isReady).orElse(null);
        if (surface == null) return;
        try {
            String target = normalize(input);
            this.address.setValue(target);
            this.addressError = "";
            surface.navigate(URI.create(target));
            this.setFocused(this.page);
        } catch (IllegalArgumentException exception) {
            this.addressError = exception.getMessage();
        }
    }

    private String status() {
        if (!this.addressError.isEmpty()) return this.addressError;
        WebSurface surface = this.surface().orElse(null);
        if (surface == null) return WebSurface.runtimeStatus();
        if (!surface.error().isEmpty()) return surface.error();
        if (!surface.isReady()) return WebSurface.runtimeStatus();
        return surface.isLoading() ? "Loading…" : surface.title();
    }

    private void drawMetrics(GuiGraphics graphics, WebSurface surface) {
        WebMetrics metrics = surface.metrics();
        String[] lines = {
                String.format(Locale.ROOT, "Game %d | Browser %.1f FPS | Upload %.1f | %dx%d @%.1fx",
                        this.minecraft.getFps(), metrics.paintFps(), metrics.uploadFps(),
                        surface.width(), surface.height(), surface.scale()),
                String.format(Locale.ROOT, "Capture %.2f | Stage %.2f | Upload %.2f ms",
                        metrics.captureMs(), metrics.stagingMs(), metrics.uploadMs()),
                String.format(Locale.ROOT, "Dirty %.1f%% | Coalesced %d | Age %.0f ms",
                        metrics.dirtyPercent(), metrics.coalesced(), surface.frameAgeMs()),
                "F3 details | F9 cap " + surface.fpsLimit()
        };
        int top = this.height - this.metricsHeight();
        graphics.fill(0, top, this.width, this.height, 0xE016302E);
        for (int line = 0; line < (this.detailed ? lines.length : 1); line++) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(lines[line], this.width - 8),
                    4, top + 3 + line * 10, 0xFFF1EBDD, false);
        }
    }

    private int metricsHeight() {
        return this.detailed ? 44 : 14;
    }

    static String normalize(String input) {
        String text = input.strip();
        if (text.isEmpty()) return "https://www.google.com/";
        if (!text.contains("://") && (text.contains(" ") || !text.contains("."))) {
            return "https://www.google.com/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        }
        URI uri = URI.create(text.contains("://") ? text : "https://" + text);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("Enter an HTTP(S) address");
        return uri.toASCIIString();
    }
}
