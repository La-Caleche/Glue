package fr.lacaleche.glue.mcsx.client.theme.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StrictJsonParser;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class ThemeJsonParser {

    private static final Set<String> DOCK_METRIC_FIELDS = Set.of(
            "gutter",
            "splitter-size",
            "header-height",
            "corner-radius",
            "border-width",
            "tab-text-size",
            "control-text-size",
            "tab-padding",
            "icon-gap",
            "tab-close-width",
            "control-width"
    );

    private ThemeJsonParser() {
    }

    public static ThemeDefinition parse(ResourceLocation resource, Reader reader) {
        JsonElement parsed;
        try {
            parsed = StrictJsonParser.parse(reader);
        } catch (RuntimeException exception) {
            throw error(resource, "invalid JSON", exception);
        }
        if (!parsed.isJsonObject()) throw error(resource, "root must be an object");

        JsonObject root = parsed.getAsJsonObject();
        rejectUnknownFields(resource, "root", root, Set.of("parent", "values"));
        if (!root.has("values") || !root.get("values").isJsonObject()) {
            throw error(resource, "required field 'values' must be an object");
        }

        ResourceLocation parent = null;
        if (root.has("parent")) {
            JsonElement value = root.get("parent");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw error(resource, "field 'parent' must be a resource location string");
            }
            try {
                parent = ResourceLocation.parse(value.getAsString());
            } catch (RuntimeException exception) {
                throw error(resource, "invalid parent resource location '" + value.getAsString() + "'", exception);
            }
        }

        Map<String, ThemeDefinition.Declaration> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> value : root.getAsJsonObject("values").entrySet()) {
            ThemeTokenRegistry.Entry token = ThemeTokenRegistry.find(value.getKey());
            if (token == null) throw error(resource, "unknown theme token '" + value.getKey() + "'");

            values.put(value.getKey(), parseDeclaration(resource, token, value.getValue()));
        }
        return new ThemeDefinition(parent, Map.copyOf(values));
    }

    private static ThemeDefinition.Declaration parseDeclaration(
            ResourceLocation resource,
            ThemeTokenRegistry.Entry token,
            JsonElement value
    ) {
        if (token.kind() == ThemeTokenRegistry.Kind.COLOR) {
            return parseColorDeclaration(resource, token.name(), value);
        }
        if (token.kind() == ThemeTokenRegistry.Kind.DOCK_METRICS) {
            return parseDockMetrics(resource, token.name(), value);
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) return reference(resource, token, primitive.getAsString());
            if (primitive.isNumber()) {
                int dimension = exactInteger(resource, token.name(), primitive);
                if (dimension < 0) throw error(resource, "token '" + token.name() + "' cannot be negative");
                return new ThemeDefinition.Literal(dimension);
            }
        }
        throw error(resource, "token '" + token.name() + "' requires a non-negative integer or @dimension-token");
    }

    private static ThemeDefinition.Literal parseDockMetrics(
            ResourceLocation resource,
            String token,
            JsonElement value
    ) {
        if (!value.isJsonObject()) throw error(resource, "token '" + token + "' requires an object");

        JsonObject object = value.getAsJsonObject();
        rejectUnknownFields(resource, "token '" + token + "'", object, DOCK_METRIC_FIELDS);
        for (String field : DOCK_METRIC_FIELDS) {
            if (!object.has(field)) throw error(resource, "token '" + token + "' is missing field '" + field + "'");
        }
        return new ThemeDefinition.Literal(new DockMetrics(
                metric(resource, token, object, "gutter"),
                metric(resource, token, object, "splitter-size"),
                metric(resource, token, object, "header-height"),
                metric(resource, token, object, "corner-radius"),
                metric(resource, token, object, "border-width"),
                metric(resource, token, object, "tab-text-size"),
                metric(resource, token, object, "control-text-size"),
                metric(resource, token, object, "tab-padding"),
                metric(resource, token, object, "icon-gap"),
                metric(resource, token, object, "tab-close-width"),
                metric(resource, token, object, "control-width")
        ));
    }

    private static int metric(ResourceLocation resource, String token, JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw error(resource, "field '" + token + "." + field + "' must be a JSON integer");
        }
        int result = exactInteger(resource, token + "." + field, value.getAsJsonPrimitive());
        if (result < 0) throw error(resource, "field '" + token + "." + field + "' cannot be negative");
        return result;
    }

    private static ThemeDefinition.Declaration parseColorDeclaration(
            ResourceLocation resource,
            String token,
            JsonElement value
    ) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return colorValue(resource, token, value.getAsString());
        }
        if (!value.isJsonObject()) {
            throw error(resource, "token '" + token + "' requires a color string or color/alpha object");
        }

        JsonObject object = value.getAsJsonObject();
        rejectUnknownFields(resource, "token '" + token + "'", object, Set.of("color", "alpha"));
        if (!object.has("color") || !object.has("alpha")) {
            throw error(resource, "token '" + token + "' color object requires 'color' and 'alpha'");
        }
        JsonElement color = object.get("color");
        JsonElement alpha = object.get("alpha");
        if (!color.isJsonPrimitive() || !color.getAsJsonPrimitive().isString()
                || !alpha.isJsonPrimitive() || !alpha.getAsJsonPrimitive().isNumber()) {
            throw error(resource, "token '" + token + "' color object has invalid field types");
        }
        int alphaValue = exactInteger(resource, token + ".alpha", alpha.getAsJsonPrimitive());
        if (alphaValue < 0 || alphaValue > 255) {
            throw error(resource, "token '" + token + "' alpha must be 0..255");
        }
        return new ThemeDefinition.Alpha(colorValue(resource, token, color.getAsString()), alphaValue);
    }

    private static ThemeDefinition.Declaration colorValue(ResourceLocation resource, String token, String value) {
        if (value.startsWith("@")) {
            return reference(resource, ThemeTokenRegistry.find(token), value);
        }
        return new ThemeDefinition.Literal(parseColor(resource, token, value));
    }

    private static ThemeDefinition.Reference reference(
            ResourceLocation resource,
            ThemeTokenRegistry.Entry target,
            String value
    ) {
        if (!value.startsWith("@") || value.length() == 1) {
            throw error(resource, "token '" + target.name() + "' has an invalid reference '" + value + "'");
        }
        String name = value.substring(1);
        ThemeTokenRegistry.Entry referenced = ThemeTokenRegistry.find(name);
        if (referenced == null) throw error(resource, "token '" + target.name() + "' references unknown token '@" + name + "'");
        if (referenced.kind() != target.kind()) {
            throw error(resource, "token '" + target.name() + "' cannot reference " + referenced.kind().name().toLowerCase()
                    + " token '@" + name + "'");
        }
        return new ThemeDefinition.Reference(name);
    }

    private static int parseColor(ResourceLocation resource, String token, String value) {
        if (!value.startsWith("#")) throw error(resource, "token '" + token + "' requires #rgb, #rrggbb or #aarrggbb");
        String digits = value.substring(1);
        if (!digits.matches("[0-9a-fA-F]+")) throw error(resource, "token '" + token + "' has an invalid hexadecimal color");
        try {
            return switch (digits.length()) {
                case 3 -> 0xff000000
                        | Integer.parseInt("" + digits.charAt(0) + digits.charAt(0), 16) << 16
                        | Integer.parseInt("" + digits.charAt(1) + digits.charAt(1), 16) << 8
                        | Integer.parseInt("" + digits.charAt(2) + digits.charAt(2), 16);
                case 6 -> 0xff000000 | Integer.parseInt(digits, 16);
                case 8 -> (int) Long.parseLong(digits, 16);
                default -> throw error(resource, "token '" + token + "' requires #rgb, #rrggbb or #aarrggbb");
            };
        } catch (NumberFormatException exception) {
            throw error(resource, "token '" + token + "' has an invalid hexadecimal color", exception);
        }
    }

    private static int exactInteger(ResourceLocation resource, String field, JsonPrimitive value) {
        String number = value.getAsString();
        if (!number.matches("-?(0|[1-9][0-9]*)")) throw error(resource, "field '" + field + "' must be a JSON integer");
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            throw error(resource, "field '" + field + "' is outside the integer range", exception);
        }
    }

    private static void rejectUnknownFields(
            ResourceLocation resource,
            String context,
            JsonObject object,
            Set<String> supported
    ) {
        for (String field : object.keySet()) {
            if (!supported.contains(field)) throw error(resource, context + " contains unknown field '" + field + "'");
        }
    }

    private static IllegalArgumentException error(ResourceLocation resource, String message) {
        return new IllegalArgumentException("Invalid MCSX theme " + resource + ": " + message);
    }

    private static IllegalArgumentException error(ResourceLocation resource, String message, RuntimeException cause) {
        return new IllegalArgumentException("Invalid MCSX theme " + resource + ": " + message, cause);
    }
}
