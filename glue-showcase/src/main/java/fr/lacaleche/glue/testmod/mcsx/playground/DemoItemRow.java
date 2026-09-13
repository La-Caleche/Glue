package fr.lacaleche.glue.testmod.mcsx.playground;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.component.Row;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.IntConsumer;

public final class DemoItemRow {

    private DemoItemRow() {
    }

    static Row create(Ui ui, DemoItem item, IntConsumer removeItem) {
        Objects.requireNonNull(ui, "ui");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(removeItem, "removeItem");
        return ui.row(
                ui.field(Component.empty())
                        .text(item.label())
                        .tag(fieldTag(item.id())),
                ui.translatableDangerButton(
                        "mcsx.showcase.remove",
                        () -> removeItem.accept(item.id())
                ).tag(removeTag(item.id()))
        ).tag(rowTag(item.id())).classes("item-row");
    }

    public static String fieldTag(int id) {
        return "mcsx.item.field." + id;
    }

    public static String rowTag(int id) {
        return "mcsx.item.row." + id;
    }

    public static String removeTag(int id) {
        return "mcsx.item.remove." + id;
    }
}
