package fr.lacaleche.composite;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The block of a composite cell. Its shapes come from its {@link CompositeBlockEntity}, so they vary
 * per position rather than per state; its model draws the parts into the chunk mesh. It ticks the
 * parts' block entities and hands each interaction to the part it aims at. Its state sums up its
 * parts for what vanilla reads from states: the light it emits and whether it is a signal source or
 * has a comparator output.
 */
public class CompositeBlock extends BaseEntityBlock implements GlueBlock {

    public static final MapCodec<CompositeBlock> CODEC = simpleCodec(CompositeBlock::new);

    /** The brightest light a part emits, which the cell emits. */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** Whether a part is a redstone signal source, so that redstone connects to the cell. */
    public static final BooleanProperty SIGNAL = BooleanProperty.create("signal");
    /** Whether a part has a comparator output. */
    public static final BooleanProperty ANALOG = BooleanProperty.create("analog");

    public CompositeBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIGHT, 0).setValue(SIGNAL, false).setValue(ANALOG, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT, SIGNAL, ANALOG);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompositeBlockEntity(CompositeBlocks.COMPOSITE_ENTITY, pos, state);
    }

    /** Ticks the parts' block entities, on whichever side their blocks tick. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, CompositeBlocks.COMPOSITE_ENTITY, (tickLevel, pos, tickState, cell) -> cell.tick());
    }

    /** Hands the interaction to the part the player aims at, as if its block stood alone here. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity cell)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return cell.useItemOn(stack, player, hand, hit);
    }

    /** See {@link #useItemOn}. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity cell)) return InteractionResult.PASS;
        return cell.useWithoutItem(player, hit);
    }

    /** Hands the attack to the part the player aims at. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity cell) cell.attack(player);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(SIGNAL);
    }

    /** The strongest signal a part sends this way, each part asked in its own orientation. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(SIGNAL) && level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.signal(level, direction, false) : 0;
    }

    /** See {@link #getSignal}. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(SIGNAL) && level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.signal(level, direction, true) : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(ANALOG);
    }

    /** The strongest comparator output of the parts: a chest part's fill, for instance. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.analogSignal() : 0;
    }

    /** Each part reacts to its neighbours as its block would: a lamp part lights when powered. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity cell) cell.neighborChanged(block, orientation, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.outline() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.collision() : Shapes.empty();
    }

    /** Every part's geometry, so outlines and ray casts follow each part at its angle. */
    @Override
    public @Nullable List<PlacedGeometry> getGeometry(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.geometry() : null;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
