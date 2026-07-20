package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.camera.FpsCameraController;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Test screen for {@link FpsCameraController}.
 * Exercises: mouse capture, WASD fly movement, scroll speed, and the cleanup lifecycle.
 *
 * <p>Coordinate model: the scene is rendered in <em>scene space</em> — a coordinate system
 * where (0, 0, 0) is the player's foot position at screen open time. The camera starts at
 * eye height (0, 1.7, 0) in scene space and moves freely within it via WASD. The block region
 * is anchored to the player's real world position and never changes during flight.
 */
public class FpsViewportTestScreen extends AbstractViewportScreen {

    private static final float RENDER_SCALE = 1.0f;

    private final BlockSceneRenderer renderer;
    private final FpsCameraController fpsCamera;

    public FpsViewportTestScreen() {
        this(createCamera());
    }

    private FpsViewportTestScreen(FpsCameraController cam) {
        super(Component.literal("FPS Scene"), cam);
        this.fpsCamera = cam;

        // Anchor the rendered region to the player's real world position — never updated during flight.
        Minecraft mc = Minecraft.getInstance();
        BlockPos center = mc.player != null ? mc.player.getOnPos() : BlockPos.ZERO;

        this.renderer = new BlockSceneRenderer() {
            @Override
            protected void renderExtras(Minecraft client, PoseStack matrices, BlockPos sceneCenter,
                                        MultiBufferSource.BufferSource bufferSource) {
                renderEntities(client, matrices, sceneCenter, bufferSource);
            }
        };
        this.renderer.setCenterPos(center);
    }

    /**
     * Camera starts in scene coordinates at eye height facing the player's look direction.
     * Scene space: origin = player's foot block, Y+ = up, units = Minecraft blocks.
     */
    private static FpsCameraController createCamera() {
        Minecraft mc = Minecraft.getInstance();
        float yaw   = mc.player != null ? mc.player.getYRot()  : 0f;
        float pitch = mc.player != null ? mc.player.getXRot()  : 0f;
        // (0.5, 1.7, 0.5) = centre of the foot block horizontally, standard eye height
        return new FpsCameraController(new Vec3(0.5, 1.7, 0.5), yaw, pitch);
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        renderer.setViewMatrix(fpsCamera.buildViewMatrix());
        renderer.setScale(RENDER_SCALE);
        return renderer.renderToTexture((int) width, (int) height, client);
    }

    private void renderEntities(Minecraft client, PoseStack matrices, BlockPos center,
                                MultiBufferSource.BufferSource bufferSource) {
        if (client.level == null) return;
        var dispatcher = client.getEntityRenderDispatcher();
        int hx = renderer.getHalfExtentX() + 1;
        int hz = renderer.getHalfExtentZ() + 1;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue; // skip self (first-person view)

            double relX = entity.getX() - center.getX();
            double relY = entity.getY() - center.getY();
            double relZ = entity.getZ() - center.getZ();

            if (Math.abs(relX) > hx) continue;
            if (relY < renderer.getMinY() - 1 || relY > renderer.getMaxY() + 2) continue;
            if (Math.abs(relZ) > hz) continue;

            dispatcher.render(entity, relX, relY, relZ,
                    entity.getYRot(), matrices, bufferSource, LightTexture.FULL_BRIGHT);
        }
        bufferSource.endBatch();
    }

    @Override
    protected void renderHud(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        if (isCapturing()) {
            guiGraphics.drawString(font,
                    "WASD: Move  Space/Shift: Up/Down  Ctrl: Sprint  Scroll: Speed  ESC: Release",
                    4, 4, 0xFFFFFFFF);
            Vec3 pos = fpsCamera.getPosition();
            BlockPos wc = renderer.getCenterPos();
            guiGraphics.drawString(font,
                    String.format("Scene: %.1f, %.1f, %.1f  |  World center: %s  |  Speed: %.3f",
                            pos.x, pos.y, pos.z,
                            wc != null ? wc.toShortString() : "?",
                            fpsCamera.getMoveSpeed()),
                    4, 14, 0xFFAAAAAA);
        } else {
            guiGraphics.drawString(font,
                    "LMB: Capture mouse  |  RMB: Pan  |  Scroll: Adjust speed  |  ESC: Close",
                    4, 4, 0xFFFFFFFF);
            guiGraphics.drawString(font,
                    "Speed: " + String.format("%.3f", fpsCamera.getMoveSpeed()),
                    4, 14, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        super.removed();
        renderer.cleanup();
    }
}
