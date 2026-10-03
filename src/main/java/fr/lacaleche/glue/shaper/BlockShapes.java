package fr.lacaleche.glue.shaper;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generated block shapes, read from {@code glue/shapes/<namespace>/<block>.json} in mod jars.
 *
 * <p>{@link BlockShapeProvider} writes those files at build time. Glue applies them to any block
 * whose class does not override {@code getShape} (or {@code getCollisionShape}) without calling
 * {@code super}: a shape written in Java always wins. The files are deliberately not data pack
 * content; a server-side override would desync collision from clients that never see it.</p>
 *
 * <p>A block's file is read on its first shape query and kept for the session. Lookups are safe
 * from any thread.</p>
 */
public final class BlockShapes {

    static final String DIRECTORY = "glue/shapes";

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shapes");
    private static final Table NONE = new Table(Map.of(), Map.of(), Map.of());
    private static final Map<Block, Table> TABLES = new ConcurrentHashMap<>();

    private BlockShapes() {
    }

    /** The generated outline of a state, or {@code null} when its block has none. */
    public static @Nullable VoxelShape outline(BlockState state) {
        return table(state.getBlock()).outlines.get(state);
    }

    /** The generated collision of a state, or {@code null} when its block has none. */
    public static @Nullable VoxelShape collision(BlockState state) {
        return table(state.getBlock()).collisions.get(state);
    }

    /**
     * The models a state draws, each where the state places it, or {@code null} when its block has
     * no generated geometry. The list is immutable.
     */
    public static @Nullable List<PlacedGeometry> geometry(BlockState state) {
        return table(state.getBlock()).geometries.get(state);
    }

    /** A state's key in a shapes file: its properties as {@code name=value}, comma-separated. */
    static String stateKey(BlockState state) {
        return state.getValues().entrySet().stream()
                .map(entry -> entry.getKey().getName() + "=" + valueName(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    static <T extends Comparable<T>> String valueName(Property<T> property, Comparable<?> value) {
        return property.getName(property.getValueClass().cast(value));
    }

    private static Table table(Block block) {
        Table table = TABLES.get(block);
        if (table != null) return table;

        // A block queried before registration has no id yet; it is looked up again once it has one.
        Optional<ResourceKey<Block>> key = BuiltInRegistries.BLOCK.getResourceKey(block);
        if (key.isEmpty()) return NONE;
        return TABLES.computeIfAbsent(block, unused -> load(block, key.get().location()));
    }

    private static Table load(Block block, ResourceLocation id) {
        Path file = Index.FILES.get(id);
        if (file == null) return NONE;

        BlockShapesFile shapesFile;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            shapesFile = BlockShapesFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(message -> new IllegalStateException("Invalid shapes file " + file + ": " + message));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read shapes file " + file, exception);
        }

        List<VoxelShape> shapes = new ArrayList<>();
        for (List<AABB> boxes : shapesFile.shapes()) shapes.add(union(boxes));
        List<ShapeGeometry> models = shapesFile.geometries().stream().map(ShapeGeometry::new).toList();

        Map<BlockState, VoxelShape> outlines = new IdentityHashMap<>();
        Map<BlockState, VoxelShape> collisions = new IdentityHashMap<>();
        Map<BlockState, List<PlacedGeometry>> geometries = new IdentityHashMap<>();
        int missing = 0;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BlockShapesFile.StateShapes entry = shapesFile.states().get(stateKey(state));
            if (entry == null) {
                missing++;
                continue;
            }
            outlines.put(state, shape(shapes, entry.outline(), file));
            collisions.put(state, shape(shapes, entry.collision().orElse(entry.outline()), file));
            if (!entry.models().isEmpty()) geometries.put(state, placed(models, entry.models(), file));
        }
        if (missing > 0) {
            LOGGER.warn("{} of {} states of {} have no generated shape in {}; regenerate the shapes",
                    missing, block.getStateDefinition().getPossibleStates().size(), id, file);
        }
        return new Table(outlines, collisions, geometries);
    }

    private static List<PlacedGeometry> placed(List<ShapeGeometry> models, List<BlockShapesFile.PlacedModel> entries, Path file) {
        List<PlacedGeometry> placed = new ArrayList<>();
        for (BlockShapesFile.PlacedModel entry : entries) {
            if (entry.geometry() < 0 || entry.geometry() >= models.size()) {
                throw new IllegalStateException("Shapes file " + file + " refers to missing geometry " + entry.geometry());
            }
            placed.add(new PlacedGeometry(models.get(entry.geometry()), entry.transform()));
        }
        return List.copyOf(placed);
    }

    private static VoxelShape shape(List<VoxelShape> shapes, int index, Path file) {
        if (index < 0 || index >= shapes.size()) {
            throw new IllegalStateException("Shapes file " + file + " refers to missing shape " + index);
        }
        return shapes.get(index);
    }

    private static VoxelShape union(List<AABB> boxes) {
        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) shape = Shapes.joinUnoptimized(shape, Shapes.create(box), BooleanOp.OR);
        return shape.optimize();
    }

    private record Table(Map<BlockState, VoxelShape> outlines, Map<BlockState, VoxelShape> collisions,
                         Map<BlockState, List<PlacedGeometry>> geometries) {
    }

    /** Every shapes file in the loaded mods, found once on first use. */
    private static final class Index {

        static final Map<ResourceLocation, Path> FILES = scan();

        private static Map<ResourceLocation, Path> scan() {
            Map<ResourceLocation, Path> files = new HashMap<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                for (Path root : mod.getRootPaths()) {
                    Path directory = root.resolve(DIRECTORY);
                    if (!Files.isDirectory(directory)) continue;
                    try (Stream<Path> paths = Files.walk(directory)) {
                        paths.filter(path -> path.toString().endsWith(".json"))
                                .forEach(path -> files.putIfAbsent(id(directory, path), path));
                    } catch (IOException exception) {
                        throw new UncheckedIOException("Cannot list " + directory + " in " + mod.getMetadata().getId(), exception);
                    }
                }
            }
            return Map.copyOf(files);
        }

        /** {@code <directory>/<namespace>/<path>.json} to {@code namespace:path}. */
        private static ResourceLocation id(Path directory, Path file) {
            Path relative = directory.relativize(file);
            String namespace = relative.getName(0).toString();
            String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
            return ResourceLocation.fromNamespaceAndPath(namespace, path.substring(0, path.length() - ".json".length()));
        }
    }
}
