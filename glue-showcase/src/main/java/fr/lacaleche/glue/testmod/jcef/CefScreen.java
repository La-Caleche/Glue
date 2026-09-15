package fr.lacaleche.glue.testmod.jcef;

import fr.lacaleche.glue.web.WebCursor;
import fr.lacaleche.glue.web.WebMetrics;
import fr.lacaleche.glue.web.WebPointerEvent;
import fr.lacaleche.glue.web.WebSurface;
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

/** Single-tab native browser with Minecraft-owned address bar and telemetry. */
public final class CefScreen extends Screen {

    private final String initialUrl;
    private final String demoUrl;
    private final String messageOrigin;
    private final Screen previous;
    private WebSurface surface;
    private EditBox address;
    private Button back;
    private Button forward;
    private Button reload;
    private boolean detailed;
    private int buttons;
    private int lastButton = -1;
    private double lastClickX;
    private double lastClickY;
    private long lastClickAt;
    private int clicks;
    private String observedUrl = "";
    private String addressError = "";
    private boolean activeWindow = true;

    public CefScreen(String url, String demoUrl, String messageOrigin, Screen previous) {
        super(Component.literal("Glue Web"));
        this.initialUrl = normalize(url);
        this.demoUrl = demoUrl;
        this.messageOrigin = messageOrigin;
        this.previous = previous;
    }

