package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InheritedTextTest {

    @Test
    void colorOnlyOverlayKeepsAnUnsetFontSize() {
        ColorValue color = new ColorValue.Literal(0xFF123456);
        StyleSpec style = StyleSpec.builder().textColor(color).build();

        InheritedText result = assertDoesNotThrow(() -> InheritedText.NONE.overlaidBy(style));

        assertEquals(color, result.textColor());
        assertNull(result.fontSize());
        assertNull(result.fontWeight());
        assertNull(result.font());
    }

    @Test
    void fontOverlayIsInheritedWithoutChangingOtherChannels() {
        InheritedText result = InheritedText.NONE.overlaidBy(StyleSpec.builder().build(), "example:ui");

        assertEquals("example:ui", result.font());
        assertNull(result.textColor());
    }
}
