package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;

final class RowProperties {

    static final Property<Row, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<Row, Integer> BACKGROUND_COLOR = Property.create(
            "background",
            Row::applyBackgroundColor
    );
    static final Property<Row, Integer> CORNER_RADIUS = Property.create(
            "corner-radius",
            Row::applyCornerRadius
    );

    private RowProperties() {
    }
}
