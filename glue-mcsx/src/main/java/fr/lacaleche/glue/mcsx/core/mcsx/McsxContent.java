package fr.lacaleche.glue.mcsx.core.mcsx;

/**
 * A node of {@code .mcsx} content: either an {@link McsxElement} (a tag) or an
 * {@link McsxText} run (literal text interleaved with {@code {{binding}}} references).
 */
public sealed interface McsxContent permits McsxElement, McsxText {
}
