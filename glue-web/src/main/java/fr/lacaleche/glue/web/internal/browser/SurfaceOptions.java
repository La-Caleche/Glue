package fr.lacaleche.glue.web.internal.browser;

import fr.lacaleche.glue.web.internal.bridge.BridgeSettings;

import fr.lacaleche.glue.web.WebSurface;

import java.net.URI;
import java.util.Objects;

/**
 * Immutable acquisition options. Width and height are CSS pixels; the scale is the device pixel
 * ratio that maps them to browser pixels.
 */
public record SurfaceOptions(URI address, int width, int height, double scale, int frameRate, boolean transparent,
                             BridgeSettings bridge) {

    public static final double MAX_SCALE = 8;

    public SurfaceOptions {
        address = address(address);
        validateSize(width, height);
        validateScale(width, height, scale);
        validateFrameRate(frameRate);
        Objects.requireNonNull(bridge, "bridge");
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

    public static void validateScale(int width, int height, double scale) {
        if (!Double.isFinite(scale) || scale <= 0 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Surface scale must be in (0, " + MAX_SCALE + "]");
        }
        // Chromium rounds the scaled viewport up to whole browser pixels.
        if (Math.ceil(width * scale) > WebSurface.MAX_DIMENSION || Math.ceil(height * scale) > WebSurface.MAX_DIMENSION) {
            throw new IllegalArgumentException("Scaled browser dimensions must not exceed " + WebSurface.MAX_DIMENSION);
        }
    }

    /** The preferred scale, reduced when needed so the scaled viewport stays within the limit. */
    public static double fitScale(int width, int height, double preferred) {
        double limit = (WebSurface.MAX_DIMENSION - 0.5) / Math.max(width, height);
        return Math.min(Math.min(preferred, MAX_SCALE), limit);
    }

    public static void validateFrameRate(int fps) {
        if (fps < 1 || fps > 120) throw new IllegalArgumentException("Browser frame rate must be in 1..120");
    }
}
