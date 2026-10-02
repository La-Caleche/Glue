package fr.lacaleche.glue.client.viewport;

import fr.lacaleche.glue.client.camera.AbstractCameraController;
import fr.lacaleche.glue.client.viewport.internal.BorrowedSceneTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft Screen-based viewport for rendering 3D scenes.
 * <p>
 * Handles camera interaction (drag, scroll, FPS capture) and renders the
 * scene texture as a fullscreen quad. Subclasses provide the scene via
 * {@link #renderSceneToTexture} and optionally overlay content via
 * {@link #onRenderOverlay}.
 *
 * @param <C> the concrete camera controller passed to the constructor; {@link #cameraController}
 *            and {@link #getCameraController()} keep that type, so subclasses never downcast
 */
public abstract class AbstractViewportScreen<C extends AbstractCameraController> extends Screen {

    private static final ResourceLocation SCENE_TEXTURE_LOC =
            ResourceLocation.fromNamespaceAndPath("glue", "scene_viewport_tex");

    protected final C cameraController;

    private final BorrowedSceneTexture sceneTexture = new BorrowedSceneTexture(SCENE_TEXTURE_LOC);
    private boolean isCapturing = false;
    private int sceneTextureId = -1;
    private double dragDistance;

    protected AbstractViewportScreen(Component title, C cameraController) {
        super(title);
        this.cameraController = cameraController;
    }

    /**
     * Subclasses implement this to render the 3D scene into an FBO and return its texture ID.
     *
     * @param width     Viewport width in pixels.
     * @param height    Viewport height in pixels.
     * @param client    Minecraft client instance.
     * @param tickDelta Partial tick delta.
     * @return The OpenGL texture ID of the rendered scene, or -1 on failure.
     */
    protected abstract int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta);

    /**
     * Called after the scene texture is drawn, for rendering overlays (e.g. gizmos).
     * Coordinates are in GUI-scaled screen space.
     */
    protected void onRenderOverlay(float contentX, float contentY, float width, float height) {
    }

    /**
     * Called when the viewport is clicked and the mouse did not drag.
     *
     * @param localX Local X within the viewport (0 = left edge).
     * @param localY Local Y within the viewport (0 = top edge).
     * @param button Mouse button (0 = left, 1 = right, 2 = middle).
     */
    protected void onViewportClick(float localX, float localY, float width, float height, int button) {
    }

    /**
     * Returns true when an overlay (e.g. gizmo) is handling input, blocking camera interaction.
     */
    protected boolean isOverlayCapturingInput() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();

        if (isCapturing) {
            cameraController.processCapturedInput(client.getWindow().getWindow());
        }

        int framebufferWidth = client.getWindow().getWidth();
        int framebufferHeight = client.getWindow().getHeight();
        sceneTextureId = renderSceneToTexture(framebufferWidth, framebufferHeight, client, partialTick);

        if (sceneTextureId > 0) {
            this.sceneTexture.update(client, sceneTextureId, framebufferWidth, framebufferHeight);
            guiGraphics.blit(SCENE_TEXTURE_LOC,
                    0, 0, this.width, this.height,
                    0.0f, 1.0f, 1.0f, 0.0f);
        }

        onRenderOverlay(0, 0, this.width, this.height);
        renderHud(guiGraphics, mouseX, mouseY, partialTick);
    }

    protected void renderHud(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverlayCapturingInput()) return true;

        if (cameraController.capturesMouseOnClick() && button == 0) {
            startCapture();
            return true;
        }

        dragDistance = 0;
        cameraController.startDrag((float) mouseX, (float) mouseY, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (cameraController.isDragging()) {
            dragDistance += Math.sqrt(dragX * dragX + dragY * dragY);
            cameraController.updateDrag((float) mouseX, (float) mouseY, this.height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cameraController.isDragging() && cameraController.getDragButton() == button) {
            cameraController.stopDrag();
            if (dragDistance < 5.0) {
                onViewportClick((float) mouseX, (float) mouseY, this.width, this.height, button);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        cameraController.handleScroll((float) verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isCapturing) {
            stopCapture();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void startCapture() {
        isCapturing = true;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    private void stopCapture() {
        isCapturing = false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        cameraController.cleanup();
    }

    @Override
    public void removed() {
        super.removed();
        if (isCapturing) stopCapture();
        this.sceneTexture.release(Minecraft.getInstance());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public C getCameraController() {
        return cameraController;
    }

}
