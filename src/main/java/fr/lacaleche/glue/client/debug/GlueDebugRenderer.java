package fr.lacaleche.glue.client.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.math.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class GlueDebugRenderer implements DebugRenderer.SimpleDebugRenderer {

    protected static final int LEFT_PADDING = 5;

    public boolean enabled = false;
    private final Map<ElementType, Map<BlockPos, Drawable>> drawables = Maps.newHashMap();

    public void clear() {
        this.drawables.clear();
    }

    protected Map<BlockPos, Drawable> getDrawables(ElementType type) {
        return this.drawables.computeIfAbsent(type, (type_) -> Maps.newHashMap());
    }

    public void addDebugElement(BlockPos pos, ElementType type, int color, String message, int duration,
                                Consumer<Drawable> consumer, boolean force) {
        final Drawable drawable = this.createDrawable(color, message, duration);
        this.getDrawables(type).put(pos, drawable);
        if (consumer != null) {
            consumer.accept(drawable);
        }
    }

    public void addDebugElement(BlockPos pos, Consumer<ElementBuilder> consumer) {
        final ElementBuilder builder = new ElementBuilder(this::handleDebugRequest);
        consumer.accept(builder);
        builder.create(pos);
    }

    private void handleDebugRequest(OccaDebugRequest request) {
        addDebugElement(request.pos(), request.type(), request.color(), request.message(), request.duration(),
                request.consumer(), request.force());
    }

    public void renderElements(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                               double cameraZ) {
        if (!this.enabled)
            return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return;
        long l = Util.getMillis();

        this.drawables.computeIfPresent(ElementType.MARKER, (type, map) -> {
            map.entrySet().removeIf((entry) -> l > entry.getValue().removalTime);
            map.forEach((pos, drawable) -> this.renderMarker(matrices, vertexConsumers, pos, drawable));
            return map;
        });

        this.drawables.computeIfPresent(ElementType.OUTLINE, (type, map) -> {
            map.entrySet().removeIf((entry) -> l > entry.getValue().removalTime);
            map.forEach((pos, drawable) -> this.renderOutline(client, matrices, vertexConsumers, pos, drawable, cameraX,
                    cameraY, cameraZ));
            return map;
        });

        this.drawables.computeIfPresent(ElementType.BOX, (type, map) -> {
            map.entrySet().removeIf((entry) -> l > entry.getValue().removalTime);
            map.forEach((pos, drawable) -> this.renderBox(matrices, vertexConsumers, pos, drawable, cameraX, cameraY,
                    cameraZ));
            return map;
        });
    }

    public void renderHud(GuiGraphics context) {
    }

    protected void renderMarker(PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos,
                                Drawable drawable) {
        DebugRenderer.renderFilledBox(matrices, vertexConsumers, pos, 0, drawable.getRed(), drawable.getBlue(),
                drawable.getGreen(), drawable.getAlpha() * 0.75F);
        this.renderDrawableText(matrices, vertexConsumers, pos, drawable);
    }

    protected void renderBox(PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos, Drawable drawable,
                             double cameraX, double cameraY, double cameraZ) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderType.lines());

        AABB box = (new AABB(BlockPos.ZERO)).inflate(0.002).deflate(0.0025 * drawable.size)
                .move(pos.getX(), pos.getY(), pos.getZ()).move(-cameraX, -cameraY, -cameraZ);
        ShapeRenderer.renderLineBox(matrices, vertexConsumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY,
                box.maxZ,
                drawable.getRed(), drawable.getBlue(), drawable.getGreen(), drawable.getAlpha());

        this.renderDrawableText(matrices, vertexConsumers, pos, drawable);
    }

    protected void renderOutline(Minecraft client, PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos,
                                 Drawable drawable, double cameraX, double cameraY, double cameraZ) {
        if (client.level == null)
            return;
        final VoxelShape voxelShape = client.level.getBlockState(pos).getOcclusionShape();
        final VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderType.lines());

        DebugRenderer.renderVoxelShape(matrices, vertexConsumer, voxelShape, -cameraX, -cameraY, -cameraZ, 1.0F, 1.0F,
                1.0F,
                1.0F, true);
        this.renderDrawableText(matrices, vertexConsumers, pos, drawable);
    }

    protected void renderDrawableText(PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos,
                                      Drawable drawable) {
        if (!drawable.message.isEmpty()) {
            double d = (double) pos.getX() + 0.5;
            double e = (double) pos.getY() + 1.2;
            double f = (double) pos.getZ() + 0.5;
            DebugRenderer.renderFloatingText(matrices, vertexConsumers, drawable.message, d, e, f, -1, 0.01F, true,
                    0.0F,
                    true);
        }
    }

    protected void drawRight(GuiGraphics context, List<String> strings, Minecraft client) {
        for (int i = 0; i < strings.size(); i++) {
            String text = strings.get(i);
            if (text == null || text.isEmpty()) {
                continue;
            }
            context.pose().pushMatrix();
            int width = client.font.width(text);

            context.pose().translate(client.getWindow().getGuiScaledWidth() - width - LEFT_PADDING,
                    25 + i * (client.font.lineHeight + 1));
            context.fill(-1, -1, width, client.font.lineHeight, -1873784752);
            context.drawString(client.font, text, 0, 0, -2039584, false);

            context.pose().popMatrix();
        }
    }

    protected Drawable createDrawable(int color, String message, int duration) {
        return new Drawable(color, message, Util.getMillis() + (long) duration);
    }

    @Environment(EnvType.CLIENT)
    public enum ElementType {
        MARKER,
        OUTLINE,
        BOX
    }

    @Environment(EnvType.CLIENT)
    public static class Drawable {
        public int color;
        public String message;
        public long removalTime;
        public double size;

        public Drawable(int color, String message, long removalTime) {
            this.color = color;
            this.message = message;
            this.removalTime = removalTime;
        }

        public float getRed() {
            return (float) (this.color >> 16 & 255) / 255.0F;
        }

        public float getBlue() {
            return (float) (this.color >> 8 & 255) / 255.0F;
        }

        public float getGreen() {
            return (float) (this.color & 255) / 255.0F;
        }

        public float getAlpha() {
            return (float) (this.color >> 24 & 255) / 255.0F;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class ElementBuilder {

        private final Consumer<OccaDebugRequest> requestConsumer;

        private ElementType type;
        private int color;
        private String message;
        private int duration;
        private Consumer<Drawable> consumer;
        private boolean force;

        public ElementBuilder(Consumer<OccaDebugRequest> requestConsumer) {
            this.requestConsumer = requestConsumer;

            this.type = ElementType.MARKER;
            this.color = 0xFFFFFF;
            this.message = "";
            this.duration = 100;
            this.consumer = null;
            this.force = false;
        }

        public ElementBuilder type(ElementType type) {
            this.type = type;
            return this;
        }

        public ElementBuilder color(int color) {
            this.color = color;
            return this;
        }

        public ElementBuilder color(int r, int g, int b) {
            this.color = Color.ofRGBA(r, g, b, 100).getColor();
            return this;
        }

        public ElementBuilder color(Color color) {
            this.color = color.getColor();
            return this;
        }

        public ElementBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ElementBuilder duration(int duration) {
            this.duration = duration;
            return this;
        }

        public ElementBuilder consumer(Consumer<Drawable> consumer) {
            this.consumer = consumer;
            return this;
        }

        public ElementBuilder force(boolean force) {
            this.force = force;
            return this;
        }

        public void create(BlockPos pos) {
            this.requestConsumer.accept(
                    new OccaDebugRequest(pos, this.type, this.color, this.message, this.duration, this.consumer,
                            this.force));
        }

    }

    public record OccaDebugRequest(BlockPos pos, ElementType type, int color, String message, int duration,
                                   Consumer<Drawable> consumer, boolean force) {
    }

}
