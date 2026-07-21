package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.List;

/**
 * An element (tag) with its attributes and child content. {@code line}/{@code column} point
 * at the opening {@code <}, 1-based, for positioned bind-time errors.
 */
public record McsxElement(String tag, List<McsxAttribute> attributes,
                          List<McsxContent> children, int line, int column) implements McsxContent {

    public McsxElement {
        attributes = List.copyOf(attributes);
        children = List.copyOf(children);
    }

    /**
     * The literal value of a non-binding attribute, or {@code null} if the attribute is
     * absent <em>or</em> is a binding. Literal and binding lookups are deliberately distinct;
     * the binder iterates {@link #attributes()} directly when it needs to see bindings.
     */
    public String attribute(String name) {
        for (McsxAttribute attribute : attributes) {
            if (attribute.name().equals(name) && !attribute.binding()) {
                return attribute.value();
            }
        }
        return null;
    }
}
