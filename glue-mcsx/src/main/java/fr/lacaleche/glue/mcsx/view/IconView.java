package fr.lacaleche.glue.mcsx.view;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.TextView;

/**
 * A centered glyph from a resource-pack font. It remains a TextView so measurement, font
 * rasterization, scaling, fallback and resource reload behavior are shared with ordinary text.
 */
public final class IconView extends TextView {

    private int iconSize;
    private int color = 0xFFFFFFFF;

    public IconView(Context context, int iconSize) {
        super(context);
        this.iconSize = iconSize;
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setTextSize(iconSize);
        setTextColor(color);
    }

    public IconView(Context context, String font, String name, String glyph, int iconSize) {
        this(context, iconSize);
        FontRegistry.getInstance().bindIcon(this, font, name, glyph);
    }

    public int color() {
        return color;
    }

    public void setColor(int argb) {
        if (color != argb) {
            color = argb;
            setTextColor(argb);
        }
    }

    public void setIcon(String font, String name, String glyph) {
        FontRegistry.getInstance().bindIcon(this, font, name, glyph);
    }

    public void setIconSize(int size) {
        if (iconSize != size) {
            iconSize = size;
            setTextSize(size);
            requestLayout();
        }
    }

}
