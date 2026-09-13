package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeDefinition;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeTokenRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
final class ThemeResolver {

    private ThemeResolver() {
    }

    static Map<ResourceLocation, Theme> resolve(Map<ResourceLocation, ThemeDefinition> definitions) {
        Map<ResourceLocation, Map<String, ThemeDefinition.Declaration>> merged = new HashMap<>();
        Map<ResourceLocation, Theme> result = new LinkedHashMap<>();
        for (ResourceLocation resource : definitions.keySet()) {
            Map<String, ThemeDefinition.Declaration> declarations = merge(resource, definitions, merged, new ArrayList<>());
            boolean controlOnDeclared = declares(resource, ThemeTokens.CONTROL_ON.name(), definitions);
            boolean accentDeclared = declares(resource, ThemeTokens.ACCENT.name(), definitions);
            result.put(resource, flatten(resource, declarations, controlOnDeclared, accentDeclared));
        }
        return Map.copyOf(result);
    }

    private static Map<String, ThemeDefinition.Declaration> merge(
            ResourceLocation resource,
            Map<ResourceLocation, ThemeDefinition> definitions,
            Map<ResourceLocation, Map<String, ThemeDefinition.Declaration>> merged,
            List<ResourceLocation> path
    ) {
        Map<String, ThemeDefinition.Declaration> cached = merged.get(resource);
        if (cached != null) return cached;
        int cycleStart = path.indexOf(resource);
        if (cycleStart >= 0) {
            List<ResourceLocation> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(resource);
            throw new IllegalArgumentException("MCSX theme parent cycle: " + cycle);
        }

        ThemeDefinition definition = definitions.get(resource);
        if (definition == null) throw new IllegalArgumentException("Missing MCSX theme parent " + resource);
        path.add(resource);
        Map<String, ThemeDefinition.Declaration> values = definition.parent() == null
                ? baseline()
                : new LinkedHashMap<>(merge(definition.parent(), definitions, merged, path));
        values.putAll(definition.values());
        path.removeLast();
        Map<String, ThemeDefinition.Declaration> immutable = Map.copyOf(values);
        merged.put(resource, immutable);
        return immutable;
    }

    private static Theme flatten(
            ResourceLocation resource,
            Map<String, ThemeDefinition.Declaration> declarations,
            boolean controlOnDeclared,
            boolean accentDeclared
    ) {
        Map<String, Object> values = new HashMap<>();
        for (String token : declarations.keySet()) {
            resolveValue(resource, token, declarations, values, new ArrayList<>());
        }

        Theme.Builder builder = Theme.builder();
        for (ThemeTokenRegistry.Entry entry : ThemeTokenRegistry.entries()) {
            set(builder, entry.token(), values.get(entry.name()));
        }
        String checkboxColor = !controlOnDeclared && accentDeclared
                ? ThemeTokens.ACCENT.name()
                : ThemeTokens.CONTROL_ON.name();
        builder.set(ThemeTokens.CHECKBOX_INDICATOR, ThemeTokens.checkboxIndicator(
                (Integer) values.get(checkboxColor),
                (Integer) values.get(ThemeTokens.TEXT_PRIMARY.name()),
                (Integer) values.get(ThemeTokens.TEXT_MUTED.name())
        ));
        return builder.build();
    }

    private static boolean declares(
            ResourceLocation resource,
            String token,
            Map<ResourceLocation, ThemeDefinition> definitions
    ) {
        ThemeDefinition definition = definitions.get(resource);
        if (definition.values().containsKey(token)) return true;

        return definition.parent() != null && declares(definition.parent(), token, definitions);
    }

    private static Object resolveValue(
            ResourceLocation resource,
            String token,
            Map<String, ThemeDefinition.Declaration> declarations,
            Map<String, Object> values,
            List<String> path
    ) {
        Object cached = values.get(token);
        if (cached != null) return cached;
        int cycleStart = path.indexOf(token);
        if (cycleStart >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(token);
            throw new IllegalArgumentException("Invalid MCSX theme " + resource + ": token reference cycle " + cycle);
        }
        ThemeDefinition.Declaration declaration = declarations.get(token);
        if (declaration == null) {
            throw new IllegalArgumentException("Invalid MCSX theme " + resource + ": missing value for token '" + token + "'");
        }
        path.add(token);
        Object value = resolveDeclaration(resource, declaration, declarations, values, path);
        path.removeLast();
        values.put(token, value);
        return value;
    }

    private static Object resolveDeclaration(
            ResourceLocation resource,
            ThemeDefinition.Declaration declaration,
            Map<String, ThemeDefinition.Declaration> declarations,
            Map<String, Object> values,
            List<String> path
    ) {
        return switch (declaration) {
            case ThemeDefinition.Literal literal -> literal.value();
            case ThemeDefinition.Reference reference -> resolveValue(resource, reference.token(), declarations, values, path);
            case ThemeDefinition.Alpha alpha -> (alpha.alpha() << 24)
                    | ((Integer) resolveDeclaration(resource, alpha.color(), declarations, values, path) & 0x00ffffff);
        };
    }

    private static Map<String, ThemeDefinition.Declaration> baseline() {
        Map<String, ThemeDefinition.Declaration> values = new LinkedHashMap<>();
        Theme dark = Themes.mcsx();
        for (ThemeTokenRegistry.Entry entry : ThemeTokenRegistry.entries()) {
            values.put(entry.name(), new ThemeDefinition.Literal(dark.get(entry.token())));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(Theme.Builder builder, Token<?> token, Object value) {
        builder.set((Token<T>) token, (T) value);
    }
}
