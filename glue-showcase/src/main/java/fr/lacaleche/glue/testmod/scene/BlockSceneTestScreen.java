package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.ui.UiButton;
import fr.lacaleche.glue.client.ui.UiLabel;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiSlider;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Orbit, zoom and pan around nearby blocks; region controls affect only this preview. */
public final class BlockSceneTestScreen extends AbstractViewportScreen<OrbitCameraController> {

    private final Screen parent;
    private final BlockSceneRenderer renderer = new BlockSceneRenderer();
    private UiRowList panel;

    public BlockSceneTestScreen() {
        this(Minecraft.getInstance().screen);
    }

    public BlockSceneTestScreen(Screen parent) {
        super(Component.literal("Orbit scene"), new OrbitCameraController(new Vector3f()));
        this.parent = parent;
        this.renderer.setCenterPos(SceneTestAnchor.aroundPlayer(Minecraft.getInstance()));
    }

    @Override
    protected void init() {
        this.panel = this.addRenderableWidget(ScenePanel.create(this.width, this.height, rows -> {
            rows.section(Component.literal("Region"));
            this.slider(rows, "Width", "Blocks across, on X. + and - change width and depth together.", 1, 16,
                    this.renderer::getHalfExtentX, value -> this.renderer.setHalfExtentX((int) value))
                    .setFormat(value -> Component.literal(Integer.toString((int) value * 2 + 1)));
            this.slider(rows, "Depth", "Blocks across, on Z.", 1, 16,
                    this.renderer::getHalfExtentZ, value -> this.renderer.setHalfExtentZ((int) value))
                    .setFormat(value -> Component.literal(Integer.toString((int) value * 2 + 1)));
            this.slider(rows, "Bottom", "The lowest layer, from the centre. Page Down lowers it.", -32, 0,
                    this.renderer::getMinY, value -> this.renderer.setMinY((int) value));
            this.slider(rows, "Top", "The highest layer, from the centre. Page Up raises it.", 0, 32,
                    this.renderer::getMaxY, value -> this.renderer.setMaxY((int) value));
            rows.row(Component.literal("Blocks"), null, new UiLabel(Component.literal("Blocks"),
                    () -> Component.literal(Integer.toString(this.blockCount()))));
            rows.section(Component.literal("Camera"));
            rows.row(Component.literal("View"), Component.literal("Back to the default orbit. Home does the same."),
                    new UiButton(Component.literal("Reset"), this.cameraController::reset));
        }));
    }

    private UiSlider slider(UiRowList rows, String label, String description, int min, int max, DoubleSupplier value,
            DoubleConsumer onChange) {
        Component name = Component.literal(label);
        return rows.row(name, Component.literal(description), new UiSlider(name, min, max, 1, value, onChange));
    }

    private int blockCount() {
        int dx = this.renderer.getHalfExtentX() * 2 + 1;
        int dz = this.renderer.getHalfExtentZ() * 2 + 1;
        return dx * dz * (this.renderer.getMaxY() - this.renderer.getMinY() + 1);
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
        ScenePanel.renderHud(graphics, this.font, this.panel, "LMB: Orbit | RMB: Pan | Wheel: Zoom | Esc: Back");
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
