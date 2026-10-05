package fr.lacaleche.glue.client.ui;

import fr.lacaleche.glue.client.render.gizmo.Gizmo;
import fr.lacaleche.glue.client.render.gizmo.GizmoView;
import fr.lacaleche.glue.client.render.gizmo.WorldGizmos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * One {@link UiPage} in a panel docked to the right edge, over a world that stays undimmed, keeps
 * running and keeps rendering: the pointer is free while the panel is open, and the game is not
 * paused. Descriptions show as tooltips. A Done button at the panel's foot closes the screen.
 *
 * <p>A click outside the panel goes to {@link #clickedOutside}, so a subclass can pick what is under
 * the pointer in the world. {@link #refresh} rebuilds the page in place, keeping its scroll, for a page
 * whose rows depend on state that changed.</p>
 *
 * <p>A {@link #setGizmo gizmo} is drawn in the world while the screen is open and takes the pointer
 * outside the panel first: a press on one of its handles drags it, Ctrl inverts its snapping, and a
 * right click or Escape cancels the drag.</p>
 */
public class UiPanelScreen extends Screen {

    private final UiPage page;
    private final UiPageHost host = new UiPageHost(this::addRenderableWidget, this::removeWidget);
    private ScreenRectangle panel = new ScreenRectangle(0, 0, 0, 0);
    private ScreenRectangle content = new ScreenRectangle(0, 0, 0, 0);
    private @Nullable Gizmo gizmo;

    public UiPanelScreen(Component title, UiPage page) {
        super(title);
        this.page = Objects.requireNonNull(page, "page");
    }

    /** The panel's width in GUI pixels: a row's label beside its control. */
    public static int panelWidth() {
        UiStyle style = UiStyle.DEFAULT;
        return style.descriptionWidth() + style.controlWidth();
    }

    @Override
    protected void init() {
        UiStyle style = UiStyle.DEFAULT;
        int padding = style.padding();
        int width = Math.min(panelWidth(), this.width - 2 * padding);
        this.panel = new ScreenRectangle(this.width - padding - width, padding, width, this.height - 2 * padding);
        int top = this.panel.top() + style.rowHeight();
        int doneTop = this.panel.bottom() - padding - style.controlHeight();
        this.content = new ScreenRectangle(this.panel.left(), top, width, Math.max(0, doneTop - padding - top));

        UiButton done = this.addRenderableWidget(new UiButton(CommonComponents.GUI_DONE, this::onClose));
        done.setRectangle(style.controlWidth(), style.controlHeight(), this.panel.right() - padding - style.controlWidth(),
                doneTop);
        this.host.show(this.page, this.content, true);
    }

    @Override
    protected void rebuildWidgets() {
        this.host.hide();
        super.rebuildWidgets();
    }

    @Override
    public void added() {
        if (this.gizmo != null) WorldGizmos.show(this.gizmo);
    }

    @Override
    public void removed() {
        this.host.hide();
        if (this.gizmo != null) {
            this.gizmo.cancel();
            WorldGizmos.hide(this.gizmo);
        }
    }

    /** The gizmo drawn in the world and handled while the screen is open, or null for none. */
    public @Nullable Gizmo gizmo() {
        return this.gizmo;
    }

    public void setGizmo(@Nullable Gizmo gizmo) {
        if (gizmo == this.gizmo) return;

        if (this.gizmo != null) {
            this.gizmo.cancel();
            WorldGizmos.hide(this.gizmo);
        }
        this.gizmo = gizmo;
        if (gizmo != null && this.minecraft != null && this.minecraft.screen == this) WorldGizmos.show(gizmo);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) this.renderPanorama(graphics, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoverGizmo(mouseX, mouseY);
        UiStyle style = UiStyle.DEFAULT;
        graphics.fill(this.panel.left(), this.panel.top(), this.panel.right(), this.panel.bottom(), style.panel());
        int padding = style.padding();
        graphics.drawString(this.font, this.title, this.panel.left() + padding,
                this.panel.top() + (style.rowHeight() - 8) / 2, style.text());
        super.render(graphics, mouseX, mouseY, partialTick);

        Component unavailable = this.host.unavailable();
        if (unavailable != null) {
            graphics.drawWordWrap(this.font, unavailable, this.content.left() + padding, this.content.top() + padding,
                    this.content.width() - 2 * padding, style.muted());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.gizmo != null && this.gizmo.isDragging()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) this.gizmo.cancel();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.panel.containsPoint((int) mouseX, (int) mouseY)) return false;

        this.setFocused(null);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.gizmo != null) {
            GizmoView view = GizmoView.world();
            if (view != null && this.gizmo.press(view, mouseX, mouseY)) return true;
        }
        return this.clickedOutside(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.gizmo != null && this.gizmo.isDragging()) {
            GizmoView view = GizmoView.world();
            if (view != null) this.gizmo.drag(view, mouseX, mouseY, hasControlDown());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.gizmo != null && this.gizmo.isDragging() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.gizmo.release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.gizmo != null && this.gizmo.isDragging()) {
            this.gizmo.cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Builds the page again in place, keeping its scroll and dropping its focus. */
    public void refresh() {
        UiRowList rows = this.host.rows();
        double scroll = rows != null ? rows.scrollAmount() : 0.0;
        this.host.show(this.page, this.content, true);
        UiRowList rebuilt = this.host.rows();
        if (rebuilt != null) rebuilt.setScrollAmount(scroll);
    }

    /** The panel's bounds in GUI pixels. */
    public ScreenRectangle panel() {
        return this.panel;
    }

    /**
     * Called for a click outside the panel that no widget took; returns whether it was handled. The
     * screen does nothing with it.
     *
     * @param mouseX the pointer in GUI pixels
     */
    protected boolean clickedOutside(double mouseX, double mouseY, int button) {
        return false;
    }

    private void hoverGizmo(int mouseX, int mouseY) {
        if (this.gizmo == null) return;

        GizmoView view = GizmoView.world();
        if (view == null || this.panel.containsPoint(mouseX, mouseY)) {
            this.gizmo.clearHover();
        } else {
            this.gizmo.hover(view, mouseX, mouseY);
        }
    }
}
