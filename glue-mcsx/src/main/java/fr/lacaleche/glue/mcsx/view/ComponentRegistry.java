package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.mcsx.Tags;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps custom element tags to {@link NativeComponent}s. The binder handles base tags itself and
 * defers every unknown tag here; {@code .mcsx} components (via {@code <import>}) are resolved
 * separately by the {@link ViewBinder.DocumentResolver} and need no registration.
 */
public final class ComponentRegistry {

    private final Map<String, NativeComponent> components = new HashMap<>();

    public ComponentRegistry register(String tag, NativeComponent component) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("component tag must not be blank");
        }
        if (Tags.KNOWN.contains(tag)) {
            throw new IllegalArgumentException("component tag '" + tag + "' is reserved");
        }
        Objects.requireNonNull(component, "component");
        if (components.putIfAbsent(tag, component) != null) {
            throw new IllegalArgumentException("component tag '" + tag + "' is already registered");
        }
        return this;
    }

    public NativeComponent get(String tag) {
        return components.get(tag);
    }

    public boolean has(String tag) {
        return components.containsKey(tag);
    }
}
