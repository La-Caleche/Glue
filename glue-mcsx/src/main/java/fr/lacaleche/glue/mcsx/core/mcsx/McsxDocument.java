package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed document: the {@code <import name="…" from="ns:path"/>} prelude (as a
 * name → source map, insertion-ordered) and the single root element.
 */
public record McsxDocument(Map<String, String> imports, McsxElement root) {

    public McsxDocument {
        imports = Collections.unmodifiableMap(new LinkedHashMap<>(imports));
        root = Objects.requireNonNull(root, "root");
    }
}
