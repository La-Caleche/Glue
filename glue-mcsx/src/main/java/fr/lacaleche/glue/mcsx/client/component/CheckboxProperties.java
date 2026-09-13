package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;
import icyllis.modernui.util.ColorStateList;

final class CheckboxProperties {

    static final Property<Checkbox, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<Checkbox, Boolean> ENABLED = Property.create(
            "enabled",
            Checkbox::setEnabled
    );
    static final Property<Checkbox, CharSequence> TEXT = Property.create(
            "text",
            Checkbox::setText
    );
    static final Property<Checkbox, Integer> TEXT_COLOR = Property.create(
            "text-color",
            Checkbox::setTextColor
    );
    static final Property<Checkbox, ColorStateList> INDICATOR_TINT = Property.create(
            "indicator-tint",
            Checkbox::setButtonTintList
    );
    static final Property<Checkbox, Integer> CONTROL_HEIGHT = Property.create(
            "control-height",
            Checkbox::setMinimumHeight
    );
    static final Property<Checkbox, Integer> TEXT_SIZE = Property.create(
            "text-size",
            (checkbox, size) -> checkbox.setTextSize(size)
    );

    private CheckboxProperties() {
    }
}
