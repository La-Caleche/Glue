package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import fr.lacaleche.glue.client.shader.internal.NoopVertexConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomVertexElementsTest {

    @Test
    void aClaimTakesAFreeIdAfterVanillasOwn() {
        VertexFormatElement first = CustomVertexElements.claim(VertexFormatElement.Type.FLOAT, 4);
        VertexFormatElement second = CustomVertexElements.claim(VertexFormatElement.Type.FLOAT, 2);

        assertTrue(first.id() >= 6, "id " + first.id());
        assertNotEquals(first.id(), second.id());
        assertSame(first, VertexFormatElement.byId(first.id()));
        assertEquals(VertexFormatElement.Usage.GENERIC, second.usage());
        assertEquals(2, second.count());
    }

    @Test
    void writingNeedsABufferBuilder() {
        VertexFormatElement element = CustomVertexElements.claim(VertexFormatElement.Type.FLOAT, 4);

        assertThrows(IllegalArgumentException.class,
                () -> CustomVertexElements.put(NoopVertexConsumer.INSTANCE, element, 1.0F, 2.0F, 3.0F, 4.0F));
    }
}
