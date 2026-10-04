package fr.lacaleche.glue.shaper;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where a ray enters some geometry.
 *
 * @param location  the entry point, in the space the ray was given in
 * @param face      the side of the block a transformed box's hit face points to the most
 * @param placement the index of the hit {@link PlacedGeometry} in the list that was cast against,
 *                  {@code 0} for a single geometry
 */
public record GeometryHit(Vec3 location, Direction face, int placement) {
}