    @Override
    protected void init() {
        if (this.surface == null) {
            this.surface = WebSurface.builder(URI.create(this.initialUrl))
                    .size(this.webWidth(), this.webHeight())
                    .onMessage(URI.create(this.messageOrigin), JcefDemo::receiveMessage)
                    .open();
            if (!this.surface.isClosed()) this.surface.setFocused(true);
        } else {
            if (!this.surface.isClosed()) this.surface.resize(this.webWidth(), this.webHeight());
        }
        this.surface.setCursorActive(false);
        String text = this.address == null ? this.initialUrl : this.address.getValue();
        this.back = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.surface.back()).bounds(4, 4, 20, 20).build());
        this.forward = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.surface.forward()).bounds(26, 4, 20, 20).build());
        this.reload = this.addRenderableWidget(Button.builder(Component.literal("Reload"), button -> {
            if (this.surface.isLoading()) this.surface.stopLoading();
            else this.surface.reload();
        }).bounds(48, 4, 42, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Demo"), button -> this.navigate(this.demoUrl)).bounds(92, 4, 36, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Google"), button -> this.navigate("https://www.google.com/")).bounds(130, 4, 44, 20).build());
        this.address = new EditBox(this.font, 178, 4, Math.max(20, this.width - 214), 20, Component.literal("Address or search"));
        this.address.setMaxLength(4096);
        this.address.setValue(text);
        this.addRenderableWidget(this.address);
        this.addRenderableWidget(Button.builder(Component.literal("Go"), button -> this.navigate(this.address.getValue())).bounds(this.width - 32, 4, 28, 20).build());
    }

    @Override
    public void tick() {
        if (this.surface.isClosed()) {
            this.back.active = this.forward.active = this.reload.active = false;
            return;
        }
        this.back.active = this.surface.canBack();
        this.forward.active = this.surface.canForward();
        this.reload.setMessage(Component.literal(this.surface.isLoading() ? "Stop" : "Reload"));
        if (!this.surface.url().equals(this.observedUrl)) {
            this.observedUrl = this.surface.url();
            if (!this.address.isFocused()) this.address.setValue(this.observedUrl);
        }
        boolean active = this.minecraft.isWindowActive();
        if (this.activeWindow != active) {
            this.surface.setFocused(active && this.getFocused() == null);
            if (!active) this.buttons = 0;
        }
        this.activeWindow = active;
        if (!active) this.surface.setCursorActive(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.surface.isClosed()) this.surface.draw(graphics, 0, this.viewY(), this.width, this.viewHeight());
        graphics.fill(0, 0, this.width, this.viewY(), 0xFF131C2A);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.statusText(), this.width - 8),
                4, 28, 0xFFCFDCEC, false);
        drawMetrics(graphics, this.surface, this.height - this.debugHeight(), this.width, this.detailed);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.updateCursor(mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Chromium owns page backgrounds; transparent local UI pixels reveal the game.
    }

    @Override
    public void mouseMoved(double x, double y) {
        this.pointer(this.inside(x, y) ? WebPointerEvent.MOVED : WebPointerEvent.EXITED, x, y, -1, 1);
        this.updateCursor(x, y);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (this.surface.isClosed() || button < 0 || button > 2) return super.mouseClicked(x, y, button);
        if (!this.inside(x, y)) {
            this.surface.setFocused(false);
            return super.mouseClicked(x, y, button);
        }
        this.setFocused(null);
        this.surface.setFocused(true);
        long now = System.nanoTime();
        this.clicks = button == this.lastButton && now - this.lastClickAt < 500_000_000L
                && Math.abs(x - this.lastClickX) < 3 && Math.abs(y - this.lastClickY) < 3 ? this.clicks % 3 + 1 : 1;
        this.lastButton = button;
        this.lastClickAt = now;
        this.lastClickX = x;
        this.lastClickY = y;
        this.buttons |= 1 << button;
        this.pointer(WebPointerEvent.PRESSED, x, y, button, this.clicks);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if ((this.buttons & 1 << button) == 0) return super.mouseReleased(x, y, button);
        this.buttons &= ~(1 << button);
        this.pointer(WebPointerEvent.RELEASED, x, y, button, this.clicks);
        this.updateCursor(x, y);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if ((this.buttons & 1 << button) == 0) return super.mouseDragged(x, y, button, dx, dy);
        this.pointer(WebPointerEvent.DRAGGED, x, y, button, 1);
        this.updateCursor(x, y);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (this.surface.isClosed()) return false;
        if (!this.inside(x, y)) return super.mouseScrolled(x, y, dx, dy);
        this.surface.wheel(this.webX(x), this.webY(y), this.modifiers(), (int) (dx * 120), (int) (dy * 120));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.surface.hasPopup()) this.surface.keyPressed(key, scanCode, modifiers);
            else this.onClose();
            return true;
        }
        if (this.surface.isClosed()) return super.keyPressed(key, scanCode, modifiers);
        if (key == GLFW.GLFW_KEY_F3) {
            this.detailed = !this.detailed;
            this.surface.resize(this.webWidth(), this.webHeight());
            return true;
        }
        if (key == GLFW.GLFW_KEY_F9) {
            this.surface.setFpsLimit(this.surface.fpsLimit() == 60 ? 30 : 60);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            this.surface.reload();
            return true;
        }
        if (key == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            this.surface.setFocused(false);
            this.setFocused(this.address);
            this.address.setFocused(true);
            this.address.setCursorPosition(this.address.getValue().length());
            this.address.setHighlightPos(0);
            return true;
        }
        if (this.address.isFocused() && key == GLFW.GLFW_KEY_ENTER) {
            this.navigate(this.address.getValue());
            return true;
        }
        if (this.getFocused() != null) return super.keyPressed(key, scanCode, modifiers);
        this.surface.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (this.surface.isClosed()) return false;
        if (this.getFocused() != null) return super.keyReleased(key, scanCode, modifiers);
        this.surface.keyReleased(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (this.surface.isClosed()) return false;
        if (this.getFocused() != null) return super.charTyped(character, modifiers);
        this.surface.character(character, modifiers);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previous);
    }

    @Override
    public void removed() {
        this.closeSurface();
    }

    /** Client-thread teardown, also used before GLFW terminates during client shutdown. */
    public void closeSurface() {
        if (this.surface != null) {
            this.surface.close();
            this.surface = null;
        }
    }

    public WebSurface surface() { return this.surface; }
    public WebCursor appliedCursor() { return this.surface == null ? WebCursor.ARROW : this.surface.appliedCursor(); }
    public int webWidth() { return Math.clamp(this.width * 2, 1, 4096); }
    public int webHeight() { return Math.clamp(this.viewHeight() * 2, 1, 4096); }
    public int viewY() { return 40; }
    public int viewHeight() { return Math.max(1, this.height - this.viewY() - this.debugHeight()); }

    public void navigate(String address) {
        if (this.surface.isClosed() || !this.surface.isReady()) return;
        try {
            String target = normalize(address);
            this.address.setValue(target);
            this.address.setFocused(false);
            this.setFocused(null);
            this.addressError = "";
            this.surface.navigate(URI.create(target));
            this.surface.setFocused(true);
        } catch (IllegalArgumentException exception) {
            this.addressError = exception.getMessage();
        }
    }

    public static void drawMetrics(GuiGraphics graphics, WebSurface surface, int y, int width, boolean detailed) {
        WebMetrics metrics = surface.metrics();
        Minecraft client = Minecraft.getInstance();
        String[] lines = {
                String.format(Locale.ROOT, "Game %d | Browser %.1f FPS | Upload %.1f",
                        client.getFps(), metrics.paintFps(), metrics.uploadFps()),
                String.format(Locale.ROOT, "Capture %.2f | Stage %.2f | Upload %.2f ms",
                        metrics.captureMs(), metrics.stagingMs(), metrics.uploadMs()),
                String.format(Locale.ROOT, "Dirty %.1f%% | Coalesced %d | Age %.0f ms",
                        metrics.dirtyPercent(), metrics.coalesced(), surface.frameAgeMs()),
                "F3 details | F9 cap " + surface.fpsLimit()
        };
        graphics.fill(0, y, width, y + (detailed ? 54 : 14), 0xE00D1420);
        for (int i = 0; i < (detailed ? lines.length : 1); i++) {
            graphics.drawString(client.font, client.font.plainSubstrByWidth(lines[i], width - 8),
                    4, y + 3 + i * 10, 0xFFE7EDF8, false);
        }
    }

    private String statusText() {
        if (!this.addressError.isEmpty()) return this.addressError;
        if (!this.surface.error().isEmpty()) return this.surface.error();
        if (!this.surface.isReady()) return WebSurface.runtimeStatus();
        return this.surface.isLoading() ? "Loading…" : this.surface.title();
    }

    private int debugHeight() { return this.detailed ? 54 : 14; }
    private boolean inside(double x, double y) { return x >= 0 && x < this.width && y >= this.viewY() && y < this.viewY() + this.viewHeight(); }
    private int webX(double x) { return (int) (x * this.webWidth() / Math.max(1, this.width)); }
    private int webY(double y) { return (int) ((y - this.viewY()) * this.webHeight() / this.viewHeight()); }
    private int modifiers() { return (hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) | (hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0) | (hasAltDown() ? GLFW.GLFW_MOD_ALT : 0); }

    private void pointer(WebPointerEvent type, double x, double y, int button, int count) {
        if (!this.surface.isClosed()) this.surface.mouse(type, this.webX(x), this.webY(y), button, this.modifiers(), count);
    }

    private void updateCursor(double x, double y) {
        if (this.surface == null) return;
        this.surface.setCursorActive(this.minecraft.screen == this && this.minecraft.isWindowActive()
                && this.minecraft.getOverlay() == null && !this.minecraft.mouseHandler.isMouseGrabbed()
                && (this.inside(x, y) || this.buttons != 0));
    }

    static String normalize(String input) {
        String text = input.strip();
        if (text.isEmpty()) return "https://www.google.com/";
        if (!text.contains("://") && (text.contains(" ") || !text.contains("."))) return "https://www.google.com/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        URI uri = URI.create(text.contains("://") ? text : "https://" + text);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Enter an HTTP(S) address");
        return uri.toASCIIString();
    }
}
