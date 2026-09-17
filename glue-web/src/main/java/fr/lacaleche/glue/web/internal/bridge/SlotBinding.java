package fr.lacaleche.glue.web.internal.bridge;

import fr.lacaleche.glue.web.bridge.WebSlotRenderer;

import java.util.Objects;
import java.util.regex.Pattern;

/** A native renderer for one slot name and the side of the page it draws on. */
public record SlotBinding(Layer layer, WebSlotRenderer renderer) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");

    public SlotBinding {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(renderer, "renderer");
    }

    public static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid slot name: " + name);
    }

    public enum Layer {
        BEHIND,
        ABOVE
    }
}
