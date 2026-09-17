package fr.lacaleche.glue.web.host;

import fr.lacaleche.glue.web.WebBuilder;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.browser.BrowserSession;
import fr.lacaleche.glue.web.internal.host.HostSizing;
import fr.lacaleche.glue.web.internal.host.HostTicker;
import fr.lacaleche.glue.web.internal.host.SurfaceInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A page embedded in any screen as an ordinary widget. It takes keyboard input while focused and
 * pointer input over its rectangle.
 *
 * <p>The page opens when the widget is first rendered and belongs to the screen displayed at that
 * moment. It closes after the next client tick once that screen is neither displayed nor the parent
 * of the displayed web screen, or after five seconds without being rendered by its displayed screen.
 * Rendering again reopens it, except after the page closed by itself, for example after a runtime
 * failure. Create the widget once and add it again in {@code init()}. The page's {@code close()}
 * closes the owning screen. Escape stays with the screen.</p>
 */
public final class WebWidget extends AbstractWidget {

    private static final int UNRENDERED_TICKS = 100;

    private final WebSurface.Builder page;
    private final SurfaceInput input = new SurfaceInput();
    private WebSurface surface;
    private Screen owner;
    private int unrenderedTicks;
    private boolean tracked;
    private boolean released;

    private WebWidget(Builder builder, int x, int y, int width, int height, WebSurface.Builder page) {
        super(x, y, width, height, builder.title);
        this.page = page;
    }

    public static Builder builder(URI address) {
        return new Builder(address);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!this.ensureSurface(client)) return;

        this.unrenderedTicks = 0;
        HostSizing.fit(this.surface, this.getWidth(), this.getHeight());
        this.input.setBounds(this.getX(), this.getY(), this.getWidth(), this.getHeight());
        this.surface.draw(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());

        double x = client.mouseHandler.getScaledXPos(client.getWindow());
        double y = client.mouseHandler.getScaledYPos(client.getWindow());
        boolean ownsInput = client.screen == this.owner && this.active;
        if (ownsInput) {
            this.input.releaseStaleButtons(this.surface, x, y);
            this.input.move(this.surface, x, y);
        }
        this.input.updateCursor(this.surface, ownsInput, x, y);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!this.active || !this.visible || !this.isOpen()) return false;
        if (!this.input.press(this.surface, x, y, button)) return false;
        this.surface.setFocused(true);
        return true;
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
        return this.active && this.visible && this.input.scroll(this.surface, x, y, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!this.isFocused() || !this.isOpen()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && !this.surface.hasPopup()) return false;
        this.surface.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (!this.isFocused() || !this.isOpen()) return false;
        this.surface.keyReleased(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!this.isFocused() || !this.isOpen()) return false;
        this.surface.character(character, modifiers);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (this.isOpen()) this.surface.setFocused(focused);
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button >= 0 && button <= 2;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // Pages provide their own feedback.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    /** The page once rendered; it stays readable after closing. */
    public Optional<WebSurface> surface() {
        return Optional.ofNullable(this.surface);
    }

    /** Sends an event to the page; false while it is closed or not connected. */
    public boolean emit(String event, Object data) {
        return this.isOpen() && this.surface.emit(event, data);
    }

    /** Closes the page now; rendering the widget again opens a new one. */
    public void close() {
        if (!this.isOpen()) return;
        this.released = true;
        this.input.reset(this.surface);
        this.surface.close();
    }

    private boolean ensureSurface(Minecraft client) {
        if (this.isOpen()) return true;
        if (client.screen == null || BrowserSession.isRuntimeStopping()) return false;
        if (this.surface != null && !this.released) return false;

        this.owner = client.screen;
        this.released = false;
        this.surface = this.page.size(this.getWidth(), this.getHeight())
                .scale(HostSizing.scaleFor(this.getWidth(), this.getHeight())).open();
        this.surface.onCloseRequest(this.owner::onClose);
        this.surface.setFocused(this.isFocused());
        if (!this.tracked) {
            this.tracked = true;
            HostTicker.track(this::isAlive);
        }
        return true;
    }

    private boolean isAlive() {
        if (this.isOpen() && WebScreen.isShowing(this.owner)) {
            // Beneath a stacked web screen the owner is not rendered, so only a displayed owner counts.
            boolean displayed = Minecraft.getInstance().screen == this.owner;
            this.unrenderedTicks = displayed ? this.unrenderedTicks + 1 : 0;
            if (this.unrenderedTicks <= UNRENDERED_TICKS) return true;
        }
        this.close();
        this.tracked = false;
        return false;
    }

    private boolean isOpen() {
        return this.surface != null && !this.surface.isClosed();
    }

    public static final class Builder extends WebBuilder<Builder> {

        private Component title = Component.empty();

        private Builder(URI address) {
            super(address);
        }

        /** Narrated widget title; defaults to empty. */
        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** A widget at a GUI rectangle; the page is sized to it. */
        public WebWidget build(int x, int y, int width, int height) {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Widget sizes must be positive");
            return new WebWidget(this, x, y, width, height, this.surfaceBuilder());
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
