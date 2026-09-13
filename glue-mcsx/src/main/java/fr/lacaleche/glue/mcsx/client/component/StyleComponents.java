package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import icyllis.modernui.view.View;

final class StyleComponents {

    private StyleComponents() {
    }

    static StyleMetadata metadata(View view) {
        return switch (view) {
            case Column column -> column.styleMetadata();
            case Row row -> row.styleMetadata();
            case Text text -> text.styleMetadata();
            case Button button -> button.styleMetadata();
            case Checkbox checkbox -> checkbox.styleMetadata();
            case TextField field -> field.styleMetadata();
            default -> null;
        };
    }

    static void apply(View view, String property, StyleValue value) {
        switch (view) {
            case Column column -> column.applyStylesheetProperty(property, value);
            case Row row -> row.applyStylesheetProperty(property, value);
            case Text text -> text.applyStylesheetProperty(property, value);
            case Button button -> button.applyStylesheetProperty(property, value);
            case Checkbox checkbox -> checkbox.applyStylesheetProperty(property, value);
            case TextField field -> field.applyStylesheetProperty(property, value);
            default -> {
            }
        }
    }
}
