# Items & Data Components

## Registering Items

```java
public static final ItemsRegistry ITEMS = new ItemsRegistry("mymod", MyMod::id);

// Block item (auto-derives name from block registry)
public static final Item MY_BLOCK_ITEM = ITEMS.register(MY_BLOCK, new Item.Properties());

// Standalone item with custom class
public static final Item MY_ITEM = ITEMS.register("my_item", MyItem::new, new Item.Properties());

// Simple block item with default properties
public static final Item SIMPLE = ITEMS.register(MY_BLOCK);
```

## Creative Mode Tabs

```java
public static final ItemGroupsRegistry GROUPS = new ItemGroupsRegistry("mymod", MyMod::id);

public static final CreativeModeTab MY_TAB = GROUPS.register("my_tab",
        FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.mymod.my_tab"))
                .icon(() -> new ItemStack(MY_ITEM))
                .displayItems((ctx, entries) -> {
                    entries.accept(MY_ITEM);
                    entries.accept(MY_BLOCK_ITEM);
                }));
```

## Data Components

Custom item data components with codec serialization:

```java
public static final DataComponentTypesRegistry DATA = new DataComponentTypesRegistry("mymod", MyMod::id);

public static final DataComponentType<TransformationComponent> TRANSFORM = DATA.register(
        "transform",
        TransformationComponent.CODEC,
        TransformationComponent.PACKET_CODEC);
```

### TransformationComponent

Glue provides a built-in `TransformationComponent` record with translation, left rotation, scale, and right rotation:

```java
public record TransformationComponent(
        Vector3f translation,
        Quaternionf leftRotation,
        Vector3f scale,
        Quaternionf rightRotation) {

    public static final TransformationComponent DEFAULT = ...;
    public static final Codec<TransformationComponent> CODEC = ...;
    public static final StreamCodec<...> PACKET_CODEC = ...;

    public Transformation toTransformation();
}
```

Usage in an item:

```java
public class MyItem extends Item {
    public MyItem(Properties properties) {
        super(properties.component(MY_TRANSFORM, TransformationComponent.DEFAULT));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        TransformationComponent comp = stack.get(MY_TRANSFORM);
        // Modify and set back
        stack.set(MY_TRANSFORM, new TransformationComponent(
                comp.translation().add(new Vector3f(0, 1, 0)),
                comp.leftRotation(), comp.scale(), comp.rightRotation()));
        return InteractionResult.SUCCESS;
    }
}
```
