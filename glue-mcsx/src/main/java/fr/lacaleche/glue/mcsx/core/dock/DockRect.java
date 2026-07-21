package fr.lacaleche.glue.mcsx.core.dock;

/** An axis-aligned pixel rectangle, top-left origin. */
public record DockRect(int x, int y, int w, int h) {

    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }
}
