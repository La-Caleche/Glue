package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.function.Consumer;

/** A scene's controls: kit rows in a panel at the top right, as tall as its rows, over the preview. */
final class ScenePanel {

    private static final int WIDTH = 190;

    private ScenePanel() {
    }

    /** Builds the rows, then fits the list to them; the screen adds the list in {@code init}. */
    static UiRowList create(int screenWidth, int screenHeight, Consumer<UiRowList> build) {
        UiStyle style = UiStyle.DEFAULT;
        int margin = style.padding();
        int maxHeight = screenHeight - 2 * margin;
        UiRowList rows = new UiRowList(new ScreenRectangle(screenWidth - WIDTH - margin, margin, WIDTH, maxHeight),
                true);
        build.accept(rows);
        rows.setRectangle(WIDTH, Math.min(maxHeight, rows.children().size() * style.rowHeight() + 4),
                rows.getX(), rows.getY());
        return rows;
    }

    /** Draws the panel behind the rows and the mouse controls at the top left, in the kit's colours. */
    static void renderHud(GuiGraphics graphics, Font font, UiRowList rows, String controls) {
        UiStyle style = UiStyle.DEFAULT;
        graphics.fill(rows.getX(), rows.getY(), rows.getRight(), rows.getBottom(), style.panel());
        graphics.drawString(font, controls, style.padding(), style.padding(), style.text());
    }
}
