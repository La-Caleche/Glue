package fr.lacaleche.glue.lumos;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Supplies a light transform once per rendered frame. Return false when the attachment is dead. */
@FunctionalInterface
public interface LightAttachment {

    boolean sample(Level level, float partialTick, LightTransform result);

    /**
     * The entity this attachment follows, or {@code null} for none. An attached light usually sits
     * <em>inside</em> its anchor (eyes, chest), where the anchor's own silhouette would blacken the
     * entire shadow map &mdash; so the renderer leaves this entity out of that light's shadow pass
     * entirely, as if it were not there. It still casts shadows from every other light.
     */
    @Nullable
    default Entity anchorEntity() {
        return null;
    }
}
