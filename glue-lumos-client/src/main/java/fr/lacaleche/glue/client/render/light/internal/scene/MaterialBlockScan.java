package fr.lacaleche.glue.client.render.light.internal.scene;

import fr.lacaleche.glue.lumos.Light;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * The single world scan behind the material G-buffer: one traversal of a light's reach that sorts every
 * visited block into the glass, water and metal buckets the material passes re-render.
 *
 * <p>The three materials cover the same volume with the same bounds, so they share one traversal &mdash;
 * collecting them apart walked the identical chunks, sections and block states three times per cache
 * miss, for nothing but a different per-block predicate.</p>
 *
 * <p>This is the work that must NOT run per frame: the caller caches the result per light and
 * invalidates it on block changes, exactly like the shadow maps.</p>
 */
@Environment(EnvType.CLIENT)
public final class MaterialBlockScan {

    /** Cached because {@code Direction.values()} clones its array on every call, and the fluid exposure
     *  test walks it once per water block. */
    private static final Direction[] DIRECTIONS = Direction.values();

    private MaterialBlockScan() {
    }

    /** The material blocks near a light, split by how they are re-rendered: {@code panes} and
     *  {@code metals} via {@code renderSingleBlock}, {@code water} via {@code renderLiquid}, and
     *  {@code terrain} &mdash; every other exposed model block &mdash; via {@code renderBatched}.
     *  Terrain is collected unconditionally (the scan is walking the volume anyway) but only
     *  re-rendered on reduced-capture frames, so a cache entry stays valid when the player toggles
     *  a shaderpack mid-session. Cached per light and invalidated together on block changes. */
    public record NearbyMaterials(List<BlockPos> panes, List<BlockPos> water, List<BlockPos> metals,
                                  List<BlockPos> terrain) {

        private static final NearbyMaterials EMPTY =
                new NearbyMaterials(List.of(), List.of(), List.of(), List.of());
    }

    /** Conservative bounding radius of one block for the cone test: the half-diagonal of a unit
     *  cube (~0.87), rounded up so a block whose corner clips the cone is never dropped. */
    private static final double BLOCK_RADIUS = 0.9;

