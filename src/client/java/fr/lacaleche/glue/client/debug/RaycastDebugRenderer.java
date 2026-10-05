package fr.lacaleche.glue.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.block.GeometryRaycast;
import fr.lacaleche.glue.client.debug.internal.RaycastProbe;
import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.BOX;
import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.MARKER;

/**
 * Shows how the camera's pick is decided: the blocks reaching past their cells that it tested, and
 * vanilla's hit next to the cell hit and the final one from {@link GeometryRaycast#trace}. Glue registers
 * one, which the developer menu's Raycast page switches.
 */
public class RaycastDebugRenderer extends GlueDebugRenderer {

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                       double cameraZ) {
        if (!this.enabled)
            return;
        RaycastProbe probe = RaycastProbe.probe();
        if (probe == null)
            return;

        GeometryRaycast.Trace trace = probe.trace();
        for (BlockPos pos : trace.reaching()) {
            this.addDebugElement(pos, builder -> builder.type(BOX).color(Color.ofRGBA(200, 200, 200, 255)));
        }
        if (trace.result().getType() != HitResult.Type.MISS)
            this.addDebugElement(trace.result().getBlockPos(),
                    builder -> builder.type(MARKER).color(255, 100, 100).message("Hit result").duration(25));

        super.renderElements(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
    }

    @Override
    public void renderHud(GuiGraphics context) {
        if (!this.enabled)
            return;
        RaycastProbe probe = RaycastProbe.probe();
        if (probe == null)
            return;

        Level level = probe.level();
        Vec3 origin = probe.origin();

        List<String> texts = new ArrayList<>();
        texts.add("Raycast debug");
        texts.add("");
        texts.add("Origin: " + BlockPos.containing(origin).toShortString());
        texts.add("Target: " + BlockPos.containing(probe.target()).toShortString());
        texts.add("Max Range: " + RaycastProbe.MAX_DISTANCE);
        texts.add("");

        if (!probe.trace().reaching().isEmpty()) {
            texts.add("Reaching past their cells:");
            for (BlockPos pos : probe.trace().reaching()) {
                texts.add(pos.toShortString() + ": " + level.getBlockState(pos));
            }
            texts.add("");
        }

        resultText(texts, "Vanilla", probe.vanilla(), level, origin);
        resultText(texts, "Cells", probe.trace().cell(), level, origin);
        resultText(texts, "Final", probe.trace().result(), level, origin);
        texts.add("Final matches vanilla: " + probe.matchesVanilla());

        this.drawRight(context, texts, Minecraft.getInstance());
    }

    private static void resultText(List<String> texts, String name, @Nullable BlockHitResult result, Level level, Vec3 origin) {
        if (result == null || result.getType() == HitResult.Type.MISS) {
            texts.add(name + ": MISS");
        } else {
            texts.add(name + " Pos: " + result.getBlockPos().toShortString() + " " + result.getDirection());
            texts.add(name + " State: " + level.getBlockState(result.getBlockPos()));
            texts.add(name + " Distance: " + String.format("%.2f", result.getLocation().distanceTo(origin)));
        }
        texts.add("");
    }
}
