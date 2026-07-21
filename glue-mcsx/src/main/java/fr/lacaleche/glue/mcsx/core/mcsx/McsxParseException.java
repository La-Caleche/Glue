package fr.lacaleche.glue.mcsx.core.mcsx;

/**
 * Thrown on any malformed {@code .mcsx} input, at both the tokenize and parse stages. The
 * {@code line}/{@code column} are 1-based; {@link #getMessage()} already includes the position.
 */
public final class McsxParseException extends RuntimeException {

    private final int line;
    private final int column;

    public McsxParseException(String message, int line, int column) {
        super(message + " at line " + line + ", column " + column);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
