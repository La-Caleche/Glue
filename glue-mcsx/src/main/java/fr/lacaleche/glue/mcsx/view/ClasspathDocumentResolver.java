package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.mcsx.DocumentLoader;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;

import java.util.HashMap;
import java.util.Map;

/**
 * The concrete {@link ViewBinder.DocumentResolver}: resolves component ids to {@code .mcsx}
 * documents loaded from the classpath (see {@link DocumentLoader}), caching parsed results so a
 * component imported many times is parsed once. Not thread-safe — used on the UI/render thread.
 */
public final class ClasspathDocumentResolver implements ViewBinder.DocumentResolver {

    private final ClassLoader loader;
    private final Map<String, McsxDocument> cache = new HashMap<>();

    public ClasspathDocumentResolver() {
        this(ClasspathDocumentResolver.class.getClassLoader());
    }

    public ClasspathDocumentResolver(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public McsxDocument resolve(String id) {
        return cache.computeIfAbsent(id, key -> DocumentLoader.load(key, loader));
    }
}
