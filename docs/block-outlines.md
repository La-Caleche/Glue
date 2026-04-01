# Block Outlines

Glue provides a custom block outline rendering system that replaces MC's default black wireframe.

## Architecture

- `GlueOutlineRenderer` — interface for custom outline renderers
- `SimpleBlockOutlineRenderer` — default implementation that renders wireframe lines
- `OutlineRendererRegistry` — registers renderers by name
- `GlueBlock.getOutlineRenderer()` — blocks declare which renderer to use

## Registering an Outline Renderer

```java
public static final OutlineRendererRegistry OUTLINES =
        new OutlineRendererRegistry("mymod", MyMod::id);

public static final GlueOutlineRenderer MY_OUTLINE =
        OUTLINES.register("custom", MyOutlineRenderer::new);
```

## Implementing a Custom Renderer

Extend `SimpleBlockOutlineRenderer` and override `renderShape`:

```java
public class MyOutlineRenderer extends SimpleBlockOutlineRenderer {

    @Override
    protected void renderShape(VoxelShape shape, PoseStack.Pose transform,
                               VertexConsumer consumer, Color color) {
        // Render with a custom color
        super.renderShape(shape, transform, consumer, Color.RED);
    }
}
```

## Linking a Block to a Renderer

Your block must implement `GlueBlock` and return the renderer's resource location:

```java
public class MyBlock extends BaseEntityBlock implements GlueBlock {

    @Override
    public ResourceLocation getOutlineRenderer() {
        return MyMod.id("custom");  // Matches the name passed to register()
    }
}
```

## GlueOutlineRenderer Interface

```java
public interface GlueOutlineRenderer {
    void render(Minecraft client, Level world, VoxelShape shape,
                PoseStack matrices, MultiBufferSource consumers,
                BlockPos blockPos, Vec3 cameraPos);

    void render(/* same */ Color color);

    void renderCollisionBox(Minecraft client, Level world, VoxelShape shape,
                            PoseStack matrices, MultiBufferSource consumers);

    void renderCollisionBox(/* same */ Color color);
}
```

## How It Works

Glue's `DrawSelectionEvents.BLOCK` event intercepts MC's block selection rendering. When the player looks at a block that implements `GlueBlock`, the custom `GlueOutlineRenderer` is used instead of the default wireframe.
