package fr.lacaleche.glue.client.viewport.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registers a renderer-owned OpenGL texture without taking ownership of its storage. */
public final class BorrowedSceneTexture {

    private final ResourceLocation location;
    private int registeredTextureId = -1;
    private int registeredWidth = -1;
    private int registeredHeight = -1;

    public BorrowedSceneTexture(ResourceLocation location) {
        this.location = Objects.requireNonNull(location, "location");
    }

    public ResourceLocation location() {
        return this.location;
    }

    public void update(Minecraft client, int textureId, int width, int height) {
        if (textureId == this.registeredTextureId
                && width == this.registeredWidth
                && height == this.registeredHeight) {
            return;
        }

        this.registeredTextureId = textureId;
        this.registeredWidth = width;
        this.registeredHeight = height;
        client.getTextureManager().release(this.location);
        client.getTextureManager().register(this.location, new ExternalTexture(textureId, width, height));
    }

    public void release(Minecraft client) {
        if (this.registeredTextureId == -1) return;

        client.getTextureManager().release(this.location);
        this.registeredTextureId = -1;
        this.registeredWidth = -1;
        this.registeredHeight = -1;
    }

    private static final class ExternalTexture extends AbstractTexture {

        private ExternalTexture(int id, int width, int height) {
            ExternalGlTexture texture = new ExternalGlTexture(id, width, height);
            this.texture = texture;
            this.textureView = new ExternalTextureView(texture);
        }
    }

    private static final class ExternalGlTexture extends GlTexture {

        private ExternalGlTexture(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "glue scene viewport", TextureFormat.RGBA8,
                    width, height, 1, 1, id);
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void removeViews() {
        }
    }

    private static final class ExternalTextureView extends GlTextureView {

        private ExternalTextureView(GlTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
