package fr.lacaleche.glue.client.options.internal;

import com.mojang.serialization.Codec;
import fr.lacaleche.glue.client.options.GraphicsOptionsPage;
import fr.lacaleche.glue.client.options.OptionRow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link GraphicsOptionsPage} as a page of Minecraft's options, reached from the video settings: each section
 * under its yellow bold title, half rows two to a line, a level row as a button. Each change applies at once; a
 * level press lays the page out again where it was scrolled, so the rows it set show their values.
 */
@Environment(EnvType.CLIENT)
public final class GraphicsOptionsScreen<S> extends OptionsSubScreen {

    private static final int ROW_WIDTH = 310;
    private static final int ROW_HEIGHT = 20;

    private final GraphicsOptionsPage<S> page;
    /** The level rows' buttons, whose label follows every other change. */
    private final Map<OptionRow.Level<S>, Button> levels = new IdentityHashMap<>();

    private GraphicsOptionsScreen(Screen lastScreen, Options options, GraphicsOptionsPage<S> page) {
        super(lastScreen, options, Component.translatable(page.key("title")));
        this.page = page;
    }

    /** The video settings' button that opens {@code page} over {@code videoSettings}. */
    public static <S> Button openButton(OptionsSubScreen videoSettings, GraphicsOptionsPage<S> page) {
        return Button.builder(Component.translatable(page.key("button")), button -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new GraphicsOptionsScreen<>(videoSettings, minecraft.options, page));
                })
                .tooltip(Tooltip.create(Component.translatable(page.key("button.tooltip"))))
                .build();
    }

    @Override
    protected void addOptions() {
        this.levels.clear();
        S saved = page.saved();
        for (GraphicsOptionsPage.Section<S> section : page.sections()) {
            section.title().ifPresent(this::header);
            List<OptionInstance<?>> halves = new ArrayList<>();
            for (OptionRow<S> row : section.rows()) {
                if (row instanceof OptionRow.Level<S> level) {
                    flush(halves);
                    this.level(level, saved);
                    continue;
                }
                OptionInstance<?> option = option(row, saved);
                if (row.half()) {
                    halves.add(option);
                    if (halves.size() == 2) flush(halves);
                } else {
                    flush(halves);
                    this.list.addBig(option);
                }
            }
            flush(halves);
        }
    }

    /** Lays out the half rows waiting for a partner: two on a line, or one alone. */
    private void flush(List<OptionInstance<?>> halves) {
        if (halves.isEmpty()) return;
        this.list.addSmall(halves.toArray(OptionInstance[]::new));
        halves.clear();
    }

    private void header(Component title) {
        Component styled = title.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        this.list.addSmall(new StringWidget(ROW_WIDTH, ROW_HEIGHT, styled, this.font), null);
    }

    private void level(OptionRow.Level<S> level, S saved) {
        Button button = Button.builder(levelMessage(level, saved), pressed -> {
                    page.apply(level.cycle().apply(page.saved(), hasShiftDown()));
                    double scroll = this.list.scrollAmount();
                    this.rebuildWidgets();
                    this.list.setScrollAmount(scroll);
                })
                .size(ROW_WIDTH, ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(level.key() + ".tooltip")))
                .build();
        this.levels.put(level, button);
        this.list.addSmall(button, null);
    }

    private static <S> Component levelMessage(OptionRow.Level<S> level, S settings) {
        return CommonComponents.optionNameValue(Component.translatable(level.key()), level.label().apply(settings));
    }

    /** Puts {@code settings} in force and shows on each level button the level they are at. */
    private void apply(S settings) {
        page.apply(settings);
        S saved = page.saved();
        this.levels.forEach((level, button) -> button.setMessage(levelMessage(level, saved)));
    }

    private OptionInstance<?> option(OptionRow<S> row, S saved) {
        return switch (row) {
            case OptionRow.Slider<S> slider -> slider(slider, saved);
            case OptionRow.Toggle<S> toggle -> OptionInstance.createBoolean(toggle.key(), tooltip(toggle.key()),
                    toggle.get().apply(saved), on -> apply(toggle.with().apply(page.saved(), on)));
            case OptionRow.Choice<S, ?> choice -> choice(choice, saved);
            case OptionRow.Level<S> level -> throw new IllegalArgumentException(level.key() + " is a button");
        };
    }

    /** A slider moving by {@code step} from {@code min}: its positions count steps, so any min fits. */
    private OptionInstance<Integer> slider(OptionRow.Slider<S> slider, S saved) {
        return new OptionInstance<>(slider.key(), tooltip(slider.key()),
                (caption, position) -> Options.genericValueLabel(caption,
                        slider.label().apply(slider.valueAt(position))),
                new OptionInstance.IntRange(0, slider.positions()),
                slider.positionOf(slider.get().apply(saved)),
                position -> apply(slider.with().apply(page.saved(), slider.valueAt(position))));
    }

    /** A choice row shows its constant's label alone, with no caption, as Lumos's shadow quality always has. */
    private <E extends Enum<E>> OptionInstance<E> choice(OptionRow.Choice<S, E> choice, S saved) {
        E[] values = choice.type().getEnumConstants();
        return new OptionInstance<>(choice.key(), tooltip(choice.key()),
                (caption, value) -> choice.label().apply(value),
                new OptionInstance.Enum<>(List.of(values), Codec.INT.xmap(ordinal -> values[ordinal], Enum::ordinal)),
                choice.get().apply(saved),
                value -> apply(choice.with().apply(page.saved(), value)));
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip"));
    }
}
