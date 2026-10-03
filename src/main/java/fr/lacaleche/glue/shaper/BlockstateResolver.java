package fr.lacaleche.glue.shaper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.OctahedralGroup;
import com.mojang.math.Quadrant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the models a blockstate file draws for one state, the way the game does: the matching
 * variant, or every multipart part whose condition holds. Works on property names and values, so
 * it needs no registered block.
 */
final class BlockstateResolver {

    private BlockstateResolver() {
    }

    /**
     * @param properties the state's property values by property name, as blockstate files write them
     * @throws IllegalStateException if no variant matches or the file has neither variants nor multipart
     */
    static List<Placement> resolve(JsonObject blockstate, Map<String, String> properties, String context) {
        if (blockstate.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : GsonHelper.getAsJsonObject(blockstate, "variants").entrySet()) {
                if (matchesVariant(variant.getKey(), properties, context)) return List.of(placement(variant.getValue(), context));
            }
            throw new IllegalStateException("No variant of " + context + " matches " + properties);
        }
        if (blockstate.has("multipart")) {
            List<Placement> placements = new ArrayList<>();
            for (JsonElement part : GsonHelper.getAsJsonArray(blockstate, "multipart")) {
                JsonObject object = GsonHelper.convertToJsonObject(part, "multipart part");
                if (!object.has("when") || matches(GsonHelper.getAsJsonObject(object, "when"), properties, context)) {
                    placements.add(placement(object.get("apply"), context));
                }
            }
            return placements;
        }
        throw new IllegalStateException("Blockstate " + context + " has neither variants nor multipart");
    }

    private static boolean matchesVariant(String key, Map<String, String> properties, String context) {
        if (key.isEmpty()) return true;
        for (String pair : key.split(",")) {
            int separator = pair.indexOf('=');
            if (separator < 0) throw new IllegalStateException("Malformed variant '" + key + "' in " + context);
            if (!value(properties, pair.substring(0, separator), context).equals(pair.substring(separator + 1))) return false;
        }
        return true;
    }

    private static boolean matches(JsonObject condition, Map<String, String> properties, String context) {
        if (condition.has("OR")) return anyMatches(GsonHelper.getAsJsonArray(condition, "OR"), properties, context);
        if (condition.has("AND")) return allMatch(GsonHelper.getAsJsonArray(condition, "AND"), properties, context);

        for (Map.Entry<String, JsonElement> term : condition.entrySet()) {
            String actual = value(properties, term.getKey(), context);
            boolean termMatches = false;
            for (String accepted : term.getValue().getAsString().split("\\|")) {
                boolean negated = accepted.startsWith("!");
                if (negated != actual.equals(negated ? accepted.substring(1) : accepted)) {
                    termMatches = true;
                    break;
                }
            }
            if (!termMatches) return false;
        }
        return true;
    }

    private static boolean anyMatches(JsonArray conditions, Map<String, String> properties, String context) {
        for (JsonElement condition : conditions) {
            if (matches(GsonHelper.convertToJsonObject(condition, "condition"), properties, context)) return true;
        }
        return false;
    }

    private static boolean allMatch(JsonArray conditions, Map<String, String> properties, String context) {
        for (JsonElement condition : conditions) {
            if (!matches(GsonHelper.convertToJsonObject(condition, "condition"), properties, context)) return false;
        }
        return true;
    }

    private static String value(Map<String, String> properties, String name, String context) {
        String value = properties.get(name);
        if (value == null) throw new IllegalStateException("Blockstate " + context + " names unknown property '" + name + "'");
        return value;
    }

    /** A weighted list draws one of its models at random; its first entry stands for all of them. */
    private static Placement placement(JsonElement element, String context) {
        JsonObject model = element.isJsonArray()
                ? GsonHelper.convertToJsonObject(element.getAsJsonArray().get(0), "model")
                : GsonHelper.convertToJsonObject(element, "model");
        return new Placement(
                ResourceLocation.parse(GsonHelper.getAsString(model, "model")),
                quadrant(GsonHelper.getAsInt(model, "x", 0), context),
                quadrant(GsonHelper.getAsInt(model, "y", 0), context));
    }

    static Quadrant quadrant(int degrees, String context) {
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> Quadrant.R0;
            case 90 -> Quadrant.R90;
            case 180 -> Quadrant.R180;
            case 270 -> Quadrant.R270;
            default -> throw new IllegalStateException("Rotation " + degrees + " in " + context + " is not a quarter turn");
        };
    }

    /** One model drawn for a state, with its blockstate rotation. */
    record Placement(ResourceLocation model, Quadrant x, Quadrant y) {

        /** The rotation the game bakes into the model, about the origin; see {@link ShapeGeometry#aboutCentre}. */
        Matrix4f rotation() {
            return new Matrix4f(OctahedralGroup.fromXYAngles(this.x, this.y).transformation());
        }
    }
}
