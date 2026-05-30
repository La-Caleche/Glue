# Blocks & Block Entities

## Registering Blocks

```java
public static final BlocksRegistry BLOCKS = new BlocksRegistry("mymod", MyMod::id);

public static final Block MY_BLOCK = BLOCKS.register("my_block", MyBlock::new,
        BlockBehaviour.Properties.of()
                .noOcclusion()
                .mapColor(MapColor.COLOR_RED)
                .sound(SoundType.AMETHYST)
                .strength(1.5F, 6.0F));
```

The registry handles `ResourceKey` creation and `BuiltInRegistries.BLOCK` registration.

## Registering Block Entities

```java
public static final BlockEntitiesRegistry BLOCK_ENTITIES =
        new BlockEntitiesRegistry("mymod", MyMod::id);

public static final BlockEntityType<MyBlockEntity> MY_BE = BLOCK_ENTITIES.register(
        "my_block_entity",
        FabricBlockEntityTypeBuilder.create(MyBlockEntity::new, MY_BLOCK).build());
```

## Block Renderer Registry

For setting render layers and block tints:

```java
public static final BlocksRendererRegistry RENDERER = new BlocksRendererRegistry();

public static void register() {
    // Set blocks to use cutout rendering (for transparency)
    RENDERER.registerCutout(MY_BLOCK, MY_OTHER_BLOCK);

    // Register a fixed tint color
    RENDERER.registerTint(MY_BLOCK, 0x00FF00);

    // Copy tint from another block
    RENDERER.registerTint(MY_BLOCK, Blocks.GRASS_BLOCK);

    // Custom tint provider
    RENDERER.registerTint(MY_BLOCK, (state, view, pos, tintIndex) -> 0xFF0000);
}
```

## GlueBlock Interface

Blocks can implement `GlueBlock` to integrate with Glue's systems:

```java
public class MyBlock extends BaseEntityBlock implements GlueBlock {
    // ...
}
```

## IHaveBigOutline Interface

For blocks with VoxelShapes that extend beyond the standard 1×1×1 bounds, implement `IHaveBigOutline` so the outline renderer handles extended shapes correctly.

## GlueVoxelShape

Wraps a `VoxelShape` so its coordinate edges collapse to a single cube range per axis,
giving a clean single-box outline silhouette instead of one split along the source shape's
internal subdivisions:

```java
protected static final VoxelShape OUTLINE = new GlueVoxelShape(Block.box(3, 0, 3, 13, 16, 13));
```
