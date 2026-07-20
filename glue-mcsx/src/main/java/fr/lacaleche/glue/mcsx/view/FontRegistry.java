package fr.lacaleche.glue.mcsx.view;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import fr.lacaleche.glue.mcsx.Mcsx;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.graphics.text.LayoutCache;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.widget.TextView;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resource-pack font registry. A file at {@code assets/ns/fonts/name.ttf} is exposed as
 * {@code ns:name}; an adjacent JSON file may select a system font instead and map semantic icon
 * names to Unicode code points. Resource reloads replace the immutable registry snapshot and rebind
 * every live text view on the ModernUI thread.
 */
public final class FontRegistry extends SimplePreparableReloadListener<Map<String, FontRegistry.Font>>
        implements IdentifiableResourceReloadListener {

    public static final String DEFAULT_ICONS = "mcsx:icons";

    private static final Gson GSON = new Gson();
    private static final FontRegistry INSTANCE = new FontRegistry();

    private final Map<TextView, Binding> bindings = new WeakHashMap<>();
    private volatile Map<String, Font> fonts;

    private FontRegistry() {
        fonts = loadBundledDefaults();
    }

    public static FontRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation getFabricId() {
        return Mcsx.id("fonts");
    }

    @Override
    protected Map<String, Font> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Typeface> typefaces = loadTypefaceFiles(manager);
        Map<String, Font> loaded = new HashMap<>();
        for (Map.Entry<String, Typeface> entry : typefaces.entrySet()) {
            loaded.put(entry.getKey(), new Font(entry.getValue(), Map.of()));
        }

        Map<ResourceLocation, Resource> configs = manager.listResources(
                "fonts", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : configs.entrySet()) {
            String id = fontId(entry.getKey(), ".json");
            FontConfig config = readConfig(entry.getKey(), entry.getValue());
            Typeface typeface = config.system == null
                    ? typefaces.get(id) : systemTypeface(config.system, entry.getKey());
            if (typeface == null && config.required) {
                throw new IllegalStateException("font config " + entry.getKey()
                        + " requires a matching .ttf or .otf file");
            }
            if (typeface == null) {
                typeface = Typeface.SANS_SERIF;
            }
            loaded.put(id, new Font(typeface, parseGlyphs(config.glyphs, entry.getKey())));
        }
        Mcsx.LOGGER.info("Prepared {} resource-pack fonts ({} font files, {} descriptors)",
                loaded.size(), typefaces.size(), configs.size());
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<String, Font> prepared, ResourceManager manager, ProfilerFiller profiler) {
        int boundViews;
        synchronized (bindings) {
            for (Binding binding : bindings.values()) {
                validate(prepared, binding);
            }
            fonts = prepared;
            boundViews = bindings.size();
        }
        Mcsx.LOGGER.info("Applied {} fonts; rebinding {} live text views", prepared.size(), boundViews);
        if (Core.getUiHandlerAsync() != null) {
            Core.postOnUiThread(this::rebindAll);
        }
    }

    /** Applies a resource-pack font, or the default sans face when {@code id} is null. */
    public void bindText(TextView view, String id) {
        Binding binding = new Binding(id, null, null);
        synchronized (bindings) {
            validate(fonts, binding);
            bindings.put(view, binding);
            apply(view, binding);
        }
    }

    /** Applies a font and resolves either a semantic glyph name or an explicit code point. */
    public void bindIcon(TextView view, String fontId, String name, String glyph) {
        Binding binding = new Binding(fontId, name, glyph);
        synchronized (bindings) {
            validate(fonts, binding);
            bindings.put(view, binding);
            apply(view, binding);
        }
    }

    public boolean hasGlyph(String fontId, String name) {
        Font font = fonts.get(fontId);
        return font != null && font.glyphs().containsKey(name);
    }

    public List<String> glyphNames(String fontId) {
        Font font = fonts.get(fontId);
        return font == null ? List.of() : font.glyphs().keySet().stream().sorted().toList();
    }

    public String glyph(String fontId, String name) {
        Font font = fonts.get(fontId);
        if (font == null) {
            throw new IllegalArgumentException("unknown font '" + fontId + "'");
        }
        String glyph = font.glyphs().get(name);
        if (glyph == null) {
            throw new IllegalArgumentException("unknown glyph '" + name + "'");
        }
        return glyph;
    }

    private void rebindAll() {
        Core.checkUiThread();
        LayoutCache.clear();
        synchronized (bindings) {
            for (Map.Entry<TextView, Binding> entry : bindings.entrySet()) {
                apply(entry.getKey(), entry.getValue());
            }
        }
    }

    private void apply(TextView view, Binding binding) {
        if (binding.fontId() == null) {
            view.setTypeface(Typeface.SANS_SERIF);
            return;
        }
        Font font = validate(fonts, binding);
        view.setTypeface(font.typeface());
        if (binding.name() != null || binding.glyph() != null) {
            view.setText(resolveGlyph(font, binding));
        }
    }

    private static Font validate(Map<String, Font> available, Binding binding) {
        if (binding.fontId() == null) {
            return null;
        }
        Font font = available.get(binding.fontId());
        if (font == null) {
            throw new IllegalArgumentException("unknown font '" + binding.fontId() + "'");
        }
        if (binding.name() != null && !font.glyphs().containsKey(binding.name())) {
            throw new IllegalArgumentException("unknown glyph '" + binding.name() + "'");
        }
        if (binding.glyph() != null) {
            codePoint(binding.glyph(), "glyph");
        }
        return font;
    }

    private static String resolveGlyph(Font font, Binding binding) {
        if (binding.glyph() != null) {
            return codePoint(binding.glyph(), "glyph");
        }
        String glyph = font.glyphs().get(binding.name());
        if (glyph == null) {
            throw new IllegalArgumentException("unknown glyph '" + binding.name() + "'");
        }
        return glyph;
    }

    private static Map<String, Typeface> loadTypefaceFiles(ResourceManager manager) {
        Map<String, Typeface> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("fonts", id ->
                id.getPath().endsWith(".ttf") || id.getPath().endsWith(".otf"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            String extension = entry.getKey().getPath().endsWith(".ttf") ? ".ttf" : ".otf";
            String id = fontId(entry.getKey(), extension);
            if (loaded.containsKey(id)) {
                throw new IllegalStateException("multiple font files define '" + id + "'");
            }
            try (InputStream stream = entry.getValue().open()) {
                loaded.put(id, withSansFallback(FontFamily.createFamilies(stream, false)));
                Mcsx.LOGGER.info("Loaded font '{}' from {}", id, entry.getKey());
            } catch (Exception e) {
                throw new IllegalStateException("cannot load font " + entry.getKey(), e);
            }
        }
        return loaded;
    }

    private static FontConfig readConfig(ResourceLocation id, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            FontConfig config = GSON.fromJson(reader, FontConfig.class);
            return config != null ? config : new FontConfig();
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("cannot load font config " + id, e);
        }
    }

    private static Map<String, Font> loadBundledDefaults() {
        ResourceLocation id = Mcsx.id("fonts/icons.json");
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        String fontPath = "assets/mcsx/fonts/icons.ttf";
        ClassLoader loader = FontRegistry.class.getClassLoader();
        try (InputStream descriptor = loader.getResourceAsStream(path);
             InputStream fontStream = loader.getResourceAsStream(fontPath)) {
            if (descriptor == null) {
                throw new IllegalStateException("missing bundled font descriptor " + path);
            }
            if (fontStream == null) {
                throw new IllegalStateException("missing bundled icon font " + fontPath);
            }
            try (Reader reader = new InputStreamReader(descriptor, StandardCharsets.UTF_8)) {
                FontConfig config = GSON.fromJson(reader, FontConfig.class);
                Map<String, String> glyphs = parseGlyphs(config.glyphs, id);
                Mcsx.LOGGER.info("Loaded bundled font '{}' with {} glyph names from {}",
                        DEFAULT_ICONS, glyphs.size(), fontPath);
                return Map.of(DEFAULT_ICONS, new Font(
                        withSansFallback(FontFamily.createFamilies(fontStream, false)), glyphs));
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot load bundled icon font", e);
        }
    }

    /**
     * A typeface that tries {@code custom} first and the default sans face after, so a codepoint the
     * custom font lacks still renders. Both loaders go through this: the bundled icon font used to
     * skip the fallback, which made any missing codepoint a blank box instead of readable text.
     */
    private static Typeface withSansFallback(FontFamily[] custom) {
        List<FontFamily> families = new ArrayList<>(List.of(custom));
        families.addAll(Typeface.SANS_SERIF.getFamilies());
        return Typeface.createTypeface(families.toArray(FontFamily[]::new));
    }

    private static Typeface systemTypeface(String family, ResourceLocation config) {
        String normalized = family.toLowerCase();
        Typeface typeface;
        if (normalized.equals("sans") || normalized.equals("sans-serif") || normalized.equals("sansserif")) {
            typeface = Typeface.SANS_SERIF;
        } else if (normalized.equals("serif")) {
            typeface = Typeface.SERIF;
        } else if (normalized.equals("mono") || normalized.equals("monospace")
                || normalized.equals("monospaced")) {
            typeface = Typeface.MONOSPACED;
        } else if (FontFamily.getSystemFontMap().containsKey(family)
                || FontFamily.getSystemFontAliases().containsKey(family)) {
            typeface = Typeface.getSystemFont(family);
        } else {
            throw new IllegalStateException("unknown system font '" + family + "' in " + config);
        }
        return typeface;
    }

    private static Map<String, String> parseGlyphs(Map<String, String> glyphs, ResourceLocation config) {
        if (glyphs == null) {
            return Map.of();
        }
        Map<String, String> parsed = new HashMap<>();
        for (Map.Entry<String, String> entry : glyphs.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new IllegalStateException("blank glyph name in " + config);
            }
            try {
                parsed.put(entry.getKey(), codePoint(entry.getValue(), config.toString()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return Map.copyOf(parsed);
    }

    private static String codePoint(String value, String source) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("blank glyph value in " + source);
        }
        String raw = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        if (raw.startsWith("U+") || raw.startsWith("u+")) {
            raw = raw.substring(2);
        }
        try {
            int point = Integer.parseInt(raw, 16);
            if (!Character.isValidCodePoint(point)) {
                throw new NumberFormatException();
            }
            return new String(Character.toChars(point));
        } catch (NumberFormatException e) {
            if (value.codePointCount(0, value.length()) == 1) {
                return value;
            }
            throw new IllegalArgumentException("invalid glyph '" + value + "' in " + source);
        }
    }

    private static String fontId(ResourceLocation resource, String extension) {
        String path = resource.getPath();
        String name = path.substring("fonts/".length(), path.length() - extension.length());
        return resource.getNamespace() + ":" + name;
    }

    record Font(Typeface typeface, Map<String, String> glyphs) {
    }

    private record Binding(String fontId, String name, String glyph) {
    }

    private static final class FontConfig {
        private String system;
        private boolean required;
        private Map<String, String> glyphs;
    }
}
