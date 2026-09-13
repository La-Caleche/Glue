package fr.lacaleche.glue.mcsx.client.theme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class Theme {

    private final Map<Token<?>, Object> values;

    private Theme(Map<Token<?>, Object> values) {
        this.values = new IdentityHashMap<>(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T get(Token<T> token) {
        Objects.requireNonNull(token, "token");
        Object value = this.values.get(token);
        if (value == null) return token.fallback();

        return tokenValue(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T tokenValue(Object value) {
        return (T) value;
    }

    public static final class Builder {

        private final Map<Token<?>, Object> values = new IdentityHashMap<>();

        private Builder() {
        }

        public <T> Builder set(Token<T> token, T value) {
            this.values.put(
                    Objects.requireNonNull(token, "token"),
                    Objects.requireNonNull(value, "value")
            );
            return this;
        }

        public Theme build() {
            return new Theme(this.values);
        }
    }
}