    /**
     * Every glass, water and metal block within a light's reach.
     *
     * <p>A block can land in two buckets: a waterlogged pane is both glass to re-draw as a model and
     * water to re-draw as fluid, so the buckets are tested independently rather than exclusively.</p>
     *
     * <p>A spot or gobo light keeps only the blocks its CONE can touch. This is a correctness-
     * preserving cut, not an approximation: the deferred pass multiplies the whole response by a
     * cone-shape factor that is exactly zero outside the outer angle, so a capture there can never
     * contribute &mdash; yet the sphere volume of a long-range spot is dominated by it (a 32-degree
     * cone covers ~8% of its range sphere), and the buckets are re-rendered every frame on
     * shaderpack frames. This cut is what keeps a single spot from tessellating tens of thousands
     * of terrain blocks per frame.</p>
     */
    public static NearbyMaterials scan(Minecraft client, Light light) {
        ClientLevel level = client.level;
        if (level == null) return NearbyMaterials.EMPTY;

        double lx = light.x;
        double ly = light.y;
        double lz = light.z;
        float range = light.range;
        boolean cone = light.cosOuter > -0.999f;
        double dirX = light.directionX;
        double dirY = light.directionY;
        double dirZ = light.directionZ;
        double cosOuter = light.cosOuter;
        double sinOuter = Math.sqrt(Math.max(0.0, 1.0 - cosOuter * cosOuter));

        BlockPos center = BlockPos.containing(lx, ly, lz);
        double reach = range + 1.0;   // +1 covers the block's own half-diagonal
        double reachSq = reach * reach;
        int radius = Math.max(2, (int) Math.ceil(reach));   // the box must contain the reach sphere
        List<BlockPos> panes = new ArrayList<>();
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> metals = new ArrayList<>();
        List<BlockPos> terrain = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        int cameraChunkX = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().x)) >> 4;
        int cameraChunkZ = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().z)) >> 4;
        int loadedRadius = client.options.getEffectiveRenderDistance() + 2;
        long minX = (long) center.getX() - radius;
        long maxX = (long) center.getX() + radius;
        long minY = (long) center.getY() - radius;
        long maxY = (long) center.getY() + radius;
        long minZ = (long) center.getZ() - radius;
        long maxZ = (long) center.getZ() + radius;
        int minChunkX = Math.max((int) Math.floorDiv(minX, 16L), cameraChunkX - loadedRadius);
        int maxChunkX = Math.min((int) Math.floorDiv(maxX, 16L), cameraChunkX + loadedRadius);
        int minChunkZ = Math.max((int) Math.floorDiv(minZ, 16L), cameraChunkZ - loadedRadius);
        int maxChunkZ = Math.min((int) Math.floorDiv(maxZ, 16L), cameraChunkZ + loadedRadius);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int originX = chunkX << 4;
            int localMinX = (int) Math.max(0L, minX - originX);
            int localMaxX = (int) Math.min(15L, maxX - originX);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int originZ = chunkZ << 4;
                int localMinZ = (int) Math.max(0L, minZ - originZ);
                int localMaxZ = (int) Math.min(15L, maxZ - originZ);
                LevelChunk chunk = level.getChunkSource().getChunk(
                        chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section.hasOnlyAir()) continue;
                    int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                    if (sectionY > maxY || sectionY + 15 < minY) continue;
                    int localMinY = (int) Math.max(0L, minY - sectionY);
                    int localMaxY = (int) Math.min(15L, maxY - sectionY);
                    for (int localY = localMinY; localY <= localMaxY; localY++) {
                        int worldY = sectionY + localY;
                        double dy = worldY + 0.5 - ly;
                        for (int localX = localMinX; localX <= localMaxX; localX++) {
                            int worldX = originX + localX;
                            double dx = worldX + 0.5 - lx;
                            for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                                int worldZ = originZ + localZ;
                                double dz = worldZ + 0.5 - lz;
                                double distSq = dx * dx + dy * dy + dz * dz;
                                if (distSq > reachSq) continue;
                                if (cone) {
                                    // Signed distance from the block's bounding sphere to the cone
                                    // surface (negative = inside): reject blocks the cone cannot
                                    // touch. Blocks hugging the apex pass via the sphere radius.
                                    double axial = dx * dirX + dy * dirY + dz * dirZ;
                                    double perp = Math.sqrt(Math.max(0.0, distSq - axial * axial));
                                    if (cosOuter * perp - sinOuter * axial > BLOCK_RADIUS) continue;
                                }
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.isAir()) continue;
                                pos.set(worldX, worldY, worldZ);

                                if (state.getRenderShape() == RenderShape.MODEL) {
                                    if (GlassSceneRenderer.isGlass(state)) {
                                        panes.add(pos.immutable());
                                    } else if (MetalSceneRenderer.isMetal(state)) {
                                        metals.add(pos.immutable());
                                    } else if (hasExposedFace(level, pos, neighbour)) {
                                        terrain.add(pos.immutable());
                                    }
                                }
                                FluidState fluid = state.getFluidState();
                                if (fluid.is(FluidTags.WATER)
                                        && hasVisibleFace(level, pos, fluid, neighbour)) {
                                    water.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return new NearbyMaterials(panes, water, metals, terrain);
    }

    /** Whether any face of this block can be seen at all &mdash; the terrain bucket keeps only the
     *  exposed shell, since a buried block can never be a light's frontmost surface and the reach
     *  volume is overwhelmingly interior. */
    private static boolean hasExposedFace(BlockGetter level, BlockPos pos,
                                          BlockPos.MutableBlockPos neighbour) {
        for (Direction direction : DIRECTIONS) {
            neighbour.setWithOffset(pos, direction);
            if (!level.getBlockState(neighbour).isSolidRender()) return true;
        }
        return false;
    }

    /**
     * Whether a fluid block can contribute any geometry at all.
     *
     * <p>Submerged water is the common case around a light, and {@code renderLiquid} rejects it only
     * after allocating six neighbour positions, so it is dropped once here rather than re-tested every
     * frame. The equivalence follows vanilla's own {@code isNeighborSameFluid}: {@code isSame} on the
     * neighbour's fluid, which pairs a source with its flowing form.</p>
     *
     * <p>An opaque block does NOT hide the face below it: vanilla occludes a fluid's top face only at
     * height 1.0, which it reaches only under an equivalent fluid. Water under a solid block still draws
     * its surface in the 1/9-block gap, so it must keep its material id.</p>
     */
    private static boolean hasVisibleFace(BlockGetter level, BlockPos pos, FluidState fluid,
                                          BlockPos.MutableBlockPos neighbour) {
        for (Direction direction : DIRECTIONS) {
            neighbour.setWithOffset(pos, direction);
            BlockState state = level.getBlockState(neighbour);
            if (state.getFluidState().getType().isSame(fluid.getType())) continue;
            if (direction != Direction.UP && state.isSolidRender()) continue;
            return true;
        }
        return false;
    }
}
