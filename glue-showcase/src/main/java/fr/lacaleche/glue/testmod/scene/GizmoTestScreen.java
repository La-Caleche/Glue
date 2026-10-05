package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.GlfwGizmoController;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
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
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** Block picking, translate/rotate/scale gizmos, snap and undo/redo in an isolated scene preview. */
public final class GizmoTestScreen extends AbstractViewportScreen<OrbitCameraController> {

    private final Screen parent;
    private final SceneTestPreviewRenderer renderer;
    private final SceneTestController controller;
    private final GlfwGizmoController gizmo = new GlfwGizmoController();
    private UiRowList panel;

    public GizmoTestScreen() {
        this(Minecraft.getInstance().screen);
    }

    public GizmoTestScreen(Screen parent) {
        super(Component.literal("Gizmo scene"), new OrbitCameraController(new Vector3f()));
        this.parent = parent;
        Minecraft client = Minecraft.getInstance();
        this.gizmo.setWindowHandle(client.getWindow().getWindow());
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
                            this.gizmo::getCurrentOperation, this.gizmo::setOperation));
            Component space = Component.literal("Space");
            rows.row(space, Component.literal("Axes along the block or along the world. Tab switches."),
                    new UiCycle<>(space, List.of(GizmoSpace.values()), GizmoTestScreen::title,
                            this.gizmo::getCurrentMode, this.gizmo::setMode));
            Component snap = Component.literal("Snap");
            rows.row(snap, Component.literal("Moves by whole steps. G switches it."),
                    new UiToggle(snap, this.gizmo::isUsingSnap, this.gizmo::setUseSnap));

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.gizmo.updateMousePosition(mouseX, mouseY);
        this.gizmo.updateFrame();
        super.render(graphics, mouseX, mouseY, partialTick);
        this.gizmo.getBackend().render(graphics);
    }

    @Override
    protected void onRenderOverlay(float x, float y, float width, float height) {
        if (this.controller.getSelectedBlockPos() == null) return;
        float[] view = this.cameraController.buildViewMatrix().translate(-0.5f, -0.5f, -0.5f).get(new float[16]);
        float[] projection = new Matrix4f().setPerspective((float) Math.toRadians(this.cameraController.getFov()),
                width / height, 0.1f, 1000f).get(new float[16]);
        this.gizmo.manipulate(view, projection, x, y, width, height, !this.cameraController.isDragging());
        this.controller.updateGizmoInteraction();
        if (this.gizmo.isDragging()) this.controller.applyGizmoTransform();
    }

    @Override
    protected void onViewportClick(float x, float y, float width, float height, int button) {
        if (button == 0) this.controller.handleClick(x, y, width, height, this.cameraController, 1.0f);
    }

    @Override
    protected boolean isOverlayCapturingInput() {
        return this.controller.getSelectedBlockPos() != null && (this.gizmo.isHovered() || this.gizmo.isDragging());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_T) {
            this.gizmo.setOperation(GizmoOperation.TRANSLATE);
        } else if (key == GLFW.GLFW_KEY_R) {
            this.gizmo.setOperation(GizmoOperation.ROTATE);
        } else if (key == GLFW.GLFW_KEY_S && !hasControlDown()) {
            this.gizmo.setOperation(GizmoOperation.SCALE);
        } else if (key == GLFW.GLFW_KEY_TAB) {
            this.gizmo.setMode(this.gizmo.getCurrentMode() == GizmoSpace.LOCAL ? GizmoSpace.WORLD : GizmoSpace.LOCAL);
        } else if (key == GLFW.GLFW_KEY_G) {
            this.gizmo.setUseSnap(!this.gizmo.isUsingSnap());
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
}
