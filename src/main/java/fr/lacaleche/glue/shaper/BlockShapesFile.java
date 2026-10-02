package fr.lacaleche.glue.shaper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.AABB;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The generated shapes of one block: distinct shapes as boxes in pixels, and the shapes each state
 * uses, keyed by {@link BlockShapes#stateKey}.
 */
record BlockShapesFile(List<List<AABB>> shapes, Map<String, StateShapes> states) {

    /** A box as {@code "minX minY minZ maxX maxY maxZ"} in pixels, one line per box in the file. */
    private static final Codec<AABB> PIXEL_BOX = Codec.STRING.comapFlatMap(BlockShapesFile::parseBox, BlockShapesFile::formatBox);

    static final Codec<BlockShapesFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PIXEL_BOX.listOf().listOf().fieldOf("shapes").forGetter(BlockShapesFile::shapes),
            Codec.unboundedMap(Codec.STRING, StateShapes.CODEC).fieldOf("states").forGetter(BlockShapesFile::states)
    ).apply(instance, BlockShapesFile::new));

    private static DataResult<AABB> parseBox(String text) {
        String[] values = text.trim().split("\\s+");
        if (values.length != 6) return DataResult.error(() -> "A box needs 6 coordinates: " + text);
        double[] pixels = new double[6];
        try {
            for (int i = 0; i < 6; i++) pixels[i] = Double.parseDouble(values[i]) / 16;
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Not a box: " + text);
        }
        return DataResult.success(new AABB(pixels[0], pixels[1], pixels[2], pixels[3], pixels[4], pixels[5]));
    }

    private static String formatBox(AABB box) {
        return Stream.of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                .map(value -> BigDecimal.valueOf(value * 16).stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(" "));
    }

    /**
     * @param outline   index of the state's outline shape
     * @param collision index of its collision shape, absent when it is the outline
     */
    record StateShapes(int outline, Optional<Integer> collision) {

        static final Codec<StateShapes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("outline").forGetter(StateShapes::outline),
                Codec.INT.optionalFieldOf("collision").forGetter(StateShapes::collision)
        ).apply(instance, StateShapes::new));
    }
}
