package fr.lacaleche.glue.lumos;

import net.minecraft.world.level.Level;

/** Supplies a light transform once per rendered frame. Return false when the attachment is dead. */
@FunctionalInterface
public interface LightAttachment {

    boolean sample(Level level, float partialTick, LightTransform result);
}
