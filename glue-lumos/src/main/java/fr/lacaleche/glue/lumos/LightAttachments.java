package fr.lacaleche.glue.lumos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Built-in frame-sampled attachment sources. */
public final class LightAttachments {

    private LightAttachments() {
    }

    public static LightAttachment block(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        return (level, partialTick, result) -> {
            result.position(immutable.getX() + 0.5, immutable.getY() + 0.5, immutable.getZ() + 0.5);
            return level.hasChunkAt(immutable);
        };
    }

    public static LightAttachment entity(Entity entity) {
        return entity(entity, false);
    }

    public static LightAttachment entityEyes(Entity entity) {
        return entity(entity, true);
    }

    private static LightAttachment entity(Entity entity, boolean eyes) {
        return new LightAttachment() {
            @Override
            public boolean sample(Level level, float partialTick, LightTransform result) {
                if (entity.isRemoved() || entity.level() != level) return false;
                Vec3 position = eyes ? entity.getEyePosition(partialTick) : entity.getPosition(partialTick);
                Vec3 direction = entity.getViewVector(partialTick);
                result.position(position.x, position.y, position.z)
                        .direction((float) direction.x, (float) direction.y, (float) direction.z);
                return true;
            }

            @Override
            public Entity anchorEntity() {
                return entity;
            }
        };
    }
}
