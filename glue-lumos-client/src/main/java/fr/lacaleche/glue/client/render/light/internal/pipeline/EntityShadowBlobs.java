package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.lumos.Light;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.List;

/**
 * Collects nearby living entities as vertical capsules for the deferred pass's blob shadows.
 *
 * <p>Entities are not baked into the (cached, block-change-invalidated) shadow maps -- they move
 * every frame. Instead each nearby living entity is approximated as a vertical capsule the deferred
 * pass analytically occludes the light against, a cheap soft shadow that grounds a lit entity without
 * a per-entity shadow map.</p>
 */
final class EntityShadowBlobs {

    static final int MAX = 8;
    /** Floats needed to hold {@link #MAX} capsules: two vec4 each. */
    static final int FLOATS = MAX * 8;

    private EntityShadowBlobs() {
    }

    /**
     * Fills {@code out} with up to {@link #MAX} capsules (camera-relative) for living entities within
     * the light's reach and returns the count. Layout per blob {@code i}:
     * {@code out[i*8..+3] = (A.xyz, radius)}, {@code out[i*8+4..+7] = (B.xyz, 0)}, where A is the lower
     * axis end and B the upper -- the capsule spans the entity from feet to head.
     */
    static int collect(Minecraft minecraft, Light light, Vector3d camera, float partialTick, float[] out) {
        Level level = minecraft.level;
        if (level == null) return 0;
        double reach = light.range;
        AABB box = new AABB(light.x - reach, light.y - reach, light.z - reach,
                light.x + reach, light.y + reach, light.z + reach);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> !entity.isSpectator() && !entity.isInvisible());
        double reachSq = reach * reach;
        int count = 0;
        for (LivingEntity entity : entities) {
            if (count >= MAX) break;
            double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            float height = entity.getBbHeight();
            double centerDX = ex - light.x;
            double centerDY = ey + height * 0.5 - light.y;
            double centerDZ = ez - light.z;
            if (centerDX * centerDX + centerDY * centerDY + centerDZ * centerDZ > reachSq) continue;

            float radius = Math.max(entity.getBbWidth() * 0.5f, 0.12f);
            float axisHalf = Math.max(height * 0.5f - radius, 0.0f);
            float axisX = (float) (ex - camera.x);
            float axisZ = (float) (ez - camera.z);
            float midY = (float) (ey - camera.y) + height * 0.5f;

            int offset = count * 8;
            out[offset] = axisX;
            out[offset + 1] = midY - axisHalf;
            out[offset + 2] = axisZ;
            out[offset + 3] = radius;
            out[offset + 4] = axisX;
            out[offset + 5] = midY + axisHalf;
            out[offset + 6] = axisZ;
            out[offset + 7] = 0.0f;
            count++;
        }
        return count;
    }
}
