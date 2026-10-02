package fr.lacaleche.glue.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface DebugEvents {

    Event<BlockOutline> BLOCK_OUTLINE = EventFactory.createArrayBacked(
            BlockOutline.class,
            listeners -> (client, world, pos, blockState, camera, hitResult, matrices, buffers) -> {
                for (BlockOutline listener : listeners) {
                    listener.onRenderBlockOutline(client, world, pos, blockState, camera, hitResult, matrices, buffers);
                }
            });

    Event<F3Screen> F3_SCREEN_LEFT = EventFactory.createArrayBacked(
            F3Screen.class,
            listeners -> (client, list) -> {
                for (F3Screen listener : listeners) {
                    listener.onRenderF3(client, list);
                }
            });

    Event<F3Screen> F3_SCREEN_RIGHT = EventFactory.createArrayBacked(
            F3Screen.class,
            listeners -> (client, list) -> {
                for (F3Screen listener : listeners) {
                    listener.onRenderF3(client, list);
                }
            });

    Event<GuiDebugLayers> GUI_DEBUG_LAYERS = EventFactory.createArrayBacked(
            GuiDebugLayers.class,
            listeners -> (guiGraphics, tickDelta, screenWidth, screenHeight) -> {
                for (GuiDebugLayers listener : listeners) {
                    listener.onRenderGuiDebug(guiGraphics, tickDelta, screenWidth, screenHeight);
                }
            });

    Event<WorldDebug> WORLD_DEBUG = EventFactory.createArrayBacked(
            WorldDebug.class,
            listeners -> (matrices, vertexConsumers, cameraX, cameraY, cameraZ) -> {
                for (WorldDebug listener : listeners) {
                    listener.onRenderWorldDebug(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
                }
            });

    Event<ParticleSpawn> PARTICLE_SPAWN = EventFactory.createArrayBacked(
            ParticleSpawn.class,
            listeners -> (particle, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
                for (ParticleSpawn listener : listeners) {
                    listener.onParticleSpawn(particle, level, x, y, z, xSpeed, ySpeed, zSpeed);
                }
            });

    @FunctionalInterface
    interface BlockOutline {
        void onRenderBlockOutline(Minecraft client, Level world, BlockPos pos, BlockState blockState, Vec3 camera,
                                  HitResult hitResult, PoseStack matrices, MultiBufferSource buffers);
    }

    @FunctionalInterface
    interface F3Screen {
        void onRenderF3(Minecraft client, List<String> list);
    }

    @FunctionalInterface
    interface GuiDebugLayers {
        void onRenderGuiDebug(GuiGraphics guiGraphics, float tickDelta, int screenWidth, int screenHeight);
    }

    @FunctionalInterface
    interface WorldDebug {
        void onRenderWorldDebug(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                                double cameraZ);
    }

    @FunctionalInterface
    interface ParticleSpawn {
        void onParticleSpawn(Particle particle, ClientLevel level,
                             double x, double y, double z, double xSpeed, double ySpeed, double zSpeed);
    }
}
