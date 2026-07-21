package fr.lacaleche.glue.mcsx.core.dock;

/** A layout file that cannot be understood; the caller falls back to the default layout. */
public class DockLayoutException extends RuntimeException {

    public DockLayoutException(String message) {
        super(message);
    }

    public DockLayoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
