package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Checkbox;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.Row;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.mcsx.client.component.TextField;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Explicitly scoped construction facade for concise Java-first MCSX composition.
 *
 * <p><b>Text factories come in three families, split by how a {@code String} is interpreted:</b></p>
 * <ul>
 *   <li><b>{@code String} overloads treat the string as a Minecraft translation key</b> —
 *       {@code heading("app.title")} resolves {@code Component.translatable} and re-translates live
 *       on language switch and resource reload. Because a {@code String} argument always selects
 *       this overload over the {@code CharSequence} one, a raw English string passed here is looked
 *       up as a key (and displayed verbatim only because a missing key falls back to itself).</li>
 *   <li><b>{@code translatable*} factories are the explicit spelling of the same behavior</b> and
 *       additionally accept {@code Component.translatable} format arguments. Prefer them when the
 *       call site should state its intent, or when arguments are needed.</li>
 *   <li><b>{@code literal*} factories take the string as raw display text</b>, never consulting the
 *       language files. Use them for user input, numbers and other computed strings.</li>
 * </ul>
 * <p>{@code Component} overloads bind any Minecraft component and re-resolve it live like the
 * translation-key overloads; {@code Value} overloads bind reactive text directly.</p>
 */
@Environment(EnvType.CLIENT)
public final class Ui {

    private final Context context;

    private Ui(Context context) {
        this.context = context;
    }

    public static Ui with(Context context) {
        return new Ui(Objects.requireNonNull(context, "context"));
    }

    /** The context every component built through this facade belongs to. */
    public Context context() {
        return this.context;
    }

    /**
     * Tags a view built outside this facade and returns it, so a custom View stays part of the
     * surrounding declaration instead of needing a statement of its own.
     */
    public static <V extends View> V tagged(V view, Object tag) {
        Objects.requireNonNull(view, "view").setTag(tag);
        return view;
    }

    public Column screen(View... children) {
        return this.screen(list(children));
    }

    public Column screen(List<? extends View> children) {
        Column screen = this.screenContent(children);
        screen.stylesheet(Stylesheet.empty());
        return screen;
    }

