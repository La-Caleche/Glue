package fr.lacaleche.glue.client.options.internal.sodium;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A graphics options page as one page of Sodium's settings screen. Sodium lays a page's groups out as untitled
 * rows and cannot scroll, so this page lays out its own: each group under its title and a rule, if it has one,
 * scrolled by the wheel once the rows outrun the screen. A row is drawn only while wholly within the space
 * between the page tabs and Sodium's buttons.
 */
@Environment(EnvType.CLIENT)
public final class SodiumGraphicsPage extends OptionPage {

    // Sodium's own layout: rows 240 by 18 pixels from (6, 28), groups 4 apart, its buttons 30 pixels above the
    // screen's foot.
    private static final int LEFT = 6;
    private static final int TOP = 28;
    private static final int ROW_WIDTH = 240;
    private static final int ROW_HEIGHT = 18;
    private static final int GROUP_GAP = 4;
    private static final int BOTTOM_MARGIN = 34;
    private static final int TITLE_HEIGHT = 14;
    private static final int RULE_GAP = 4;
    private static final int SCROLLBAR_X = LEFT + ROW_WIDTH + 2;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int TITLE_COLOUR = 0xFFFFFF55;
    private static final int RULE_COLOUR = 0x60FFFFFF;
    private static final int TRACK_COLOUR = 0x40000000;
    private static final int THUMB_COLOUR = 0xC0FFFFFF;

    private final List<Optional<Component>> titles;
    private Runnable relayout = () -> {
    };

    SodiumGraphicsPage(Component name, List<Section> sections) {
        super(name, sections.stream().map(Section::group).collect(ImmutableList.toImmutableList()));
        this.titles = sections.stream()
                .map(section -> section.title().map(title -> (Component) title.copy().withStyle(ChatFormatting.BOLD)))
                .toList();
    }

    /** The pixels a wheel notch scrolls the page by. */
    public static int scrollStep() {
        return ROW_HEIGHT;
    }

    /** How far the rows scroll on a screen {@code screenHeight} tall; 0 when they all fit. */
    public int maxScroll(int screenHeight) {
        int height = -GROUP_GAP;
        for (int i = 0; i < titles.size(); i++) {
            int title = titles.get(i).isPresent() ? TITLE_HEIGHT : 0;
            height += title + getGroups().get(i).getOptions().size() * ROW_HEIGHT + GROUP_GAP;
        }
        return Math.max(0, height - (screenHeight - BOTTOM_MARGIN - TOP));
    }

    /**
     * Lays the page out scrolled {@code scroll} pixels down a screen {@code screenHeight} tall: the controls of
     * the rows within it go to {@code controls}, the titles and the scrollbar to {@code decorations}.
     * {@code relayout} lays it out again, for {@link #relayout()}.
     */
    public void layOut(int scroll, int screenHeight, Runnable relayout, Consumer<ControlElement<?>> controls,
                       Consumer<Renderable> decorations) {
        this.relayout = relayout;
        int bottom = screenHeight - BOTTOM_MARGIN;
        int y = TOP - scroll;
        for (int i = 0; i < titles.size(); i++) {
            Optional<Component> title = titles.get(i);
            if (title.isPresent()) {
                if (y >= TOP && y + TITLE_HEIGHT <= bottom) decorations.accept(title(title.get(), y));
                y += TITLE_HEIGHT;
            }
            for (Option<?> option : getGroups().get(i).getOptions()) {
                if (y >= TOP && y + ROW_HEIGHT <= bottom) {
                    controls.accept(option.getControl().createElement(new Dim2i(LEFT, y, ROW_WIDTH, ROW_HEIGHT)));
                }
                y += ROW_HEIGHT;
            }
            y += GROUP_GAP;
        }
        int max = maxScroll(screenHeight);
        if (max > 0) decorations.accept(scrollbar(scroll, max, bottom));
    }

    /** Lays the page out again where it is scrolled, so controls whose values changed from elsewhere draw them. */
    void relayout() {
        relayout.run();
    }

    /** The group's title, then a rule to the rows' right edge. */
    private static Renderable title(Component title, int y) {
        return (graphics, mouseX, mouseY, partialTick) -> {
            Font font = Minecraft.getInstance().font;
            int textY = y + TITLE_HEIGHT - font.lineHeight - 1;
            graphics.drawString(font, title, LEFT, textY, TITLE_COLOUR);
            int ruleY = textY + font.lineHeight / 2;
            graphics.fill(LEFT + font.width(title) + RULE_GAP, ruleY, LEFT + ROW_WIDTH, ruleY + 1, RULE_COLOUR);
        };
    }

    private static Renderable scrollbar(int scroll, int max, int bottom) {
        int track = bottom - TOP;
        int thumb = Math.max(ROW_HEIGHT, track * track / (track + max));
        int thumbY = TOP + (track - thumb) * Math.clamp(scroll, 0, max) / max;
        return (graphics, mouseX, mouseY, partialTick) -> {
            graphics.fill(SCROLLBAR_X, TOP, SCROLLBAR_X + SCROLLBAR_WIDTH, bottom, TRACK_COLOUR);
            graphics.fill(SCROLLBAR_X, thumbY, SCROLLBAR_X + SCROLLBAR_WIDTH, thumbY + thumb, THUMB_COLOUR);
        };
    }

    /** A group of options under its title, or heading the page with none. */
    record Section(Optional<Component> title, OptionGroup group) {
    }
}
