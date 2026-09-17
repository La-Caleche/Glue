package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.GlfwGizmoController;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Block picking, translate/rotate/scale gizmos, snap and undo/redo in an isolated scene preview. */
public final class GizmoTestScreen extends AbstractViewportScreen<OrbitCameraController> {

    private final Screen parent;
    private final SceneTestPreviewRenderer renderer;
    private final SceneTestController controller;
    private final GlfwGizmoController gizmo = new GlfwGizmoController();

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
        graphics.drawString(this.font, "LMB: Pick/orbit | RMB: Pan | Wheel: Zoom | T/R/S: Operation", 4, 4, 0xFFFFFFFF);
        graphics.drawString(this.font, "Tab: Space | G: Snap | Ctrl+Z/Y: Undo/redo",
                4, 16, 0xFFAAAAAA);
        graphics.drawString(this.font, "Del: Clear | Home: Reset | Esc: Back", 4, 28, 0xFFAAAAAA);
        graphics.drawString(this.font, this.gizmo.getCurrentOperation() + " | " + this.gizmo.getCurrentMode()
                + " | Snap: " + (this.gizmo.isUsingSnap() ? "ON" : "OFF")
                + " | Undo: " + this.controller.getHistoryManager().canUndo()
                + " | Redo: " + this.controller.getHistoryManager().canRedo(), 4, 40, 0xFFAAAAAA);
        if (this.controller.getSelectedBlockPos() != null) {
            graphics.drawString(this.font, "Selected: " + this.controller.getSelectedBlockPos().toShortString(),
                    4, this.height - 12, 0xFF8CC63F);
        }
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
