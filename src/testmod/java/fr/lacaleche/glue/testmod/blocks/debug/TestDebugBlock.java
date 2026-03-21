package fr.lacaleche.glue.testmod.blocks.debug;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.shaper.GlueVoxelShape;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestOutlineRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TestDebugBlock extends BaseEntityBlock implements GlueBlock {
    public static final MapCodec<TestDebugBlock> CODEC = simpleCodec(TestDebugBlock::new);

    @Override
    public MapCodec<? extends TestDebugBlock> codec() {
        return CODEC;
    }

    protected static final VoxelShape SHAPE = Shapes.block();
    protected static final VoxelShape OUTLINE_SHAPE = new GlueVoxelShape(Block.box(3, 0, 3, 13, 16, 13), 45);

    public TestDebugBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestDebugBlockEntity(pos, state);
    }

    @Override
    public ResourceLocation getOutlineRenderer() {
        return TestmodClient.id("example");
    }
}
