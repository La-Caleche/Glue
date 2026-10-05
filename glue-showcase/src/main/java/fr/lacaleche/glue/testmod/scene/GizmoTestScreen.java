package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.Gizmo;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.gizmo.GizmoView;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.ui.UiButton;
import fr.lacaleche.glue.client.ui.UiCycle;
import fr.lacaleche.glue.client.ui.UiLabel;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiToggle;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** Block picking, a translate, rotate and scale gizmo, snap and undo/redo in an isolated scene preview. */
public final class GizmoTestScreen extends AbstractViewportScreen<OrbitCameraController> {

    private final Screen parent;
    private final SceneTestPreviewRenderer renderer;
    private final SceneTestController controller;
    private final Gizmo gizmo = new Gizmo();
    private UiRowList panel;

    public GizmoTestScreen() {
        this(Minecraft.getInstance().screen);
    }

    public GizmoTestScreen(Screen parent) {
        super(Component.literal("Gizmo scene"), new OrbitCameraController(new Vector3f()));
        this.parent = parent;
        Minecraft client = Minecraft.getInstance();
        BlockPos center = SceneTestAnchor.aroundPlayer(client);
        this.controller = new SceneTestController(center, this.gizmo);
        this.renderer = new SceneTestPreviewRenderer(this.controller);
        this.renderer.setCenterPos(center);
    }

    @Override
    protected void init() {
        this.panel = this.addRenderableWidget(ScenePanel.create(this.width, this.height, rows -> {
            rows.section(Component.literal("Gizmo"));
            Component operation = Component.literal("Operation");
            rows.row(operation, Component.literal("What dragging the gizmo does. T, R and S pick one."),
                    new UiCycle<>(operation, List.of(GizmoOperation.values()), GizmoTestScreen::title,
                            this.gizmo::operation, this.gizmo::setOperation));
            Component space = Component.literal("Space");
            rows.row(space, Component.literal("Axes along the block or along the world. Tab switches."),
                    new UiCycle<>(space, List.of(GizmoSpace.values()), GizmoTestScreen::title,
                            this.gizmo::space, this.gizmo::setSpace));
            Component snap = Component.literal("Snap");
            rows.row(snap, Component.literal("Moves by whole steps. G switches it, Ctrl inverts it while dragging."),
                    new UiToggle(snap, this.gizmo::isSnapping, this.gizmo::setSnapping));

            rows.section(Component.literal("Selection"));
            Component block = Component.literal("Block");
            rows.row(block, Component.literal("A left click in the preview picks a block."), new UiLabel(block,
                    () -> this.controller.getSelectedBlockPos() == null ? Component.literal("None")
                            : Component.literal(this.controller.getSelectedBlockPos().toShortString())));
            this.button(rows, "Selection", "Drops the selection. Delete does the same.", "Clear",
                    this.controller::clearSelectedBlock);

            rows.section(Component.literal("History"));
            this.button(rows, "Undo", "Steps back one drag. Ctrl+Z does the same.", "Undo", this.controller::undo);
            this.button(rows, "Redo", "Replays an undone drag. Ctrl+Y does the same.", "Redo", this.controller::redo);

            rows.section(Component.literal("Camera"));
            this.button(rows, "View", "Back to the default orbit. Home does the same.", "Reset",
                    this.cameraController::reset);
        }));
    }

    private void button(UiRowList rows, String label, String description, String text, Runnable action) {
        rows.row(Component.literal(label), Component.literal(description),
                new UiButton(Component.literal(text), action));
    }

    private static Component title(Enum<?> value) {
        String name = value.name();
        return Component.literal(name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT));
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        this.renderer.setFov(this.cameraController.getFov());
        this.renderer.setViewMatrix(this.cameraController.buildViewMatrix());
        this.renderer.setScale(1.0f);
        return this.renderer.renderToTexture((int) width, (int) height, client);
    }

    /** The preview's camera; a block's translation is its centre, half a block off the renderer's corner. */
    public GizmoView getGizmoView() {
        Matrix4f view = this.cameraController.buildViewMatrix().translate(-0.5f, -0.5f, -0.5f);
        Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(this.cameraController.getFov()),
                (float) this.width / this.height, 0.1f, 1000f);
        return new GizmoView(new Vector3d(), view, projection, 0, 0, this.width, this.height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.cameraController.isDragging() || this.panel.isMouseOver(mouseX, mouseY)) {
            this.gizmo.clearHover();
        } else {
            this.gizmo.hover(this.getGizmoView(), mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.gizmo.isDragging()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) this.gizmo.cancel();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !this.panel.isMouseOver(mouseX, mouseY)
                && this.gizmo.press(this.getGizmoView(), mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.gizmo.isDragging()) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        this.gizmo.drag(this.getGizmoView(), mouseX, mouseY, hasControlDown());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.gizmo.isDragging() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        this.gizmo.release();
        return true;
    }

    @Override
    protected void onViewportClick(float x, float y, float width, float height, int button) {
        if (button == 0) this.controller.handleClick(x, y, width, height, this.cameraController, 1.0f);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && this.gizmo.isDragging()) {
            this.gizmo.cancel();
        } else if (key == GLFW.GLFW_KEY_T) {
            this.gizmo.setOperation(GizmoOperation.TRANSLATE);
        } else if (key == GLFW.GLFW_KEY_R) {
            this.gizmo.setOperation(GizmoOperation.ROTATE);
        } else if (key == GLFW.GLFW_KEY_S && !hasControlDown()) {
            this.gizmo.setOperation(GizmoOperation.SCALE);
        } else if (key == GLFW.GLFW_KEY_TAB) {
            this.gizmo.setSpace(this.gizmo.space() == GizmoSpace.LOCAL ? GizmoSpace.WORLD : GizmoSpace.LOCAL);
        } else if (key == GLFW.GLFW_KEY_G) {
            this.gizmo.setSnapping(!this.gizmo.isSnapping());
        } else if (key == GLFW.GLFW_KEY_HOME) {
            this.cameraController.reset();
        } else if (key == GLFW.GLFW_KEY_Z && hasControlDown()) {
            if (hasShiftDown()) this.controller.redo();
            else this.controller.undo();
        } else if (key == GLFW.GLFW_KEY_Y && hasControlDown()) {
            this.controller.redo();
        } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            this.controller.clearSelectedBlock();
        } else {
            return super.keyPressed(key, scanCode, modifiers);
        }
        return true;
    }

    @Override
    protected void renderHud(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.gizmo.render(graphics, this.getGizmoView());
        ScenePanel.renderHud(graphics, this.font, this.panel, "LMB: Pick/orbit | RMB: Pan | Wheel: Zoom | Esc: Back");
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        try {
            super.removed();
        } finally {
            this.renderer.cleanup();
        }
    }

    /** Borrowed renderer, owned and cleaned up by this screen. */
    public BlockSceneRenderer getSceneRenderer() {
        return this.renderer;
    }

    public SceneTestController getSceneController() {
        return this.controller;
    }

    public Gizmo getGizmo() {
        return this.gizmo;
    }
}
