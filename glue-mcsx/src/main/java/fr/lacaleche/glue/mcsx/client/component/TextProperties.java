package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;

final class TextProperties {

    static final Property<Text, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<Text, CharSequence> TEXT = Property.create(
            "text",
            Text::setText
    );
    static final Property<Text, Integer> COLOR = Property.create(
            "color",
            Text::setTextColor
    );
    static final Property<Text, Integer> TEXT_SIZE = Property.create(
            "text-size",
            (text, size) -> text.setTextSize(size)
    );

    private TextProperties() {
    }
}
