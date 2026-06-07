package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.GlfwGizmoController;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Test screen demonstrating the full gizmo system:
 * block selection via raycasting, gizmo manipulation (translate/rotate/scale),
 * undo/redo history, and snap.
 */
public class GizmoTestScreen extends AbstractViewportScreen {

    private static final float RENDER_SCALE = 1.0f;

    private final SceneTestPreviewRenderer renderer;
    private final SceneTestController controller;
    private final GlfwGizmoController gizmoController;
    private final OrbitCameraController orbitCamera;

    public GizmoTestScreen() {
        super(Component.literal("Gizmo Test"), new OrbitCameraController(new Vector3f(0, 0, 0)));
        this.orbitCamera = (OrbitCameraController) cameraController;
        this.renderer = new SceneTestPreviewRenderer();

        Minecraft client = Minecraft.getInstance();
        this.gizmoController = new GlfwGizmoController();
        this.gizmoController.setWindowHandle(client.getWindow().getWindow());

        this.controller = new SceneTestController(
                client.player != null ? client.player.getOnPos() : new net.minecraft.core.BlockPos(0, 0, 0),
                this.gizmoController);
        this.renderer.setSceneController(controller);
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        renderer.setFov(orbitCamera.getFov());
        renderer.setViewMatrix(orbitCamera.buildViewMatrix());
        renderer.setScale(RENDER_SCALE);
        return renderer.renderToTexture((int) width, (int) height, client);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        gizmoController.updateMousePosition(mouseX, mouseY);
        gizmoController.updateFrame();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        gizmoController.getBackend().render(guiGraphics);
    }

    @Override
    protected void onRenderOverlay(float contentX, float contentY, float width, float height) {
        if (controller.getSelectedBlockPos() == null) return;

        float[] viewMatrix = new float[16];
        Matrix4f gizmoView = orbitCamera.buildViewMatrix();
        gizmoView.scale(RENDER_SCALE);
        gizmoView.translate(-0.5f, -0.5f, -0.5f);
        gizmoView.get(viewMatrix);

        float[] projectionMatrix = new float[16];
        new Matrix4f().setPerspective(
                (float) Math.toRadians(orbitCamera.getFov()),
                width / height, 0.1f, 1000.0f)
                .get(projectionMatrix);

        gizmoController.manipulate(viewMatrix, projectionMatrix,
                contentX, contentY, width, height,
                !cameraController.isDragging());

        controller.updateGizmoInteraction();

        if (gizmoController.isDragging()) {
            controller.applyGizmoTransform();
        }
    }

    @Override
    protected void onViewportClick(float localX, float localY, float width, float height, int button) {
        if (button == 0) {
            controller.handleClick(localX, localY, width, height, orbitCamera, RENDER_SCALE);
        }
    }

    @Override
    protected boolean isOverlayCapturingInput() {
        return gizmoController.isHovered() || gizmoController.isDragging();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Gizmo operation
        if (keyCode == GLFW.GLFW_KEY_T) {
            gizmoController.setOperation(GizmoOperation.TRANSLATE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            gizmoController.setOperation(GizmoOperation.ROTATE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && !hasControlDown()) {
            gizmoController.setOperation(GizmoOperation.SCALE);
            return true;
        }
        // Toggle local/world space
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            gizmoController.setMode(gizmoController.getCurrentMode() == GizmoSpace.LOCAL
                    ? GizmoSpace.WORLD : GizmoSpace.LOCAL);
            return true;
        }
        // Toggle snap
        if (keyCode == GLFW.GLFW_KEY_G) {
            gizmoController.setUseSnap(!gizmoController.isUsingSnap());
            return true;
        }
        // Reset camera
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            orbitCamera.reset();
            return true;
        }
        // Undo / redo
        if (keyCode == GLFW.GLFW_KEY_Z && hasControlDown()) {
            if (hasShiftDown()) controller.getHistoryManager().redo();
            else controller.getHistoryManager().undo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Y && hasControlDown()) {
            controller.getHistoryManager().redo();
            return true;
        }
        // Deselect
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            controller.clearSelectedBlock();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderHud(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        String opName = switch (gizmoController.getCurrentOperation()) {
            case TRANSLATE -> "Translate";
            case ROTATE -> "Rotate";
            case SCALE -> "Scale";
        };
        String spaceName = gizmoController.getCurrentMode() == GizmoSpace.LOCAL ? "Local" : "World";
        String snapState = gizmoController.isUsingSnap() ? "ON" : "OFF";

        guiGraphics.drawString(font,
                "LMB: Select/Rotate  RMB: Pan  Scroll: Zoom  T/R/S: " + opName
                        + "  Tab: " + spaceName + "  G: Snap " + snapState,
                4, 4, 0xFFFFFFFF);

        boolean canUndo = controller.getHistoryManager().canUndo();
        boolean canRedo = controller.getHistoryManager().canRedo();
        guiGraphics.drawString(font,
                "Ctrl+Z: Undo  Ctrl+Y: Redo  Del: Deselect  Home: Reset cam  ESC: Close",
                4, 14, canUndo ? 0xFFAAAAAA : 0x55AAAAAA);

        guiGraphics.drawString(font,
                String.format("Undo: %s  Redo: %s  Zoom: %.1f",
                        canUndo ? "available" : "—",
                        canRedo ? "available" : "—",
                        orbitCamera.getZoom()),
                4, 24, 0xFF888888);

        if (controller.getSelectedBlockPos() != null) {
            guiGraphics.drawString(font,
                    "Selected: " + controller.getSelectedBlockPos().toShortString(),
                    4, this.height - 12, 0xFF00FF00);
        }
    }

    @Override
    public void removed() {
        super.removed();
        renderer.cleanup();
    }
}
