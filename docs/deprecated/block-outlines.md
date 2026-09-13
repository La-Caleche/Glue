# Block Outlines

Glue provides a custom block outline rendering system that replaces MC's default black wireframe.

## Registration

### Java

```java
public static final OutlineRendererRegistry OUTLINES =
        new OutlineRendererRegistry("mymod", MyMod::id);

public static final GlueOutlineRenderer MY_OUTLINE =
        OUTLINES.register("custom", MyOutlineRenderer::new);
```

### Data-Driven (JSON)

Create `assets/<modid>/glue/outlines/<name>.json`:

```json
{
    "type": "simple",
    "red": 255,
    "green": 0,
    "blue": 0,
    "alpha": 0.4
}
```

Loaded into `GlueClientRegistries.OUTLINE_RENDERERS`. Hot-reloadable via F3+T.

## Custom Renderer

Extend `SimpleBlockOutlineRenderer`:

```java
public class MyOutlineRenderer extends SimpleBlockOutlineRenderer {

    @Override
    protected void renderShape(VoxelShape shape, PoseStack.Pose transform,
                               VertexConsumer consumer, Color color) {
        super.renderShape(shape, transform, consumer, Color.RED);
    }
}
```

## Linking a Block to a Renderer

Your block must implement `GlueBlock`:

```java
public class MyBlock extends BaseEntityBlock implements GlueBlock {

    @Override
    public ResourceLocation getOutlineRenderer() {
        return MyMod.id("custom");
    }
}
```

## How It Works

Glue's `DrawSelectionEvents.BLOCK` event intercepts MC's block selection rendering. When the player looks at a block implementing `GlueBlock`, the custom `GlueOutlineRenderer` is used instead of the default wireframe.
