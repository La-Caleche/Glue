package fr.lacaleche.glue.client.render;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** A texture-backed fullbright material for custom entity, item, and block-entity passes. */
public final class EmissiveMaterial {

    private final ResourceLocation texture;
    private final RenderType renderType;
    private final boolean shaded;

    private EmissiveMaterial(ResourceLocation texture, boolean shaded) {
        if (texture == null) throw new IllegalArgumentException("Emissive texture must not be null");
        this.texture = texture;
        this.shaded = shaded;
        renderType = shaded
                ? RenderType.entityTranslucentEmissive(texture, false)
                : RenderType.eyes(texture);
    }

    /** Fullbright while retaining Minecraft's directional entity lighting. */
    public static EmissiveMaterial shaded(ResourceLocation texture) {
        return new EmissiveMaterial(texture, true);
    }

    /** Fully unshaded fullbright emission. */
    public static EmissiveMaterial unshaded(ResourceLocation texture) {
        return new EmissiveMaterial(texture, false);
    }

    public ResourceLocation texture() {
        return texture;
    }

    public RenderType renderType() {
        return renderType;
    }

    public int packedLight() {
        return LightTexture.FULL_BRIGHT;
    }

    public boolean shaded() {
        return shaded;
    }
}
