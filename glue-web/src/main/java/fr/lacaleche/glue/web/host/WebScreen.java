package fr.lacaleche.glue.web.host;

import fr.lacaleche.glue.web.WebBuilder;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.host.HostSizing;
import fr.lacaleche.glue.web.internal.host.HostTicker;
import fr.lacaleche.glue.web.internal.host.SurfaceInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A full-window screen whose content is one page. It forwards all pointer and keyboard input,
 * owns the cursor while displayed, and sizes the page to the GUI so one CSS pixel is one GUI pixel.
 *
 * <p>The page lives while the screen is displayed or is the parent of the displayed web screen, so
 * web screens can stack and return without reloading. Otherwise it is closed after the next client
 * tick, and reopened if the screen is shown again. A transparent screen draws its parent web screens
 * beneath itself. The page may call the bridge's {@code close()} to return to the parent.</p>
 */
public final class WebScreen extends Screen {

    private final WebSurface.Builder page;
    private final Screen parent;
    private final boolean pausesGame;
    private final boolean closeOnEscape;
    private final boolean background;
    private final SurfaceInput input = new SurfaceInput();
    private WebSurface surface;
    private boolean tracked;
    private boolean windowActive = true;

    private WebScreen(Builder builder, Screen parent, WebSurface.Builder page) {
        super(builder.title);
        this.page = page;
        this.parent = parent;
        this.pausesGame = builder.pausesGame;
        this.closeOnEscape = builder.closeOnEscape;
        this.background = builder.background;
    }

    public static Builder builder(URI address) {
        return new Builder(address);
    }

    @Override
    protected void init() {
        if (this.surface == null || this.surface.isClosed()) {
            this.surface = this.page.size(this.width, this.height).scale(HostSizing.scaleFor(this.width, this.height)).open();
            this.surface.onCloseRequest(this::onClose);
            if (!this.tracked) {
                this.tracked = true;
                HostTicker.track(this::isAlive);
            }
        }
        if (!this.surface.isClosed()) this.surface.setFocused(this.minecraft.isWindowActive());
    }

    @Override
    public void added() {
        // Minecraft calls added() before init(), so the screen's client field may still be unset.
        this.windowActive = Minecraft.getInstance().isWindowActive();
        if (this.isOpen()) this.surface.setFocused(this.windowActive);
    }

    @Override
    public void removed() {
        if (!this.isOpen()) return;
        this.input.reset(this.surface);
        this.surface.setFocused(false);
    }

    @Override
    public void tick() {
        boolean active = this.minecraft.isWindowActive();
        if (active == this.windowActive) return;
        this.windowActive = active;
        if (!this.isOpen()) return;
        this.surface.setFocused(active);
        if (!active) this.input.reset(this.surface);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.isOpen()) {
            this.drawPage(graphics, this.width, this.height);
            double x = this.pointerX();
            double y = this.pointerY();
            this.input.move(this.surface, x, y);
            this.input.updateCursor(this.surface, this.minecraft.screen == this, x, y);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.background) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        } else if (this.parent instanceof WebScreen web) {
            web.renderStack(graphics, this.width, this.height);
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        this.input.move(this.surface, x, y);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!this.isOpen()) return super.mouseClicked(x, y, button);
        this.surface.setFocused(true);
        return this.input.press(this.surface, x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return this.input.release(this.surface, x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        return this.input.drag(this.surface, x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return this.input.scroll(this.surface, x, y, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && this.closeOnEscape && !(this.isOpen() && this.surface.hasPopup())) {
            this.onClose();
            return true;
        }
        if (!this.isOpen()) return super.keyPressed(key, scanCode, modifiers);
        this.surface.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (!this.isOpen()) return false;
        this.surface.keyReleased(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!this.isOpen()) return false;
        this.surface.character(character, modifiers);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.closeOnEscape;
    }

    @Override
    public boolean isPauseScreen() {
        return this.pausesGame;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** The page, once the screen has been displayed; it stays readable after closing. */
    public Optional<WebSurface> surface() {
        return Optional.ofNullable(this.surface);
    }

    /** Sends an event to the page; false while it is closed or not connected. */
    public boolean emit(String event, Object data) {
        return this.isOpen() && this.surface.emit(event, data);
    }

    /** The screen shown when this one closes; null returns to the game. */
    public Screen parent() {
        return this.parent;
    }

    /** Whether the screen is displayed or an ancestor, through web screens, of the displayed screen. */
    static boolean isShowing(Screen screen) {
        for (Screen current = Minecraft.getInstance().screen; current != null;
             current = current instanceof WebScreen web ? web.parent : null) {
            if (current == screen) return true;
        }
        return false;
    }

    private void renderStack(GuiGraphics graphics, int width, int height) {
        if (!this.background && this.parent instanceof WebScreen web) web.renderStack(graphics, width, height);
        if (this.isOpen()) this.drawPage(graphics, width, height);
    }

    private void drawPage(GuiGraphics graphics, int width, int height) {
        HostSizing.fit(this.surface, width, height);
        this.input.setBounds(0, 0, width, height);
        this.surface.draw(graphics, 0, 0, width, height);
    }

    private boolean isAlive() {
        if (!this.isOpen()) {
            this.tracked = false;
            return false;
        }
        if (isShowing(this)) return true;
        this.input.reset(this.surface);
        this.surface.close();
        this.tracked = false;
        return false;
    }

    private boolean isOpen() {
        return this.surface != null && !this.surface.isClosed();
    }

    private double pointerX() {
        return this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
    }

    private double pointerY() {
        return this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
    }

    public static final class Builder extends WebBuilder<Builder> {

        private Component title = Component.empty();
        private boolean pausesGame;
        private boolean closeOnEscape = true;
        private boolean background;

        private Builder(URI address) {
            super(address);
        }

        /** Narrated screen title; defaults to empty. */
        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** Whether singleplayer pauses while the screen is open; defaults to false. */
        public Builder pausesGame(boolean pausesGame) {
            this.pausesGame = pausesGame;
            return this;
        }

        /** Whether Escape closes the screen when no page popup is open; defaults to true. */
        public Builder closeOnEscape(boolean closeOnEscape) {
            this.closeOnEscape = closeOnEscape;
            return this;
        }

        /** Whether Minecraft's blurred menu background is drawn beneath the page; defaults to false. */
        public Builder background(boolean background) {
            this.background = background;
            return this;
        }

        /** A screen that returns to the currently displayed screen when closed. */
        public WebScreen build() {
            return this.build(Minecraft.getInstance().screen);
        }

        /** A screen that returns to the given parent, or to the game for null, when closed. */
        public WebScreen build(Screen parent) {
            return new WebScreen(this, parent, this.surfaceBuilder());
        }

        /** Builds the screen over the displayed screen and displays it. */
        public WebScreen open() {
            WebScreen screen = this.build();
            Minecraft.getInstance().setScreen(screen);
            return screen;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
