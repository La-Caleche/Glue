package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import fr.lacaleche.glue.client.mixin.shader.runtime.BufferBuilderInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryUtil;

/**
 * Vertex elements beyond vanilla's, for a {@link com.mojang.blaze3d.vertex.VertexFormat} a runtime pipeline
 * reads. Element ids are a global table of {@value VertexFormatElement#MAX_COUNT} that other mods claim
 * too, so an element takes the first free id when it is claimed, never a fixed one.
 */
@Environment(EnvType.CLIENT)
public final class CustomVertexElements {

    private static final int FIRST_FREE_ID = 6;

    private CustomVertexElements() {
    }

    /**
     * Registers a generic element of {@code count} components at the first free id. Claim once, at
     * startup, and keep the element: ids are never given back.
     *
     * @throws IllegalStateException when all ids are taken
     */
    public static synchronized VertexFormatElement claim(VertexFormatElement.Type type, int count) {
        for (int id = FIRST_FREE_ID; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null)
                return VertexFormatElement.register(id, 0, type, VertexFormatElement.Usage.GENERIC, count);
        }
        throw new IllegalStateException("All " + VertexFormatElement.MAX_COUNT + " vertex element ids are taken");
    }

    /**
     * Writes a float element of the vertex being built. Like vanilla's setters it writes nothing when the
     * format lacks the element or the vertex already has it; every element of the format must be written
     * before the next vertex starts.
     *
     * @throws IllegalArgumentException when {@code consumer} is not a {@link BufferBuilder}, the element is
     *                                  not a float one, or {@code values} does not match its count
     */
    public static void put(VertexConsumer consumer, VertexFormatElement element, float... values) {
        long pointer = begin(consumer, element, values.length);
        if (pointer == -1L) return;
        for (int i = 0; i < values.length; i++)
            MemoryUtil.memPutFloat(pointer + 4L * i, values[i]);
    }

    /** {@link #put(VertexConsumer, VertexFormatElement, float...)} for a four-float element, allocating nothing. */
    public static void put(VertexConsumer consumer, VertexFormatElement element, float x, float y, float z, float w) {
        long pointer = begin(consumer, element, 4);
        if (pointer == -1L) return;
        MemoryUtil.memPutFloat(pointer, x);
        MemoryUtil.memPutFloat(pointer + 4L, y);
        MemoryUtil.memPutFloat(pointer + 8L, z);
        MemoryUtil.memPutFloat(pointer + 12L, w);
    }

    private static long begin(VertexConsumer consumer, VertexFormatElement element, int count) {
        if (!(consumer instanceof BufferBuilder builder))
            throw new IllegalArgumentException("Custom elements are written to a BufferBuilder, not " + consumer.getClass().getName());
        if (element.type() != VertexFormatElement.Type.FLOAT)
            throw new IllegalArgumentException("Element " + element + " is not a float element");
        if (count != element.count())
            throw new IllegalArgumentException("Element " + element + " takes " + element.count() + " values, not " + count);
        return ((BufferBuilderInvoker) builder).glue$beginElement(element);
    }
}
