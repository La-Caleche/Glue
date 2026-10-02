package fr.lacaleche.glue.shaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Quadrant;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BlockShapeGenerationTest {

    private static final String SHELF_MODEL = """
            {"elements": [
              {"from": [2, 0, 0], "to": [14, 4, 16]},
              {"from": [2, 4, 10], "to": [14, 14, 16]}
            ]}""";

    private static final String SHELF_BLOCKSTATE = """
            {"variants": {
              "facing=north": {"model": "test:block/shelf"},
              "facing=east": {"model": "test:block/shelf", "y": 90},
              "facing=south": {"model": "test:block/shelf", "y": 180},
              "facing=west": {"model": "test:block/shelf", "y": 270}
            }}""";

    @TempDir
    Path root;

    @Test
    void blockstateRotationsMatchVoxelShaper() throws IOException {
        writeModel("test", "block/shelf", SHELF_MODEL);
        ModelSource source = new ModelSource(List.of(this.root));
        JsonObject blockstate = JsonParser.parseString(SHELF_BLOCKSTATE).getAsJsonObject();
        VoxelShape north = Shapes.or(Shapes.box(2 / 16d, 0, 0, 14 / 16d, 4 / 16d, 1),
                Shapes.box(2 / 16d, 4 / 16d, 10 / 16d, 14 / 16d, 14 / 16d, 1));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<BlockstateResolver.Placement> placements =
                    BlockstateResolver.resolve(blockstate, Map.of("facing", facing.getSerializedName()), "test:shelf");
            VoxelShape shape = BlockShapeProvider.shape(placements, null, new Matrix4f(), 16, source);

            assertSameShape(VoxelShaper.rotate(north, Direction.NORTH, facing), shape, "facing " + facing);
        }
    }

    @Test
    void sixteenStepRotationTurnsLikeABlockstateYRotation() {
        Matrix4f blockstateY90 = new BlockstateResolver.Placement(ResourceLocation.parse("test:m"), Quadrant.R0, Quadrant.R90).rotation();
        Matrix4f fourSteps = new Matrix4f().rotationY((float) Math.toRadians(-22.5 * 4));

        assertTrue(blockstateY90.equals(fourSteps, 1e-6f), () -> blockstateY90 + " vs " + fourSteps);
    }

    @Test
    void collisionModelReplacesTheDrawnModelAtItsRotation() throws IOException {
        writeModel("test", "block/shelf", SHELF_MODEL);
        writeModel("test", "block/shelf_collision", """
                {"elements": [{"from": [0, 0, 0], "to": [16, 4, 16]}]}""");
        ModelSource source = new ModelSource(List.of(this.root));
        List<BlockstateResolver.Placement> placements = List.of(
                new BlockstateResolver.Placement(ResourceLocation.parse("test:block/shelf"), Quadrant.R0, Quadrant.R90));

        VoxelShape collision = BlockShapeProvider.shape(placements, ResourceLocation.parse("test:block/shelf_collision"),
                new Matrix4f(), 16, source);

        assertSameShape(Shapes.box(0, 0, 0, 1, 0.25, 1), collision, "");
    }

    @Test
    void multipartUnitesEveryMatchingPart() throws IOException {
        writeModel("test", "block/post", """
                {"elements": [{"from": [6, 0, 6], "to": [10, 16, 10]}]}""");
        writeModel("test", "block/side", """
                {"elements": [{"from": [7, 0, 0], "to": [9, 16, 6]}]}""");
        JsonObject blockstate = JsonParser.parseString("""
                {"multipart": [
                  {"apply": {"model": "test:block/post"}},
                  {"when": {"north": "true"}, "apply": {"model": "test:block/side"}},
                  {"when": {"east": "true"}, "apply": {"model": "test:block/side", "y": 90}}
                ]}""").getAsJsonObject();
        ModelSource source = new ModelSource(List.of(this.root));

        VoxelShape shape = BlockShapeProvider.shape(
                BlockstateResolver.resolve(blockstate, Map.of("north", "true", "east", "false"), "test:fence"),
                null, new Matrix4f(), 16, source);

        assertSameShape(Shapes.or(Shapes.box(6 / 16d, 0, 6 / 16d, 10 / 16d, 1, 10 / 16d),
                Shapes.box(7 / 16d, 0, 0, 9 / 16d, 1, 6 / 16d)), shape, "");
    }

    @Test
    void multipartConditionsFollowTheGameSyntax() {
        JsonObject blockstate = JsonParser.parseString("""
                {"multipart": [
                  {"when": {"part": "head|foot"}, "apply": {"model": "test:a"}},
                  {"when": {"part": "!head"}, "apply": {"model": "test:b"}},
                  {"when": {"OR": [{"open": "true"}, {"part": "head"}]}, "apply": {"model": "test:c"}},
                  {"when": {"AND": [{"open": "true"}, {"part": "foot"}]}, "apply": {"model": "test:d"}}
                ]}""").getAsJsonObject();

        List<BlockstateResolver.Placement> head = BlockstateResolver.resolve(blockstate, Map.of("part", "head", "open", "false"), "t");
        List<BlockstateResolver.Placement> openFoot = BlockstateResolver.resolve(blockstate, Map.of("part", "foot", "open", "true"), "t");

        assertEquals(List.of("test:a", "test:c"), head.stream().map(p -> p.model().toString()).toList());
        assertEquals(List.of("test:a", "test:b", "test:c", "test:d"), openFoot.stream().map(p -> p.model().toString()).toList());
    }

    @Test
    void variantKeyMatchesASubsetAndAWeightedListUsesItsFirstModel() {
        JsonObject blockstate = JsonParser.parseString("""
                {"variants": {
                  "half=top": [{"model": "test:top_a", "x": 180}, {"model": "test:top_b"}],
                  "": {"model": "test:any"}
                }}""").getAsJsonObject();

        BlockstateResolver.Placement top = BlockstateResolver.resolve(blockstate, Map.of("half", "top", "lit", "true"), "t").getFirst();
        BlockstateResolver.Placement bottom = BlockstateResolver.resolve(blockstate, Map.of("half", "bottom", "lit", "true"), "t").getFirst();

        assertEquals("test:top_a", top.model().toString());
        assertEquals(Quadrant.R180, top.x());
        assertEquals("test:any", bottom.model().toString());
    }

    @Test
    void blockstateErrorsNameTheProblem() {
        JsonObject variants = JsonParser.parseString("""
                {"variants": {"facing=north": {"model": "test:m"}, "facing=up": {"model": "test:m", "y": 45}}}""").getAsJsonObject();

        IllegalStateException unknown = assertThrows(IllegalStateException.class,
                () -> BlockstateResolver.resolve(variants, Map.of("mode", "0"), "test:block"));
        IllegalStateException unmatched = assertThrows(IllegalStateException.class,
                () -> BlockstateResolver.resolve(variants, Map.of("facing", "south"), "test:block"));
        IllegalStateException notQuarter = assertThrows(IllegalStateException.class,
                () -> BlockstateResolver.resolve(variants, Map.of("facing", "up"), "test:block"));

        assertTrue(unknown.getMessage().contains("unknown property 'facing'"), unknown.getMessage());
        assertTrue(unmatched.getMessage().contains("No variant"), unmatched.getMessage());
        assertTrue(notQuarter.getMessage().contains("not a quarter turn"), notQuarter.getMessage());
    }

    @Test
    void modelInheritsElementsAndRotationsFromItsParents() throws IOException {
        writeModel("test", "block/template", """
                {"elements": [{"from": [4, 0, 4], "to": [12, 2, 12],
                  "rotation": {"origin": [8, 0, 8], "axis": "y", "angle": 45, "rescale": true}}]}""");
        writeModel("test", "block/middle", """
                {"parent": "test:block/template", "textures": {"all": "test:block/a"}}""");
        writeModel("test", "block/child", """
                {"parent": "test:block/middle"}""");

        ShapeGeometry geometry = new ModelSource(List.of(this.root)).geometry(ResourceLocation.parse("test:block/child"));

        assertEquals(1, geometry.boxes().size());
        GeometryBox box = geometry.boxes().getFirst();
        assertEquals(new AABB(0.25, 0, 0.25, 0.75, 0.125, 0.75), box.box());
        assertEquals(new GeometryBox.Rotation(new Vec3(0.5, 0, 0.5), Direction.Axis.Y, 45, true),
                box.rotation());
    }

    @Test
    void builtinParentAndMissingElementsGiveNoGeometry() throws IOException {
        writeModel("test", "block/generated", """
                {"parent": "builtin/generated"}""");
        writeModel("test", "block/textures_only", """
                {"textures": {"all": "test:block/a"}}""");
        ModelSource source = new ModelSource(List.of(this.root));

        assertTrue(source.geometry(ResourceLocation.parse("test:block/generated")).boxes().isEmpty());
        assertTrue(source.geometry(ResourceLocation.parse("test:block/textures_only")).boxes().isEmpty());
    }

    @Test
    void missingModelAndParentCycleFail() throws IOException {
        writeModel("test", "block/a", """
                {"parent": "test:block/b"}""");
        writeModel("test", "block/b", """
                {"parent": "test:block/a"}""");
        ModelSource source = new ModelSource(List.of(this.root));

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> source.geometry(ResourceLocation.parse("test:block/nowhere")));
        IllegalStateException cycle = assertThrows(IllegalStateException.class,
                () -> source.geometry(ResourceLocation.parse("test:block/a")));

        assertTrue(missing.getMessage().contains("assets/test/models/block/nowhere.json"), missing.getMessage());
        assertTrue(cycle.getMessage().contains("cycle"), cycle.getMessage());
    }

    @Test
    void shapesFileRoundTripsInPixels() {
        BlockShapesFile file = new BlockShapesFile(
                List.of(List.of(new AABB(0.125, 0, 0, 0.875, 0.25, 1)), List.of(new AABB(-0.03125, 0, 0, 1, 0.5, 1.5))),
                Map.of("facing=north", new BlockShapesFile.StateShapes(0, Optional.of(1)),
                        "facing=south", new BlockShapesFile.StateShapes(0, Optional.empty())));

        JsonObject json = BlockShapesFile.CODEC.encodeStart(JsonOps.INSTANCE, file).getOrThrow().getAsJsonObject();
        BlockShapesFile decoded = BlockShapesFile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals("2 0 0 14 4 16", json.getAsJsonArray("shapes").get(0).getAsJsonArray().get(0).getAsString());
        assertEquals(file, decoded);
    }

    @Test
    void shapesFileAcceptsAnyWhitespaceAndRejectsMalformedBoxes() {
        JsonObject tabbed = JsonParser.parseString("""
                {"shapes": [["2\\t0  0 14 4 16"]], "states": {"": {"outline": 0}}}""").getAsJsonObject();
        JsonObject shortBox = JsonParser.parseString("""
                {"shapes": [["1 2 3"]], "states": {}}""").getAsJsonObject();

        assertEquals(new AABB(0.125, 0, 0, 0.875, 0.25, 1),
                BlockShapesFile.CODEC.parse(JsonOps.INSTANCE, tabbed).getOrThrow().shapes().getFirst().getFirst());
        assertTrue(BlockShapesFile.CODEC.parse(JsonOps.INSTANCE, shortBox).isError());
    }

    private void writeModel(String namespace, String path, String json) throws IOException {
        Path file = this.root.resolve("assets/" + namespace + "/models/" + path + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private static void assertSameShape(VoxelShape expected, VoxelShape actual, String context) {
        assertFalse(Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME),
                () -> context + " expected " + expected.toAabbs() + " but was " + actual.toAabbs());
    }
}
