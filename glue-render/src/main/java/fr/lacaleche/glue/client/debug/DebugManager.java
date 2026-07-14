package fr.lacaleche.glue.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.events.RenderEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.List;

public class DebugManager {

    private static final DebugManager INSTANCE = new DebugManager();
    private final List<GlueDebugRenderer> renderers = new ArrayList<>();

    private DebugManager() {
        RenderEvents.RENDER_HUD.register(this::renderHudAll);
    }

    public static DebugManager getInstance() {
        return INSTANCE;
    }

    public void register(GlueDebugRenderer renderer) {
        this.renderers.add(renderer);
    }

    public void renderAll(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                          double cameraZ) {
        for (GlueDebugRenderer renderer : this.renderers) {
            renderer.render(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
        }
    }

    public void renderHudAll(GuiGraphics context) {
        for (GlueDebugRenderer renderer : this.renderers) {
            renderer.renderHud(context);
        }
    }
}
