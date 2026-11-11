package fr.lacaleche.glue.client.options;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A mod's graphics settings, described once and shown both as a screen opened from the game's video settings
 * and, while Sodium is installed, as a page of Sodium's settings screen. Register it with
 * {@link GraphicsOptions#register}.
 *
 * <p>The settings are one immutable value {@code S} with value equality, a record most often. {@code saved}
 * gives the value now in force; {@code apply} puts a new one in force and saves it. The vanilla screen applies
 * each change as it is made; Sodium's page applies the edited value once, on its Apply button. Each row reads its
 * value from {@code S} and writes it into a copy.
 *
 * <p>The page's texts are the translations of keys under its prefix {@code p}: {@code p.title}, the vanilla
 * screen's title; {@code p.page}, Sodium's tab; {@code p.button} and {@code p.button.tooltip}, the button in the
 * video settings.
 *
 * @param <S> the settings value, immutable
 */
public final class GraphicsOptionsPage<S> {

    private final String keyPrefix;
    private final Supplier<S> saved;
    private final Consumer<S> apply;
    private final List<Section<S>> sections;

    private GraphicsOptionsPage(String keyPrefix, Supplier<S> saved, Consumer<S> apply, List<Section<S>> sections) {
        this.keyPrefix = keyPrefix;
        this.saved = saved;
        this.apply = apply;
        this.sections = List.copyOf(sections);
    }

    /** A page whose texts are under {@code keyPrefix}, over the settings {@code saved} gives and {@code apply} sets. */
    public static <S> Builder<S> builder(String keyPrefix, Supplier<S> saved, Consumer<S> apply) {
        return new Builder<>(Objects.requireNonNull(keyPrefix), Objects.requireNonNull(saved),
                Objects.requireNonNull(apply));
    }

    /** The translation key {@code name} under this page's prefix. */
    public String key(String name) {
        return keyPrefix + "." + name;
    }

    /** The settings now in force. */
    public S saved() {
        return saved.get();
    }

    /** Puts {@code settings} in force and saves them. */
    public void apply(S settings) {
        apply.accept(settings);
    }

    public List<Section<S>> sections() {
        return sections;
    }

    /** Rows under a title, or heading the page untitled. */
    public record Section<S>(Optional<Component> title, List<OptionRow<S>> rows) {

        public Section {
            rows = List.copyOf(rows);
        }
    }

    /** Builds a page section by section; rows go to the section last opened. */
    public static final class Builder<S> {

        private final String keyPrefix;
        private final Supplier<S> saved;
        private final Consumer<S> apply;
        private final List<Section<S>> sections = new ArrayList<>();
        private Optional<Component> title = Optional.empty();
        private List<OptionRow<S>> rows;

        private Builder(String keyPrefix, Supplier<S> saved, Consumer<S> apply) {
            this.keyPrefix = keyPrefix;
            this.saved = saved;
            this.apply = apply;
        }

        /** Opens an untitled section, the page's head most often. */
        public Builder<S> section() {
            return open(Optional.empty());
        }

        /** Opens a section under {@code title}. */
        public Builder<S> section(Component title) {
            return open(Optional.of(title));
        }

        public Builder<S> slider(String key, int min, int max, int step, IntFunction<Component> label,
                                 Function<S, Integer> get, BiFunction<S, Integer, S> with, OptionImpact impact) {
            return row(new OptionRow.Slider<>(key, min, max, step, label, get, with, impact, false));
        }

        public Builder<S> toggle(String key, Function<S, Boolean> get, BiFunction<S, Boolean, S> with,
                                 OptionImpact impact) {
            return row(new OptionRow.Toggle<>(key, get, with, impact, false));
        }

        public <E extends Enum<E>> Builder<S> choice(String key, Class<E> type, Function<E, Component> label,
                                                     Function<S, E> get, BiFunction<S, E, S> with,
                                                     OptionImpact impact) {
            return row(new OptionRow.Choice<>(key, type, label, get, with, impact, false));
        }

        public Builder<S> level(String key, Function<S, Component> label, BiFunction<S, Boolean, S> cycle) {
            return row(new OptionRow.Level<>(key, label, cycle));
        }

        /** Sets the row last added beside the next half row on the vanilla screen. */
        public Builder<S> half() {
            if (rows == null || rows.isEmpty()) throw new IllegalStateException("half() follows a row");
            if (rows.getLast() instanceof OptionRow.Level<S> level) {
                throw new IllegalStateException(level.key() + ": a level row takes a whole line");
            }
            rows.set(rows.size() - 1, rows.getLast().asHalf());
            return this;
        }

        public Builder<S> row(OptionRow<S> row) {
            if (rows == null) throw new IllegalStateException("A row goes in a section: call section() first");
            rows.add(Objects.requireNonNull(row));
            return this;
        }

        public GraphicsOptionsPage<S> build() {
            close();
            if (sections.isEmpty()) throw new IllegalStateException(keyPrefix + ": a page has a row at least");
            return new GraphicsOptionsPage<>(keyPrefix, saved, apply, sections);
        }

        private Builder<S> open(Optional<Component> next) {
            close();
            title = next;
            rows = new ArrayList<>();
            return this;
        }

        private void close() {
            if (rows != null && !rows.isEmpty()) sections.add(new Section<>(title, rows));
            rows = null;
        }
    }
}