    private Column screenContent(List<? extends View> children) {
        Column viewport = this.column(children).classes("ui-viewport");
        ScrollView scroll = new ScrollView(this.context);
        scroll.setFillViewport(true);
        scroll.addView(viewport, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return this.column(scroll).classes("ui-screen");
    }

    public Column screen(
            Value<? extends Theme> theme,
            Value<? extends Stylesheet> stylesheet,
            View... children
    ) {
        return this.screen(theme, stylesheet, list(children));
    }

    public Column screen(
            Value<? extends Theme> theme,
            Value<? extends Stylesheet> stylesheet,
            List<? extends View> children
    ) {
        Column screen = this.screenContent(children);
        screen.theme(theme);
        screen.stylesheet(stylesheet);
        return screen;
    }

    public Column card(View... children) {
        return this.card(list(children));
    }

    public Column card(List<? extends View> children) {
        return this.column(children).classes("ui-card");
    }

    public Column section(CharSequence title, View... children) {
        return this.section(title, list(children));
    }

    public Column section(CharSequence title, List<? extends View> children) {
        Column section = this.column().classes("ui-section");
        section.add(this.text(title).classes("ui-section-title"));
        section.add(children);
        return section;
    }

    public Column section(String translationKey, View... children) {
        return this.section(translated(translationKey), children);
    }

    public Column section(String translationKey, List<? extends View> children) {
        return this.section(translated(translationKey), children);
    }

    public Column section(Component title, View... children) {
        return this.section(title, list(children));
    }

    public Column section(Component title, List<? extends View> children) {
        Column section = this.column().classes("ui-section");
        section.add(this.text(title).classes("ui-section-title"));
        section.add(children);
        return section;
    }

    public Column literalSection(String title, View... children) {
        return this.section(literal(title), children);
    }

    public Column literalSection(String title, List<? extends View> children) {
        return this.section(literal(title), children);
    }

    public Column translatableSection(String key, Object... arguments) {
        return this.section(translated(key, arguments));
    }

    public Column translatableSection(String key, View... children) {
        return this.section(translated(key), children);
    }

    public Column translatableSection(String key, List<? extends View> children) {
        return this.section(translated(key), children);
    }

    public Row actions(View... children) {
        return this.actions(list(children));
    }

    public Row actions(List<? extends View> children) {
        return this.row(children).classes("ui-actions");
    }

    public Column column(View... children) {
        return this.column(list(children));
    }

    public Column column(List<? extends View> children) {
        Column column = new Column(this.context);
        column.add(children);
        return column;
    }

    public Row row(View... children) {
        return this.row(list(children));
    }

    public Row row(List<? extends View> children) {
        Row row = new Row(this.context);
        row.add(children);
        return row;
    }

    public Text heading(CharSequence text) {
        return this.text(text).classes("ui-heading");
    }

    public Text heading(String translationKey) {
        return this.heading(translated(translationKey));
    }

    public Text heading(Component text) {
        return this.text(text).classes("ui-heading");
    }

    public Text literalHeading(String text) {
        return this.heading(literal(text));
    }

    public Text translatableHeading(String key, Object... arguments) {
        return this.heading(translated(key, arguments));
    }

    public Text copy(CharSequence text) {
        return this.text(text).classes("ui-copy");
    }

    public Text copy(String translationKey) {
        return this.copy(translated(translationKey));
    }

    public Text copy(Component text) {
        return this.text(text).classes("ui-copy");
    }

    public Text literalCopy(String text) {
        return this.copy(literal(text));
    }

    public Text translatableCopy(String key, Object... arguments) {
        return this.copy(translated(key, arguments));
    }

    public Text text(CharSequence text) {
        return new Text(this.context, text);
    }

    public Text text(String translationKey) {
        return this.text(translated(translationKey));
    }

    public Text text(Component text) {
        return new Text(this.context, text);
    }

    public Text literalText(String text) {
        return this.text(literal(text));
    }

    public Text translatableText(String key, Object... arguments) {
        return this.text(translated(key, arguments));
    }

    public Text text(Value<? extends CharSequence> text) {
        return new Text(this.context, text);
    }

    public TextField field(CharSequence hint) {
        TextField field = new TextField(this.context);
        field.hint(hint);
        field.classes("ui-field");
        return field;
    }

    public TextField field(String translationKey) {
        return this.field(translated(translationKey));
    }

    public TextField field(Component hint) {
        TextField field = new TextField(this.context);
        field.hint(hint);
        field.classes("ui-field");
        return field;
    }

    public TextField literalField(String hint) {
        return this.field(literal(hint));
    }

    public TextField translatableField(String key, Object... arguments) {
        return this.field(translated(key, arguments));
    }

    public Checkbox checkbox(CharSequence text) {
        return new Checkbox(this.context, text).classes("ui-checkbox");
    }

    public Checkbox checkbox(String translationKey) {
        return this.checkbox(translated(translationKey));
    }

    public Checkbox checkbox(Component text) {
        return new Checkbox(this.context, text).classes("ui-checkbox");
    }

    public Checkbox literalCheckbox(String text) {
        return this.checkbox(literal(text));
    }

    public Checkbox translatableCheckbox(String key, Object... arguments) {
        return this.checkbox(translated(key, arguments));
    }

    public Button button(CharSequence text, Runnable action) {
        Button button = new Button(this.context, text, action);
        button.setGravity(Gravity.CENTER);
        button.classes("ui-button");
        return button;
    }

    public Button button(String translationKey, Runnable action) {
        return this.button(translated(translationKey), action);
    }

    public Button button(Component text, Runnable action) {
        Button button = new Button(this.context, text, action);
        button.setGravity(Gravity.CENTER);
        button.classes("ui-button");
        return button;
    }

    public Button literalButton(String text, Runnable action) {
        return this.button(literal(text), action);
    }

    public Button translatableButton(String key, Runnable action, Object... arguments) {
        return this.button(translated(key, arguments), action);
    }

    public Button secondaryButton(CharSequence text, Runnable action) {
        return this.button(text, action).classes("secondary");
    }

    public Button secondaryButton(String translationKey, Runnable action) {
        return this.secondaryButton(translated(translationKey), action);
    }

    public Button secondaryButton(Component text, Runnable action) {
        return this.button(text, action).classes("secondary");
    }

    public Button literalSecondaryButton(String text, Runnable action) {
        return this.secondaryButton(literal(text), action);
    }

    public Button translatableSecondaryButton(String key, Runnable action, Object... arguments) {
        return this.secondaryButton(translated(key, arguments), action);
    }

    public Button quietButton(CharSequence text, Runnable action) {
        return this.button(text, action).classes("quiet");
    }

    public Button quietButton(String translationKey, Runnable action) {
        return this.quietButton(translated(translationKey), action);
    }

    public Button quietButton(Component text, Runnable action) {
        return this.button(text, action).classes("quiet");
    }

    public Button literalQuietButton(String text, Runnable action) {
        return this.quietButton(literal(text), action);
    }

    public Button translatableQuietButton(String key, Runnable action, Object... arguments) {
        return this.quietButton(translated(key, arguments), action);
    }

    public Button dangerButton(CharSequence text, Runnable action) {
        return this.button(text, action).classes("danger");
    }

    public Button dangerButton(String translationKey, Runnable action) {
        return this.dangerButton(translated(translationKey), action);
    }

    public Button dangerButton(Component text, Runnable action) {
        return this.button(text, action).classes("danger");
    }

    public Button literalDangerButton(String text, Runnable action) {
        return this.dangerButton(literal(text), action);
    }

    public Button translatableDangerButton(String key, Runnable action, Object... arguments) {
        return this.dangerButton(translated(key, arguments), action);
    }

    /** A keyed list whose rows are built from the item their key held when the row was created. */
    public <T, K> Column keyedColumn(
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super T, ? extends View> viewFactory
    ) {
        Column column = new Column(this.context);
        column.classes("ui-list");
        column.children(items, keyExtractor, viewFactory);
        return column;
    }

    /**
     * A keyed list whose rows receive the live value for their key. Prefer it whenever a row shows a
     * field that changes while the row stays: the row binds that field once instead of re-deriving
     * itself from the whole collection on every change.
     */
    public <T, K> Column boundColumn(
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super Value<T>, ? extends View> viewFactory
    ) {
        Column column = new Column(this.context);
        column.classes("ui-list");
        column.boundChildren(items, keyExtractor, viewFactory);
        return column;
    }

    private static List<View> list(View... children) {
        return Arrays.asList(Objects.requireNonNull(children, "children"));
    }

    private static Component literal(String text) {
        return Component.literal(Objects.requireNonNull(text, "text"));
    }

    private static Component translated(String key, Object... arguments) {
        return Component.translatable(Objects.requireNonNull(key, "key"), arguments);
    }

}
