package fr.lacaleche.glue.mcsx.core.style;

/**
 * Thrown when a {@code class="…"} string contains an unknown utility, variant, or malformed
 * arbitrary value. Extends {@link IllegalArgumentException} to honor the "fail loudly at bind
 * time" contract while staying distinguishable from other IAEs.
 */
public final class TailwindException extends IllegalArgumentException {

    public TailwindException(String message) {
        super(message);
    }
}
