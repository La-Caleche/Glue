package fr.lacaleche.glue.web.internal;

import fr.lacaleche.glue.web.WebSurface;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable acquisition options; browser state never retains a mutable public builder. */
public record SurfaceOptions(URI address, int width, int height, int frameRate, boolean transparent,
                             WebOrigin origin, Consumer<String> messages) {

    public SurfaceOptions {
        address = address(address);
        validateSize(width, height);
        validateFrameRate(frameRate);
        if ((origin == null) != (messages == null)) throw new IllegalArgumentException("Message origin and handler must be supplied together");
    }

    public static URI address(URI address) {
        Objects.requireNonNull(address, "address");
        if (!address.isAbsolute()) throw new IllegalArgumentException("Browser address must be absolute");
        return address;
    }

    public static void validateSize(int width, int height) {
        if (width < 1 || height < 1 || width > WebSurface.MAX_DIMENSION || height > WebSurface.MAX_DIMENSION) {
            throw new IllegalArgumentException("Browser dimensions must be in 1.." + WebSurface.MAX_DIMENSION);
        }
    }

    public static void validateFrameRate(int fps) {
        if (fps < 1 || fps > 120) throw new IllegalArgumentException("Browser frame rate must be in 1..120");
    }
}
