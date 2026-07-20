package fr.lacaleche.glue.mcsx.core.mcsx;

/**
 * A lexical token produced by {@link McsxTokenizer}. {@code line}/{@code column} are 1-based
 * and point at the first character of the token.
 *
 * @param value for {@code STRING} the unquoted contents; for {@code BINDING}/{@code INTERP}
 *              the trimmed reference; for {@code TAG_NAME}/{@code ATTR_NAME} the name; for
 *              {@code TEXT}/{@code COMMENT} the raw run; for punctuation the literal glyph
 */
public record Token(Type type, String value, int line, int column) {

    public enum Type {
        /** {@code <} opening a start tag. */
        LT,
        /** {@code </} opening an end tag. */
        LT_SLASH,
        /** the tag name immediately after {@code <} or {@code </}. */
        TAG_NAME,
        /** an attribute name inside a tag. */
        ATTR_NAME,
        /** {@code =} between an attribute name and its value. */
        EQ,
        /** a quoted attribute value (value = contents, quotes stripped). */
        STRING,
        /** an attribute binding {@code {ref}} (value = trimmed ref). */
        BINDING,
        /** {@code >} closing a tag. */
        TAG_END,
        /** {@code />} closing a self-closing tag. */
        TAG_SELF_END,
        /** a literal text run in content (value = raw, un-normalized). */
        TEXT,
        /** a text interpolation {@code {{ref}}} (value = trimmed ref). */
        INTERP,
        /** an {@code <!-- … -->} comment (value = inner text). */
        COMMENT,
        /** end of input. */
        EOF
    }
}
