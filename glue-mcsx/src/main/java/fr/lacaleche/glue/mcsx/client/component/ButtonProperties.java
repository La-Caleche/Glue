package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;

final class ButtonProperties {

    static final Property<Button, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<Button, Boolean> ENABLED = Property.create(
            "enabled",
            Button::setEnabled
    );
    static final Property<Button, Boolean> PRESSED = Property.create(
            "pressed",
            Button::applyLatched
    );
    static final Property<Button, CharSequence> TEXT = Property.create(
            "text",
            Button::setText
    );
    static final Property<Button, Integer> TEXT_COLOR = Property.create(
            "text-color",
            Button::setTextColor
    );
    static final Property<Button, Integer> BACKGROUND_COLOR = Property.create(
            "background",
            Button::applyBackgroundColor
    );
    static final Property<Button, Integer> CORNER_RADIUS = Property.create(
            "corner-radius",
            Button::applyCornerRadius
    );
    static final Property<Button, Integer> CONTROL_HEIGHT = Property.create(
            "control-height",
            Button::setMinimumHeight
    );
    static final Property<Button, Integer> TEXT_SIZE = Property.create(
            "text-size",
            (button, size) -> button.setTextSize(size)
    );
    static final Property<Button, Integer> FONT_WEIGHT = Property.create(
            "font-weight",
            Button::setTextStyle
    );
    static final Property<Button, Integer> ELEVATION = Property.create(
            "elevation",
            Button::applyElevation
    );
    static final Property<Button, Integer> TOP_HIGHLIGHT = Property.create(
            "top-highlight",
            Button::applyTopHighlight
    );
    static final Property<Button, Integer> TOP_HIGHLIGHT_HEIGHT = Property.create(
            "top-highlight-height",
            Button::applyTopHighlightHeight
    );

    private ButtonProperties() {
    }
}
