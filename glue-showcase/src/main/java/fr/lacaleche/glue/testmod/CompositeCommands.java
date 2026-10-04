package fr.lacaleche.glue.testmod;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.DataResult;
import fr.lacaleche.composite.CompositeCells;
import fr.lacaleche.composite.CompositePart;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * {@code /composite}: builds composite cells by hand.
 *
 * <ul>
 *   <li>{@code /composite add <pos> <block> [<x> <y> <z> [<yaw> [<scale>]]]} adds a part, offset in
 *   pixels, turned clockwise seen from above in degrees, and scaled about the cell centre;</li>
 *   <li>{@code /composite remove <pos> <index>} removes one part;</li>
 *   <li>{@code /composite clear <pos>} empties the cell;</li>
 *   <li>{@code /composite pack copy|move <from> <to> <cell> [<x> <y> <z> [<yaw> [<scale>]]]} packs
 *   the blocks of a cuboid into the cell, each where it stood relative to the others, the group
 *   offset in pixels, turned and scaled about its centre; without a scale it fits in one block.
 *   {@code move} removes the blocks from the cuboid.</li>
 * </ul>
 */
final class CompositeCommands {

    private CompositeCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("composite")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("add").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(add(Commands.argument("block", BlockStateArgument.block(registries))))))
                        .then(Commands.literal("remove").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(context -> report(context, CompositeCells.remove(
                                                context.getSource().getLevel(), pos(context),
                                                IntegerArgumentType.getInteger(context, "index")))))))
                        .then(Commands.literal("clear").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> report(context, CompositeCells.set(
                                        context.getSource().getLevel(), pos(context), List.of())))))
                        .then(Commands.literal("pack")
                                .then(pack(Commands.literal("copy"), false))
                                .then(pack(Commands.literal("move"), true)))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> add(ArgumentBuilder<CommandSourceStack, ?> block) {
        return block.executes(context -> add(context, 0, 0, 0, 0, 1))
                .then(Commands.argument("x", FloatArgumentType.floatArg(-16, 16))
                        .then(Commands.argument("y", FloatArgumentType.floatArg(-16, 16))
                                .then(Commands.argument("z", FloatArgumentType.floatArg(-16, 16))
                                        .executes(context -> add(context, offset(context, "x"), offset(context, "y"),
                                                offset(context, "z"), 0, 1))
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360))
                                                .executes(context -> add(context, offset(context, "x"),
                                                        offset(context, "y"), offset(context, "z"),
                                                        FloatArgumentType.getFloat(context, "yaw"), 1))
                                                .then(Commands.argument("scale", FloatArgumentType.floatArg(1 / 16f, 1))
                                                        .executes(context -> add(context, offset(context, "x"),
                                                                offset(context, "y"), offset(context, "z"),
                                                                FloatArgumentType.getFloat(context, "yaw"),
                                                                FloatArgumentType.getFloat(context, "scale"))))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> pack(ArgumentBuilder<CommandSourceStack, ?> mode, boolean move) {
        return mode.then(Commands.argument("from", BlockPosArgument.blockPos())
                .then(Commands.argument("to", BlockPosArgument.blockPos())
                        .then(Commands.argument("cell", BlockPosArgument.blockPos())
                                .executes(context -> pack(context, move, new Vector3f(), 0, fitScale(context)))
                                .then(Commands.argument("x", FloatArgumentType.floatArg(-16, 16))
                                        .then(Commands.argument("y", FloatArgumentType.floatArg(-16, 16))
                                                .then(Commands.argument("z", FloatArgumentType.floatArg(-16, 16))
                                                        .executes(context -> pack(context, move, offset(context), 0, fitScale(context)))
                                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360))
                                                                .executes(context -> pack(context, move, offset(context),
                                                                        FloatArgumentType.getFloat(context, "yaw"), fitScale(context)))
                                                                .then(Commands.argument("scale", FloatArgumentType.floatArg(1 / 64f, 1))
                                                                        .executes(context -> pack(context, move, offset(context),
                                                                                FloatArgumentType.getFloat(context, "yaw"),
                                                                                FloatArgumentType.getFloat(context, "scale")))))))))));
    }

    private static int pack(CommandContext<CommandSourceStack> context, boolean move, Vector3f offset, float yaw, float scale) {
        CommandSourceStack source = context.getSource();
        BlockPos cell = BlockPosArgument.getBlockPos(context, "cell");
        DataResult<Integer> result = CompositePacker.pack(source.getLevel(), BlockPosArgument.getBlockPos(context, "from"),
                BlockPosArgument.getBlockPos(context, "to"), cell, offset, yaw, scale, move);
        return result.mapOrElse(count -> {
            source.sendSuccess(() -> Component.literal("Packed " + count + " blocks into " + cell.toShortString()
                    + " at scale " + scale), false);
            return count;
        }, error -> {
            source.sendFailure(Component.literal(error.message()));
            return 0;
        });
    }

    private static float fitScale(CommandContext<CommandSourceStack> context) {
        return CompositePacker.fitScale(BlockPosArgument.getBlockPos(context, "from"), BlockPosArgument.getBlockPos(context, "to"));
    }

    private static Vector3f offset(CommandContext<CommandSourceStack> context) {
        return new Vector3f(offset(context, "x"), offset(context, "y"), offset(context, "z"));
    }

    private static int add(CommandContext<CommandSourceStack> context, float x, float y, float z, float yaw, float scale) {
        TransformationComponent transform = new TransformationComponent(new Vector3f(x, y, z),
                new Quaternionf().rotationY((float) Math.toRadians(-yaw)), new Vector3f(scale), new Quaternionf());
        CompositePart part = new CompositePart(BlockStateArgument.getBlock(context, "block").getState(), transform);
        return report(context, CompositeCells.add(context.getSource().getLevel(), pos(context), part));
    }

    private static int report(CommandContext<CommandSourceStack> context, DataResult<List<CompositePart>> result) {
        CommandSourceStack source = context.getSource();
        return result.mapOrElse(parts -> {
            source.sendSuccess(() -> Component.literal(pos(context).toShortString() + " holds " + parts.size() + " parts"), false);
            return parts.size();
        }, error -> {
            source.sendFailure(Component.literal(error.message()));
            return 0;
        });
    }

    private static BlockPos pos(CommandContext<CommandSourceStack> context) {
        return BlockPosArgument.getBlockPos(context, "pos");
    }

    private static float offset(CommandContext<CommandSourceStack> context, String axis) {
        return FloatArgumentType.getFloat(context, axis) / 16;
    }
}
