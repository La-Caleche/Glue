package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/**
 * Test screen demonstrating a block scene viewport with orbit camera.
 * Renders blocks around the player's position with full rotation/zoom/pan controls.
 * Use +/- keys to adjust the rendered region, R to reset the camera.
 */
public class BlockSceneTestScreen extends AbstractViewportScreen {

    private static final float RENDER_SCALE = 1.0f;

    private final BlockSceneRenderer renderer;
    private final OrbitCameraController orbitCamera;

    public BlockSceneTestScreen() {
        super(Component.literal("Block Scene Test"),
                new OrbitCameraController(new Vector3f(0, 0, 0)));
        this.orbitCamera = (OrbitCameraController) cameraController;
        this.renderer = new BlockSceneRenderer();
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        renderer.setViewMatrix(orbitCamera.buildViewMatrix());
        renderer.setScale(RENDER_SCALE);
        return renderer.renderToTexture((int) width, (int) height, client);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Expand/shrink horizontal extent
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) {
            renderer.setHalfExtentX(renderer.getHalfExtentX() + 1);
            renderer.setHalfExtentZ(renderer.getHalfExtentZ() + 1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
            renderer.setHalfExtentX(Math.max(1, renderer.getHalfExtentX() - 1));
            renderer.setHalfExtentZ(Math.max(1, renderer.getHalfExtentZ() - 1));
            return true;
        }
        // Expand/shrink vertical extent
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            renderer.setMaxY(renderer.getMaxY() + 1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            renderer.setMinY(renderer.getMinY() - 1);
            return true;
        }
        // Reset camera
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            orbitCamera.reset();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderHud(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.drawString(font,
                "LMB: Rotate  RMB: Pan  Scroll: Zoom  +/-: Region  PgUp/Dn: Height  Home: Reset  ESC: Close",
                4, 4, 0xFFFFFFFF);

        int dx = renderer.getHalfExtentX() * 2 + 1;
        int dz = renderer.getHalfExtentZ() * 2 + 1;
        int dy = renderer.getMaxY() - renderer.getMinY() + 1;
        int blockCount = dx * dz * dy;
        guiGraphics.drawString(font,
                String.format("Region: %dx%dx%d  (%d blocks)  Zoom: %.1f",
                        dx, dy, dz, blockCount, orbitCamera.getZoom()),
                4, 14, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        super.removed();
        renderer.cleanup();
    }
}
