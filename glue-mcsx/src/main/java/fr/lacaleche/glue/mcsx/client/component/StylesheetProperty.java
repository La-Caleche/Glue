package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import icyllis.modernui.view.View;

final class StylesheetProperty {

    private StylesheetProperty() {
    }

    static <V extends View> void apply(
            V view,
            PropertyScope<V> scope,
            Property<V, Integer> property,
            StyleValue value
    ) {
        if (value == null) {
            scope.clearStylesheet(property);
        } else if (value instanceof StyleValue.Literal literal) {
            scope.setStylesheet(property, literal.value());
        } else if (value instanceof StyleValue.TokenReference reference) {
            scope.bindStylesheet(property, new ThemeValue<>(view, reference.token()));
        }
    }
}
