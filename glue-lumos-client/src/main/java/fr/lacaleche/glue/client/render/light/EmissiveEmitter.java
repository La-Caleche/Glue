package fr.lacaleche.glue.client.render.light;

import fr.lacaleche.glue.client.render.EmissiveMaterial;
import fr.lacaleche.glue.lumos.Light;

/** Pairs a caller-rendered emissive surface with optional world illumination. */
public final class EmissiveEmitter implements AutoCloseable {

    private final EmissiveMaterial material;
    private final LightHandle light;

    private EmissiveEmitter(EmissiveMaterial material, LightHandle light) {
        this.material = material;
        this.light = light;
    }

    public static EmissiveEmitter attach(EmissiveMaterial material, Light definition,
                                         LightAttachment attachment) {
        if (material == null) throw new IllegalArgumentException("Emissive material must not be null");
        LightHandle handle = LightManager.getInstance().attach(definition, attachment);
        return new EmissiveEmitter(material, handle);
    }

    public EmissiveMaterial material() {
        return material;
    }

    public LightHandle light() {
        return light;
    }

    @Override
    public void close() {
        light.remove();
    }
}
