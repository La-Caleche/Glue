package fr.lacaleche.glue.client.render.light.internal.context;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightAttachment;
import fr.lacaleche.glue.lumos.LightHandle;
import fr.lacaleche.glue.lumos.LightTransform;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** The client implementation of {@link LightHandle}: a frame-sampled light owned by one world. */
public final class AttachedLight implements LightHandle {

    private final WorldLightContext owner;
    private final LightAttachment attachment;
    private final LightTransform transform = new LightTransform();
    private Light template;
    private Light resolved;
    private boolean removed;

    public AttachedLight(WorldLightContext owner, Light template, LightAttachment attachment) {
        this.owner = owner;
        this.template = template;
        this.attachment = attachment;
    }

    @Override
    public LightHandle light(Light light) {
        if (light == null) throw new IllegalArgumentException("Light must not be null");
        owner.update(this, light);
        return this;
    }

    @Override
    public void remove() {
        if (!removed) {
            owner.remove(this);
        }
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    public Light resolve(float partialTick) {
        if (removed || !attachment.sample(owner.level(), partialTick, transform)) return null;
        if (resolved != null
                && resolved.x == transform.x && resolved.y == transform.y && resolved.z == transform.z
                && resolved.directionX == transform.directionX
                && resolved.directionY == transform.directionY
                && resolved.directionZ == transform.directionZ) {
            return resolved;
        }
        resolved = template.at(transform.x, transform.y, transform.z,
                transform.directionX, transform.directionY, transform.directionZ);
        return resolved;
    }

    public Light resolved() {
        return resolved;
    }

    /** The entity this light follows, or null — excluded from its own shadow pass. */
    @Nullable
    public Entity anchorEntity() {
        return attachment.anchorEntity();
    }

    public void markRemoved() {
        removed = true;
    }

    public void updateTemplate(Light light) {
        template = light;
        resolved = null;
    }
}
