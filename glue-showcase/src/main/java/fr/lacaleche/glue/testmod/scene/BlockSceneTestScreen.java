package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Orbit, zoom and pan around nearby blocks; region controls affect only this preview. */
public final class BlockSceneTestScreen extends AbstractViewportScreen<OrbitCameraController> {

    private final Screen parent;
    private final BlockSceneRenderer renderer = new BlockSceneRenderer();

    public BlockSceneTestScreen() {
        this(Minecraft.getInstance().screen);
    }

    public BlockSceneTestScreen(Screen parent) {
        super(Component.literal("Orbit scene"), new OrbitCameraController(new Vector3f()));
        this.parent = parent;
        this.renderer.setCenterPos(SceneTestAnchor.aroundPlayer(Minecraft.getInstance()));
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        // Framing, picking and rendering must use the same FOV.
        this.renderer.setFov(this.cameraController.getFov());
        this.renderer.setViewMatrix(this.cameraController.buildViewMatrix());
        this.renderer.setScale(1.0f);
        return this.renderer.renderToTexture((int) width, (int) height, client);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            this.renderer.setHalfExtentX(this.renderer.getHalfExtentX() + 1);
            this.renderer.setHalfExtentZ(this.renderer.getHalfExtentZ() + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            this.renderer.setHalfExtentX(Math.max(1, this.renderer.getHalfExtentX() - 1));
            this.renderer.setHalfExtentZ(Math.max(1, this.renderer.getHalfExtentZ() - 1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            this.renderer.setMaxY(this.renderer.getMaxY() + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.renderer.setMinY(this.renderer.getMinY() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            this.cameraController.reset();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void renderHud(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font, "LMB: Orbit | RMB: Pan | Wheel: Zoom | Home: Reset | Esc: Back", 4, 4, 0xFFFFFFFF);
        int dx = this.renderer.getHalfExtentX() * 2 + 1;
        int dz = this.renderer.getHalfExtentZ() * 2 + 1;
        int dy = this.renderer.getMaxY() - this.renderer.getMinY() + 1;
        graphics.drawString(this.font, "+/-: Region | PgUp/Dn: Height | " + dx + " x " + dy + " x " + dz
                + " (" + dx * dy * dz + " blocks)", 4, 16, 0xFFAAAAAA);
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
}
