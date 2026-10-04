package fr.lacaleche.composite;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * One thing drawn inside a composite cell, a {@link BlockPart block} or an {@link ItemPart item},
 * and the transform that moves, turns and scales it about the cell centre, in block units.
 *
 * <p>Parts are only drawn. A part's block keeps its model, shapes and block entity data, but never
 * ticks, is used, or reacts to or acts on its neighbours: a lit lamp part looks lit because its
 * state says so, and emits no light; a powered lever part powers nothing.</p>
 */
public sealed interface CompositePart permits BlockPart, ItemPart {

    /**
     * A block part as its {@code state}, an item part as its {@code item}. Decoding fails for a block
     * or an item that is no longer registered, which drops the part from a cell.
     */
    // Lazy: the parts initialise this interface, which declares default methods, before their own codecs.
    Codec<CompositePart> CODEC = Codec.lazyInitialized(() -> Codec.either(BlockPart.CODEC, ItemPart.CODEC).xmap(
            either -> either.map(block -> block, item -> item),
            part -> switch (part) {
                case BlockPart block -> Either.left(block);
                case ItemPart item -> Either.right(item);
            }));

    TransformationComponent transform();

    /**
     * What the part looks like in its cell, for its outline and for picking, moved by the part's
     * transform.
     */
    List<PlacedGeometry> geometry();

    /** The part's collision once transformed, voxelized where it leaves the axes. */
    VoxelShape collision();

    /** The transform as a matrix in block units, applied about the cell centre. */
    default Matrix4f matrix() {
        return ShapeGeometry.aboutCentre(transform().toTransformation().getMatrix());
    }

    /** Whether the part sits exactly where a plain block would. */
    default boolean isIdentity() {
        return matrix().equals(new Matrix4f(), 1e-6f);
    }

    /** The part's outline once transformed, voxelized where it leaves the axes. */
    default VoxelShape outline() {
        return PlacedGeometry.toShape(geometry(), ShapeGeometry.DEFAULT_RESOLUTION);
    }

    /** The bounds of the part's geometry, or {@code null} when it has none. */
    default @Nullable AABB bounds() {
        return PlacedGeometry.bounds(geometry());
    }
}
