package fr.lacaleche.glue.web.bridge;

import java.util.Objects;

/**
 * A page element marked {@code data-glue-slot="name"} or {@code "name:argument"}, positioned in GUI
 * coordinates for the latest draw.
 */
public record WebSlot(String name, String argument, int x, int y, int width, int height) {

    public WebSlot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(argument, "argument");
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= this.x && pointX < this.x + this.width && pointY >= this.y && pointY < this.y + this.height;
    }
}
