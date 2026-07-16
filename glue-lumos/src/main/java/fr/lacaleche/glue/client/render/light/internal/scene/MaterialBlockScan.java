package fr.lacaleche.glue.client.render.light.internal.scene;

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

    /** The special-material blocks near a light, split by how they are re-rendered: {@code panes} and
     *  {@code metals} via {@code renderSingleBlock}, {@code water} via {@code renderLiquid}. Cached per
     *  light and invalidated together on block changes. */
    public record NearbyMaterials(List<BlockPos> panes, List<BlockPos> water, List<BlockPos> metals) {

        private static final NearbyMaterials EMPTY =
                new NearbyMaterials(List.of(), List.of(), List.of());
    }

    /**
     * Every glass, water and metal block within a light's reach.
     *
     * <p>A block can land in two buckets: a waterlogged pane is both glass to re-draw as a model and
     * water to re-draw as fluid, so the buckets are tested independently rather than exclusively.</p>
     */
    public static NearbyMaterials scan(Minecraft client, double lx, double ly, double lz, float range) {
        ClientLevel level = client.level;
        if (level == null) return NearbyMaterials.EMPTY;

        BlockPos center = BlockPos.containing(lx, ly, lz);
        double reach = range + 1.0;   // +1 covers the block's own half-diagonal
        double reachSq = reach * reach;
        int radius = Math.max(2, (int) Math.ceil(reach));   // the box must contain the reach sphere
        List<BlockPos> panes = new ArrayList<>();
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> metals = new ArrayList<>();
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
                                if (dx * dx + dy * dy + dz * dz > reachSq) continue;
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.isAir()) continue;
                                pos.set(worldX, worldY, worldZ);

                                if (state.getRenderShape() == RenderShape.MODEL) {
                                    if (GlassSceneRenderer.isGlass(state)) {
                                        panes.add(pos.immutable());
                                    } else if (MetalSceneRenderer.isMetal(state)) {
                                        metals.add(pos.immutable());
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
        return new NearbyMaterials(panes, water, metals);
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
