package fr.lacaleche.glue.shaper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads blockstate and block model JSON for shape generation, without the game's model loading:
 * first from the given roots (directories holding {@code assets/}), then from the classpath, which
 * has the vanilla and dependency models.
 */
final class ModelSource {

    /** Longer parent chains are treated as a cycle. */
    private static final int MAX_PARENT_DEPTH = 32;

    private final List<Path> roots;
    private final Map<ResourceLocation, ShapeGeometry> geometries = new HashMap<>();

    ModelSource(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    JsonObject blockstate(ResourceLocation block) {
        String path = "assets/" + block.getNamespace() + "/blockstates/" + block.getPath() + ".json";
        JsonObject json = read(path);
        if (json == null) throw new IllegalStateException("Missing blockstate " + path);
        return json;
    }

    /** The elements of a model, inherited from its parents when it declares none. */
    ShapeGeometry geometry(ResourceLocation model) {
        ShapeGeometry geometry = this.geometries.get(model);
        if (geometry == null) {
            geometry = readGeometry(model, 0);
            this.geometries.put(model, geometry);
        }
        return geometry;
    }

    private ShapeGeometry readGeometry(ResourceLocation model, int depth) {
        if (depth > MAX_PARENT_DEPTH) throw new IllegalStateException("Model parent chain too deep, or a cycle, at " + model);

        String path = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        JsonObject json = read(path);
        if (json == null) throw new IllegalStateException("Missing model " + path);

        if (json.has("elements")) return elements(GsonHelper.getAsJsonArray(json, "elements"), model);
        if (!json.has("parent")) return ShapeGeometry.EMPTY;

        String parent = GsonHelper.getAsString(json, "parent");
        if (parent.startsWith("builtin/")) return ShapeGeometry.EMPTY;
        return readGeometry(ResourceLocation.parse(parent), depth + 1);
    }

    private static ShapeGeometry elements(JsonArray elements, ResourceLocation model) {
        List<GeometryBox> boxes = new ArrayList<>();
        for (JsonElement element : elements) {
            JsonObject object = GsonHelper.convertToJsonObject(element, "element");
            Vec3 from = vector(object, "from", model).scale(1 / 16d);
            Vec3 to = vector(object, "to", model).scale(1 / 16d);
            GeometryBox box = GeometryBox.of(new AABB(from, to));
            // A zero angle leaves the element aligned, so its edges merge with its neighbours' in outlines.
            if (object.has("rotation") && GsonHelper.getAsFloat(GsonHelper.getAsJsonObject(object, "rotation"), "angle") != 0f) {
                box = box.rotated(rotation(GsonHelper.getAsJsonObject(object, "rotation"), model));
            }
            boxes.add(box);
        }
        return new ShapeGeometry(boxes);
    }

    private static GeometryBox.Rotation rotation(JsonObject json, ResourceLocation model) {
        String axis = GsonHelper.getAsString(json, "axis");
        Direction.Axis parsed = Direction.Axis.byName(axis.toLowerCase(Locale.ROOT));
        if (parsed == null) throw new IllegalStateException("Unknown rotation axis '" + axis + "' in model " + model);
        return new GeometryBox.Rotation(
                vector(json, "origin", model).scale(1 / 16d),
                parsed,
                GsonHelper.getAsFloat(json, "angle"),
                GsonHelper.getAsBoolean(json, "rescale", false));
    }

    private static Vec3 vector(JsonObject json, String member, ResourceLocation model) {
        JsonArray array = GsonHelper.getAsJsonArray(json, member);
        if (array.size() != 3) throw new IllegalStateException("'" + member + "' needs 3 values in model " + model);
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private @Nullable JsonObject read(String path) {
        try {
            for (Path root : this.roots) {
                Path file = root.resolve(path);
                if (Files.isRegularFile(file)) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        return JsonParser.parseReader(reader).getAsJsonObject();
                    }
                }
            }
            try (InputStream stream = ModelSource.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) return null;
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + path, exception);
        }
    }
}
