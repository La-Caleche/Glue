package fr.lacaleche.glue.testmod.mcsx.playground;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.Row;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.mcsx.ShowcaseUiScreen;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.List;

public final class ModernUiDemo extends ShowcaseUiScreen {

    public static final String TAG_TITLE = "mcsx.title";
    public static final String TAG_EDITOR = "mcsx.editor";
    public static final String TAG_COUNTER = "mcsx.counter";
    public static final String TAG_INCREMENT = "mcsx.increment";
    public static final String TAG_BURST = "mcsx.burst";
    public static final String TAG_DETAILS = "mcsx.details";
    public static final String TAG_TOGGLE_DETAILS = "mcsx.toggle-details";
    public static final String TAG_ITEMS = "mcsx.items";
    public static final String TAG_ADD = "mcsx.add";
    public static final String TAG_ROTATE = "mcsx.rotate";
    public static final String TAG_CLOSE = "mcsx.close";

    private final Signal<Integer> count = Signal.of(0);
    private final Signal<Boolean> detailsVisible = Signal.of(true);
    private final DemoForm form = new DemoForm();
    private final Signal<List<DemoItem>> items = Signal.of(List.of(
            DemoItem.create(1, "Apple"),
            DemoItem.create(2, "Banana"),
            DemoItem.create(3, "Cherry")
    ));
    private int nextItemId = 4;

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                Signal.of(Themes.mcsx()),
                Stylesheets.resource(TestmodClient.id("showcase")),
                ui.card(
                        ui.translatableHeading("mcsx.showcase.title").tag(TAG_TITLE),
                        ui.translatableCopy("mcsx.showcase.description"),
                        ui.translatableField("mcsx.showcase.editor_hint")
                                .tag(TAG_EDITOR)
                                .classes("editor"),
                        this.createCounterSection(ui),
                        this.form.create(ui),
                        this.createRawStylesheetExample(ui),
                        this.createCollectionSection(ui),
                        this.createFooter(ui)
                )
        ).classes("showcase");
    }

    private Column createCounterSection(Ui ui) {
        return ui.translatableSection(
                "mcsx.showcase.counter",
                ui.text(this.count.map(value -> "Counter: " + value))
                        .tag(TAG_COUNTER)
                        .classes("counter-value"),
                ui.translatableCopy("mcsx.showcase.details")
                        .visible(this.detailsVisible)
                        .tag(TAG_DETAILS),
                ui.actions(
                        ui.translatableButton("mcsx.showcase.increment", this::increment)
                                .tag(TAG_INCREMENT),
                        ui.translatableSecondaryButton("mcsx.showcase.burst", this::burst)
                                .tag(TAG_BURST)
                                .classes("burst"),
                        ui.translatableQuietButton(
                                "mcsx.showcase.toggle_details",
                                this::toggleDetails
                        ).tag(TAG_TOGGLE_DETAILS)
                )
        );
    }

    private Column createCollectionSection(Ui ui) {
        return ui.translatableSection(
                "mcsx.showcase.collection",
                ui.translatableCopy("mcsx.showcase.collection_description"),
                ui.keyedColumn(
                        this.items,
                        DemoItem::id,
                        item -> DemoItemRow.create(ui, item, this::removeItem)
                ).tag(TAG_ITEMS),
                ui.actions(
                        ui.translatableButton("mcsx.showcase.add", this::addItem).tag(TAG_ADD),
                        ui.translatableSecondaryButton("mcsx.showcase.rotate", this::rotateItems)
                                .enabled(this.items.map(current -> current.size() > 1))
                                .tag(TAG_ROTATE)
                )
        );
    }

    private Column createRawStylesheetExample(Ui ui) {
        Column example = ui.column(
                ui.translatableText("mcsx.showcase.raw_title"),
                ui.translatableText("mcsx.showcase.raw_description"),
                ui.translatableButton("mcsx.showcase.raw_action", this::increment)
        ).classes("raw-scope-demo");
        example.rawStylesheet(Stylesheets.resource(TestmodClient.id("showcase-raw")));
        return example;
    }

    private Row createFooter(Ui ui) {
        return ui.actions(
                ui.translatableQuietButton("mcsx.showcase.close", this::back)
                        .tag(TAG_CLOSE)
        ).classes("footer-actions");
    }

    private void increment() {
        this.count.update(value -> value + 1);
    }

    private void burst() {
        this.count.update(value -> value + 3);
    }

    private void toggleDetails() {
        this.detailsVisible.update(visible -> !visible);
    }

    private void addItem() {
        int id = this.nextItemId++;
        this.items.update(current -> {
            List<DemoItem> next = new ArrayList<>(current);
            next.add(DemoItem.create(id, "Item " + id));
            return List.copyOf(next);
        });
    }

    private void removeItem(int id) {
        this.items.update(current -> {
            List<DemoItem> next = new ArrayList<>(current);
            next.removeIf(item -> item.id() == id);
            return List.copyOf(next);
        });
    }

    private void rotateItems() {
        this.items.update(current -> {
            if (current.size() < 2) return current;

            List<DemoItem> next = new ArrayList<>(current);
            next.add(next.removeFirst());
            return List.copyOf(next);
        });
    }

    public String itemLabel(int id) {
        return this.item(id).label().get();
    }

    public void renameItem(int id, String label) {
        this.item(id).label().set(label);
    }

    public DemoForm form() {
        return this.form;
    }

    private DemoItem item(int id) {
        for (DemoItem item : this.items.get()) {
            if (item.id() == id) return item;
        }
        throw new IllegalArgumentException("Unknown item " + id);
    }

}
