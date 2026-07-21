package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightHandle;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The additive-sprite demo block entity: a tick clock plus a Lumos light that follows the sprite's
 * bob. The animation math lives here so the renderer (which draws the quad) and the light (which
 * lights the world) read one source of truth and cannot drift apart.
 */
public final class TestAdditiveSpriteBlockEntity extends TickingBlockEntity {

    private static final float LIGHT_R = 1.0f;
    private static final float LIGHT_G = 0.49f;
    private static final float LIGHT_B = 0.72f;
    private static final float LIGHT_INTENSITY = 3.0f;
    private static final float LIGHT_RANGE = 10.0f;

    @Nullable
    private LightHandle light;

    public TestAdditiveSpriteBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.ADDITIVE_SPRITE_BLOCK_ENTITY, pos, state);
    }

    public double spriteCenterY(float partialTick) {
        float time = (getTicks() + partialTick) / 20f;
        return 1.8 + Math.sin(time * 1.5) * 0.1;
    }

    public float pulse(float partialTick) {
        float time = (getTicks() + partialTick) / 20f;
        return 1.0f + 0.15f * (float) Math.sin(time * 2.5);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  TestAdditiveSpriteBlockEntity entity) {
        TickingBlockEntity.tick(level, pos, state, entity);
        if (entity.light != null && !entity.light.isRemoved()) return;
        entity.light = Lumos.attach(level,
                Light.point(0, 0, 0, LIGHT_R, LIGHT_G, LIGHT_B, LIGHT_INTENSITY, LIGHT_RANGE),
                (lvl, partialTick, out) -> {
                    if (entity.isRemoved()) return false;
                    out.position(pos.getX() + 0.5, pos.getY() + entity.spriteCenterY(partialTick),
                            pos.getZ() + 0.5);
                    return true;
                });
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (light != null) light.remove();
    }
}
