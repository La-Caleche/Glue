package fr.lacaleche.glue.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public interface DrawSelectionEvents {

    Event<Block> BLOCK = EventFactory.createArrayBacked(Block.class, callbacks -> (client, world, camera, hitResult, matrix, buffers) -> {
        for (Block e : callbacks)
            if (e.onHighlightBlock(client, world, camera, hitResult, matrix, buffers))
                return true;
        return false;
    });

    @FunctionalInterface
    interface Block {
        boolean onHighlightBlock(Minecraft client, Level world, Vec3 camera, HitResult hitResult, PoseStack matrices, MultiBufferSource buffers);
    }

}
