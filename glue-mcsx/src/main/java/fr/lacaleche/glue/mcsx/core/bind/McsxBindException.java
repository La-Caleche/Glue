package fr.lacaleche.glue.mcsx.core.bind;

/**
 * Thrown by the binder when a well-formed document cannot be turned into a live View tree —
 * an unknown tag, an unresolvable binding, a malformed control-flow attribute, etc. Positioned
 * where the source element sits (1-based); {@link #getMessage()} already includes the position.
 */
public final class McsxBindException extends RuntimeException {

    public McsxBindException(String message) {
        super(message);
    }

    public McsxBindException(String message, int line, int column) {
        super(message + " at line " + line + ", column " + column);
    }
}
