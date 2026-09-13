package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.lumos.Light;

import java.util.Locale;

/**
 * One demo light as the studio presents it: the live {@link Light} plus the measurements the UI
 * shows. {@code Light} has no {@code equals}, so the instance itself is the studio's identity for a
 * light &mdash; it is the keyed-list key and the selection value.
 */
record StudioLight(Light light, int slot, double distance) {

    String label() {
        return switch (this.light.type) {
            case POINT -> "Point " + this.slot;
            case SPOT -> "Spot " + this.slot;
            case GOBO -> "Gobo " + this.slot;
        };
    }

    String detail() {
        return String.format(
                Locale.ROOT,
                "%.0f range · %.1f power · %s",
                this.light.range,
                this.light.intensity,
                this.light.castsShadow ? "shadowed" : "no shadow"
        );
    }

    String distanceText() {
        return String.format(Locale.ROOT, "%.0fm", this.distance);
    }

    int color() {
        return 0xff000000
                | channel(this.light.r) << 16
                | channel(this.light.g) << 8
                | channel(this.light.b);
    }

    static int channel(float value) {
        return Math.clamp(Math.round(value * 255f), 0, 255);
    }

    /** A {@link Light} cone-angle cosine as the half-angle in degrees the UI and edits work with. */
    static float coneDegrees(float cosine) {
        return (float) Math.toDegrees(Math.acos(Math.clamp(cosine, -1f, 1f)));
    }
}
