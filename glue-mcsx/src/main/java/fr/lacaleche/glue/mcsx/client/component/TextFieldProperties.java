package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;

final class TextFieldProperties {

    static final Property<TextField, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<TextField, Boolean> ENABLED = Property.create(
            "enabled",
            TextField::setEnabled
    );
    static final Property<TextField, CharSequence> HINT = Property.create(
            "hint",
            TextField::setHint
    );
    static final Property<TextField, Integer> TEXT_COLOR = Property.create(
            "text-color",
            TextField::setTextColor
    );
    static final Property<TextField, Integer> HINT_TEXT_COLOR = Property.create(
            "hint-text-color",
            TextField::setHintTextColor
    );
    static final Property<TextField, Integer> BACKGROUND_COLOR = Property.create(
            "background",
            TextField::applyBackgroundColor
    );
    static final Property<TextField, Integer> CORNER_RADIUS = Property.create(
            "corner-radius",
            TextField::applyCornerRadius
    );
    static final Property<TextField, Integer> CONTROL_HEIGHT = Property.create(
            "control-height",
            TextField::setMinimumHeight
    );
    static final Property<TextField, Integer> TEXT_SIZE = Property.create(
            "text-size",
            (field, size) -> field.setTextSize(size)
    );
    static final Property<TextField, Integer> HORIZONTAL_PADDING = Property.create(
            "padding-horizontal",
            TextField::applyHorizontalPadding
    );
    static final Property<TextField, Integer> VERTICAL_PADDING = Property.create(
            "padding-vertical",
            TextField::applyVerticalPadding
    );

    private TextFieldProperties() {
    }
}
