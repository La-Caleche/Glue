package fr.lacaleche.glue.mcsx.client.component;

import dev.vfyjxf.taffy.style.FlexDirection;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public final class Column extends TaffyLayout {

    private final PropertyScope<Column> properties = new PropertyScope<>(this);
    private final ShapeDrawable themedBackground = new ShapeDrawable();
    private final StyleMetadata style = new StyleMetadata(this);

    public Column(Context context) {
        super(context, FlexDirection.COLUMN);
        this.setBackground(this.themedBackground);
        this.properties.setComponent(ColumnProperties.BACKGROUND_COLOR, 0);
        this.properties.setComponent(ColumnProperties.CORNER_RADIUS, 0);
    }

    public void add(View... children) {
        this.addChildren(children);
    }

    public void add(List<? extends View> children) {
        this.addChildren(children);
    }

    public Column visible(boolean visible) {
        this.properties.set(ColumnProperties.VISIBLE, visible);
        return this;
    }

    public Column visible(Value<Boolean> visible) {
        this.properties.bind(ColumnProperties.VISIBLE, visible);
        return this;
    }

    public Column tag(Object tag) {
        this.setTag(tag);
        return this;
    }

    public Column classes(String... names) {
        this.style.classes(names);
        return this;
    }

    public Column part(String name) {
        this.style.part(name);
        return this;
    }

    public Column state(String name, Value<Boolean> value) {
        this.style.state(name, value);
        return this;
    }

    /** Keyed children whose factory sees the item its key held when the row was created. */
    public <T, K> void children(
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super T, ? extends View> viewFactory
    ) {
        Objects.requireNonNull(viewFactory, "viewFactory");
        this.bindChildren(items, keyExtractor, value -> viewFactory.apply(value.get()));
    }

    /**
     * Keyed children whose factory receives the live value for its key, so a row binds its fields
     * once and updates in place instead of re-deriving them from the whole collection.
     */
    public <T, K> void boundChildren(
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super Value<T>, ? extends View> viewFactory
    ) {
        this.bindChildren(items, keyExtractor, viewFactory);
    }

    public void theme(Theme theme) {
        this.provideTheme(theme);
    }

    public void theme(Value<? extends Theme> theme) {
        this.provideTheme(theme);
    }

    public void stylesheet(Stylesheet stylesheet) {
        this.provideStylesheet(stylesheet);
    }

    public void stylesheet(Value<? extends Stylesheet> stylesheet) {
        this.provideStylesheet(stylesheet);
    }

    /** Installs only the supplied rules and shields this subtree from outer style scopes. */
    public void rawStylesheet(Stylesheet stylesheet) {
        this.provideRawStylesheet(stylesheet);
    }

    /** Installs only the supplied reactive rules and shields this subtree from outer style scopes. */
    public void rawStylesheet(Value<? extends Stylesheet> stylesheet) {
        this.provideRawStylesheet(stylesheet);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.style.mount();
        this.properties.mount();
    }

    @Override
    protected void onDetachedFromWindow() {
        RuntimeException failure = null;
        failure = LifecycleCleanup.attempt(failure, this.properties::unmount);
        failure = LifecycleCleanup.attempt(failure, this.style::unmount);
        failure = LifecycleCleanup.attempt(failure, super::onDetachedFromWindow);
        LifecycleCleanup.finish(failure);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (this.style != null) {
            this.style.interactionChanged();
        }
    }

    StyleMetadata styleMetadata() {
        return this.style;
    }

    void applyStylesheetProperty(String property, StyleValue value) {
        if (property.equals("background")) {
            StylesheetProperty.apply(
                    this,
                    this.properties,
                    ColumnProperties.BACKGROUND_COLOR,
                    value
            );
        } else if (property.equals("corner-radius")) {
            StylesheetProperty.apply(
                    this,
                    this.properties,
                    ColumnProperties.CORNER_RADIUS,
                    value
            );
        }
    }

    void applyBackgroundColor(int color) {
        this.themedBackground.setColor(color);
    }

    void applyCornerRadius(int radius) {
        this.themedBackground.setCornerRadius(radius);
    }
}
