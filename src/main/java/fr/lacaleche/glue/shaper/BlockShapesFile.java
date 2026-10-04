package fr.lacaleche.glue.shaper;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The generated shapes of one block: distinct shapes as boxes in pixels, the geometry of the models
 * the block draws or collides as, the resolution its collision was voxelized at, and per state,
 * keyed by {@link BlockShapes#stateKey}, its shapes and where its models stand.
 */
record BlockShapesFile(List<List<AABB>> shapes, List<List<GeometryBox>> geometries, int collisionResolution,
                       Map<String, StateShapes> states) {

    /** A box as {@code "minX minY minZ maxX maxY maxZ"} in pixels, one line per box in the file. */
    private static final Codec<AABB> PIXEL_BOX = Codec.STRING.comapFlatMap(BlockShapesFile::parseBox, BlockShapesFile::formatBox);

    /**
     * A model element: its box, then, when rotated, {@code "<axis> <degrees> <originX> <originY>
     * <originZ>"} and {@code "rescale"} if it stretches, all in pixels.
     */
    private static final Codec<GeometryBox> ELEMENT = Codec.STRING.comapFlatMap(BlockShapesFile::parseElement, BlockShapesFile::formatElement);

    static final Codec<BlockShapesFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PIXEL_BOX.listOf().listOf().fieldOf("shapes").forGetter(BlockShapesFile::shapes),
            ELEMENT.listOf().listOf().optionalFieldOf("geometries", List.of()).forGetter(BlockShapesFile::geometries),
            Codec.INT.optionalFieldOf("collision_resolution", BlockShapeProvider.Rule.DEFAULT_COLLISION_RESOLUTION)
                    .forGetter(BlockShapesFile::collisionResolution),
            Codec.unboundedMap(Codec.STRING, StateShapes.CODEC).fieldOf("states").forGetter(BlockShapesFile::states)
    ).apply(instance, BlockShapesFile::new));

    private static DataResult<AABB> parseBox(String text) {
        String[] values = text.trim().split("\\s+");
        if (values.length != 6) return DataResult.error(() -> "A box needs 6 coordinates: " + text);
        return pixels(values, 0, 6, text).map(pixels -> new AABB(pixels[0], pixels[1], pixels[2], pixels[3], pixels[4], pixels[5]));
    }

    private static String formatBox(AABB box) {
        return format(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static DataResult<GeometryBox> parseElement(String text) {
        String[] values = text.trim().split("\\s+");
        if (values.length != 6 && values.length != 11 && values.length != 12) {
            return DataResult.error(() -> "An element needs a box, then optionally an axis, an angle and an origin: " + text);
        }
        DataResult<AABB> box = parseBox(String.join(" ", List.of(values).subList(0, 6)));
        if (values.length == 6) return box.map(GeometryBox::of);

        Direction.Axis axis = Direction.Axis.byName(values[6].toLowerCase(Locale.ROOT));
        if (axis == null) return DataResult.error(() -> "Not an axis: " + values[6] + " in " + text);
        if (values.length == 12 && !values[11].equals("rescale")) return DataResult.error(() -> "Expected 'rescale': " + text);
        float degrees;
        try {
            degrees = Float.parseFloat(values[7]);
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Not an angle: " + values[7] + " in " + text);
        }
        return box.flatMap(aabb -> pixels(values, 8, 11, text).map(origin -> GeometryBox.of(aabb).rotated(
                new GeometryBox.Rotation(new Vec3(origin[0], origin[1], origin[2]), axis, degrees, values.length == 12))));
    }

    private static String formatElement(GeometryBox element) {
        String box = formatBox(element.box());
        GeometryBox.Rotation rotation = element.rotation();
        if (rotation == null) return box;
        return box + " " + rotation.axis().getName() + " " + BigDecimal.valueOf(rotation.degrees()).stripTrailingZeros().toPlainString()
                + " " + format(rotation.origin().x, rotation.origin().y, rotation.origin().z)
                + (rotation.rescale() ? " rescale" : "");
    }

    /** {@code values[from..to)} as pixels converted to block units. */
    private static DataResult<double[]> pixels(String[] values, int from, int to, String text) {
        double[] blocks = new double[to - from];
        try {
            for (int i = from; i < to; i++) blocks[i - from] = Double.parseDouble(values[i]) / 16;
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Not a number in " + text);
        }
        return DataResult.success(blocks);
    }

    /** Block units written as pixels, without trailing zeros. */
    private static String format(double... blocks) {
        return Arrays.stream(blocks)
                .mapToObj(value -> BigDecimal.valueOf(value * 16).stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(" "));
    }

    /**
     * @param outline   index of the state's outline shape
     * @param collision index of its collision shape, absent when it is the outline
     * @param models    the models the state draws and where
     * @param collisionModels the models its collision is made of and where, empty when it is the drawn models
     */
    record StateShapes(int outline, Optional<Integer> collision, List<PlacedModel> models, List<PlacedModel> collisionModels) {

        static final Codec<StateShapes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("outline").forGetter(StateShapes::outline),
                Codec.INT.optionalFieldOf("collision").forGetter(StateShapes::collision),
                PlacedModel.CODEC.listOf().optionalFieldOf("models", List.of()).forGetter(StateShapes::models),
                PlacedModel.CODEC.listOf().optionalFieldOf("collision_models", List.of()).forGetter(StateShapes::collisionModels)
        ).apply(instance, StateShapes::new));
    }

    /**
     * A model drawn by a state: the index of its geometry, its blockstate {@code x} and {@code y}
     * quarter turns, and an extra turn about the vertical axis in degrees, such as a 16-step rotation
     * applied by a renderer.
     */
    record PlacedModel(int geometry, int x, int y, float turn) {

        static final Codec<PlacedModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("geometry").forGetter(PlacedModel::geometry),
                Codec.INT.optionalFieldOf("x", 0).forGetter(PlacedModel::x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(PlacedModel::y),
                Codec.FLOAT.optionalFieldOf("turn", 0f).forGetter(PlacedModel::turn)
        ).apply(instance, PlacedModel::new));

        /** Where the model stands in the block, in block units: the turn applied after the blockstate rotation. */
        Matrix4f transform() {
            Matrix4f rotation = new Matrix4f().rotationY((float) Math.toRadians(-this.turn))
                    .mul(new Matrix4f(OctahedralGroup.fromXYAngles(
                            BlockstateResolver.quadrant(this.x, "a shapes file"),
                            BlockstateResolver.quadrant(this.y, "a shapes file")).transformation()));
            return ShapeGeometry.aboutCentre(rotation);
        }
    }
}
