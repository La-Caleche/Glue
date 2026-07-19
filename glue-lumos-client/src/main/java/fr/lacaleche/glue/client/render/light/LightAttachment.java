package fr.lacaleche.glue.client.render.light;

import net.minecraft.client.multiplayer.ClientLevel;

/** Supplies a light transform once per rendered frame. Return false when the attachment is dead. */
@FunctionalInterface
public interface LightAttachment {

    boolean sample(ClientLevel level, float partialTick, LightTransform result);
}
