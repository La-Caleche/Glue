package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;

final class ColumnProperties {

    static final Property<Column, Boolean> VISIBLE = Property.create(
            "visible",
            ComponentVisibility::apply
    );
    static final Property<Column, Integer> BACKGROUND_COLOR = Property.create(
            "background",
            Column::applyBackgroundColor
    );
    static final Property<Column, Integer> CORNER_RADIUS = Property.create(
            "corner-radius",
            Column::applyCornerRadius
    );

    private ColumnProperties() {
    }
}
