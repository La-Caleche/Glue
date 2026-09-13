package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Indicates persisted layout data that cannot be safely interpreted. */
@Environment(EnvType.CLIENT)
public class DockLayoutException extends RuntimeException {

    public DockLayoutException(String message) {
        super(message);
    }

    public DockLayoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
