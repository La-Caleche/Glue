package fr.lacaleche.glue.mcsx.client.theme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;

/**
 * A typed semantic theme key. Token lookup uses instance identity rather than the token name.
 */
@Environment(EnvType.CLIENT)
public final class Token<T> {

    private final String name;
    private final T fallback;

    private Token(String name, T fallback) {
        if (name.isBlank()) throw new IllegalArgumentException("Token name cannot be blank");

        this.name = name;
        this.fallback = fallback;
    }

    public static <T> Token<T> of(String name, T fallback) {
        return new Token<>(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(fallback, "fallback")
        );
    }

    public String name() {
        return this.name;
    }

    public T fallback() {
        return this.fallback;
    }

    @Override
    public String toString() {
        return "@" + this.name;
    }
}
