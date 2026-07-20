package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.List;

/**
 * A run of text: an ordered list of {@link Part}s, each either a literal string or a
 * {@code {{binding}}} reference. Whitespace has already been normalized by the parser.
 */
public record McsxText(List<Part> parts) implements McsxContent {

    public McsxText {
        parts = List.copyOf(parts);
    }

    /**
     * One piece of a text run.
     *
     * @param value   the literal text, or the reference expression when {@code binding}
     * @param binding {@code true} when this part came from a {@code {{ref}}} interpolation
     */
    public record Part(String value, boolean binding) {
    }
}
