package fr.lacaleche.glue.testmod.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.camera.FpsCameraController;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Free-flight camera in an anchored block-and-entity preview; the real player stays in place. */
public final class FpsViewportTestScreen extends AbstractViewportScreen<FpsCameraController> {

    private final Screen parent;
    private final BlockSceneRenderer renderer;
    private float partialTick;

    public FpsViewportTestScreen() {
        this(Minecraft.getInstance().screen);
    }

    public FpsViewportTestScreen(Screen parent) {
        super(Component.literal("FPS scene"), createCamera());
        this.parent = parent;
        this.renderer = new BlockSceneRenderer() {
            @Override
            protected void renderExtras(Minecraft client, PoseStack matrices, BlockPos center,
                                        MultiBufferSource.BufferSource buffers) {
                FpsViewportTestScreen.this.renderEntities(client, matrices, center, buffers);
            }
        };
        this.renderer.setCenterPos(SceneTestAnchor.aroundPlayer(Minecraft.getInstance()));
    }

    @Override
    protected int renderSceneToTexture(float width, float height, Minecraft client, float tickDelta) {
        this.partialTick = tickDelta;
        this.renderer.setFov(this.cameraController.getFov());
        this.renderer.setViewMatrix(this.cameraController.buildViewMatrix());
        this.renderer.setScale(1.0f);
        return this.renderer.renderToTexture((int) width, (int) height, client);
    }

    private void renderEntities(Minecraft client, PoseStack matrices, BlockPos center,
                                MultiBufferSource.BufferSource buffers) {
        if (client.level == null) return;
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        int halfX = this.renderer.getHalfExtentX() + 1;
        int halfZ = this.renderer.getHalfExtentZ() + 1;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            double x = entity.getX() - center.getX();
            double y = entity.getY() - center.getY();
            double z = entity.getZ() - center.getZ();
            if (Math.abs(x) > halfX || Math.abs(z) > halfZ) continue;
            if (y < this.renderer.getMinY() - 1 || y > this.renderer.getMaxY() + 2) continue;
            dispatcher.render(entity, x, y, z, this.partialTick, matrices, buffers, LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch();
    }

    @Override
    protected void renderHud(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String controls = this.isCapturing()
                ? "WASD: Fly | Space/Shift: Up/down | Ctrl: Fast | Esc: Release"
                : "LMB: Capture | RMB: Pan | Wheel: Speed | Esc: Back";
        graphics.drawString(this.font, controls, 4, 4, 0xFFFFFFFF);
        Vec3 position = this.cameraController.getPosition();
        graphics.drawString(this.font, String.format(Locale.ROOT, "Scene: %.1f, %.1f, %.1f | Speed: %.3f",
                position.x, position.y, position.z, this.cameraController.getMoveSpeed()), 4, 16, 0xFFAAAAAA);
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

    private static FpsCameraController createCamera() {
        Minecraft client = Minecraft.getInstance();
        float yaw = client.player == null ? 0 : client.player.getYRot();
        float pitch = client.player == null ? 0 : client.player.getXRot();
        return new FpsCameraController(new Vec3(0.5, 1.7, 0.5), yaw, pitch);
    }
}
