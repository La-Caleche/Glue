package fr.lacaleche.composite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.List;

/**
 * An item drawn in a composite cell as an item frame draws it, before the frame halves it: a flat
 * item stands upright across the cell, a block wide, and a block item is its block at half size.
 * It has no collision; its outline follows the same rule, from the block's outline for a block item.
 *
 * <p>The part holds its own copy of the stack, of one item, which must not be modified, and compares
 * as its item and components.</p>
 */
public record ItemPart(ItemStack stack, TransformationComponent transform) implements CompositePart {

    public static final Codec<ItemPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(ItemPart::stack),
            TransformationComponent.CODEC.optionalFieldOf("transform", TransformationComponent.DEFAULT)
                    .forGetter(ItemPart::transform)
    ).apply(instance, ItemPart::new));

    /** A flat item: one pixel thick across the middle of the cell. */
    private static final VoxelShape FLAT = Shapes.box(0, 0, 7.5 / 16, 1, 1, 8.5 / 16);

    /** @throws IllegalArgumentException for an empty stack, which draws nothing */
    public ItemPart {
        if (stack.isEmpty()) throw new IllegalArgumentException("An item part needs an item");
        stack = stack.copyWithCount(1);
    }

    public ItemPart(ItemStack stack) {
        this(stack, TransformationComponent.DEFAULT);
    }

    @Override
    public List<PlacedGeometry> geometry() {
        Matrix4f matrix = matrix();
        if (this.stack.getItem() instanceof BlockItem item) {
            VoxelShape shape = item.getBlock().defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            return List.of(new PlacedGeometry(ShapeGeometry.of(shape), matrix.mul(ShapeGeometry.aboutCentre(new Matrix4f().scale(0.5f)))));
        }
        return List.of(new PlacedGeometry(ShapeGeometry.of(FLAT), matrix));
    }

    @Override
    public VoxelShape collision() {
        return Shapes.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemPart part && ItemStack.matches(this.stack, part.stack) && this.transform.equals(part.transform);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(this.stack) + this.transform.hashCode();
    }
}
