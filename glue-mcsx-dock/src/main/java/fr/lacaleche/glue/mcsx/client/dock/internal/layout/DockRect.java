package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** An axis-aligned stage rectangle with a top-left origin. */
@Environment(EnvType.CLIENT)
public record DockRect(int x, int y, int width, int height) {

    public DockRect {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Rectangle dimensions cannot be negative");
    }

    public boolean contains(int pointX, int pointY) {
        return this.contains((double) pointX, pointY);
    }

    public boolean contains(double pointX, double pointY) {
        long right = (long) this.x + this.width;
        long bottom = (long) this.y + this.height;
        return pointX >= this.x && pointX < right && pointY >= this.y && pointY < bottom;
    }

    public boolean intersects(DockRect other) {
        return (long) this.x < (long) other.x + other.width
                && (long) other.x < (long) this.x + this.width
                && (long) this.y < (long) other.y + other.height
                && (long) other.y < (long) this.y + this.height;
    }

    public int right() {
        return Math.toIntExact((long) this.x + this.width);
    }

    public int bottom() {
        return Math.toIntExact((long) this.y + this.height);
    }
}
