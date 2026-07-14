package fr.lacaleche.glue.client.render.light;

/** Stable world-owned handle for a frame-sampled light attachment. */
public final class LightHandle {

    private final WorldLightContext owner;
    private final LightAttachment attachment;
    private final LightTransform transform = new LightTransform();
    private Light template;
    private Light resolved;
    private boolean removed;

    LightHandle(WorldLightContext owner, Light template, LightAttachment attachment) {
        this.owner = owner;
        this.template = template;
        this.attachment = attachment;
    }

    public LightHandle light(Light light) {
        if (light == null) throw new IllegalArgumentException("Light must not be null");
        owner.update(this, light);
        return this;
    }

    public void remove() {
        if (!removed) {
            owner.remove(this);
        }
    }

    public boolean isRemoved() {
        return removed;
    }

    Light resolve(float partialTick) {
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

    Light resolved() {
        return resolved;
    }

    void markRemoved() {
        removed = true;
    }

    void updateTemplate(Light light) {
        template = light;
        resolved = null;
    }
}
