package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code .mcsx} tag and attribute vocabulary, in one place. The binder, the element resolver and
 * the linter all have to agree on what a tag means; when each kept its own copy the copies drifted,
 * and a tag the binder could not build ({@code <option>}) still linted clean — the inverse of the
 * {@code <checkbox>} bug the linter exists to catch.
 *
 * <p>This lives in {@code core} so the linter can read it without a window. {@code ViewBinder} owns
 * the builders themselves and only borrows the names; {@code ViewBinderVocabularyTest} pins the two
 * to each other.
 */
public final class Tags {

    private Tags() {
    }

    /** Tags the binder builds a View for. Anything else must be an import or a native component. */
    public static final Set<String> BUILT_IN = Set.of(
            "div", "button", "text", "input", "scroll", "icon",
            "if", "for", "slot", "overlay", "key");

    /**
     * Tags whose children configure their parent instead of rendering: {@code <variants>}/{@code
     * <case>} carry the cva-style class table, {@code <state>} declares a local signal.
     */
    public static final Set<String> CONFIG = Set.of("variants", "case", "state");

    /** Attributes that name a controller method rather than a value. */
    public static final Set<String> HANDLER_ATTRIBUTES = Set.of("onClick", "onPress", "onClose");

    /** Attributes the binder reads literally, never as a scope reference. */
    public static final Set<String> LITERAL_ATTRIBUTES = Set.of(
            "as", "is", "on", "name", "from", "initial", "combo", "placement", "modal");

    /** Every tag that may legally appear in a document without being imported or registered. */
    public static final Set<String> KNOWN =
            Stream.concat(BUILT_IN.stream(), CONFIG.stream()).collect(Collectors.toUnmodifiableSet());
}
