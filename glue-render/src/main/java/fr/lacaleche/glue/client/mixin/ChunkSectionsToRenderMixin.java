package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.render.internal.TerrainMaterialBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin {

    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void glue$captureTerrainMaterial(ChunkSectionLayerGroup group, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            TerrainMaterialBuffer.capture((ChunkSectionsToRender) (Object) this);
        }
    }
}
