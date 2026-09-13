---
title: Items and Data Components
description: Register a simple Lumen Probe, place it in a creative tab, and add small immutable item data.
artifact: glue-core
modId: glue
environment: client and server
---

# Items and Data Components

Begin with a plain Lumen Probe and a creative tab. Once that works, replace the plain registration
with a component-backed version that stores one immutable setting.

## Register the Simple Item

`ItemsRegistry` assigns the item key to `Item.Properties` before calling the factory.

```java [WorkshopItems.java]
private static final ItemsRegistry ITEMS = new ItemsRegistry("lightworkshop");

public static final Item LUMEN_PROBE = ITEMS.register(
        "lumen_probe",
        Item::new,
        new Item.Properties().stacksTo(1));

public static void initialize() {
}
```

Call `WorkshopItems.initialize()` from common initialization. The complete resource files and
`/give` check are in [Build the Lumen Probe](../workshop/probe.md).

## Put the Probe in a Creative Tab

Build the tab after the item it references.

```java [WorkshopItemGroups.java]
private static final ItemGroupsRegistry ITEM_GROUPS =
        new ItemGroupsRegistry("lightworkshop");

public static final CreativeModeTab MAIN = ITEM_GROUPS.register(
        "main",
        FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.lightworkshop.main"))
                .icon(() -> new ItemStack(WorkshopItems.LUMEN_PROBE))
                .displayItems((context, entries) ->
                        entries.accept(WorkshopItems.LUMEN_PROBE)));

public static void initialize() {
}
```

Initialize `WorkshopItemGroups` after `WorkshopItems`, then add the translation:

```json [assets/lightworkshop/lang/en_us.json]
{
  "item.lightworkshop.lumen_probe": "Lumen Probe",
  "itemGroup.lightworkshop.main": "Light Workshop"
}
```

**Expected result:** the creative inventory contains a **Light Workshop** tab whose icon and first
entry are the Lumen Probe.

<DocImage title="Light Workshop creative tab" description="The Minecraft 1.21.8 creative inventory open to a tab named Light Workshop, showing the Lumen Probe as both the tab icon and a visible item entry." />

## Add One Immutable Setting

Use a data component when an `ItemStack` must retain custom state. This record stores whether the
probe is active and supplies both persistent and network codecs.

```java [ProbeState.java]
package dev.example.lightworkshop.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ProbeState(boolean active) {
    public static final ProbeState DEFAULT = new ProbeState(false);
    public static final Codec<ProbeState> CODEC =
            Codec.BOOL.xmap(ProbeState::new, ProbeState::active);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProbeState> PACKET_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ProbeState::active,
                    ProbeState::new);
}
```

Register the component before the item, then install its default on the item's properties.

```java [WorkshopItems.java]
private static final DataComponentTypesRegistry COMPONENTS =
        new DataComponentTypesRegistry("lightworkshop");
private static final ItemsRegistry ITEMS = new ItemsRegistry("lightworkshop");

public static final DataComponentType<ProbeState> PROBE_STATE = COMPONENTS.register(
        "probe_state", ProbeState.CODEC, ProbeState.PACKET_CODEC);

public static final Item LUMEN_PROBE = ITEMS.register(
        "lumen_probe", Item::new,
        new Item.Properties().stacksTo(1)
                .component(PROBE_STATE, ProbeState.DEFAULT));
```

Use this component-backed item registration **instead of** the earlier plain registration. To
toggle it, read the current value and store a new record with
`stack.set(WorkshopItems.PROBE_STATE, new ProbeState(!current.active()))`.

**Expected result:** each new probe starts inactive, and the value persists with its item stack and
synchronizes when the stack is sent over the network.

::: details Data component registration forms
`register(path, codec, streamCodec)` creates a persistent, network-synchronized component.
`register(path, builderOperator)` exposes the complete `DataComponentType.Builder<T>` when only
one of those behaviors or additional builder configuration is needed. The operator must mutate the
supplied builder; returning a replacement builder is not honored.
:::

## Reuse TransformationComponent Later

`TransformationComponent` is a reusable Core record containing translation, left rotation, scale,
and right rotation. It exposes `CODEC`, `PACKET_CODEC`, `DEFAULT`, and `toTransformation()` and can
be registered through `DataComponentTypesRegistry` in the same way as `ProbeState`.

```java
TransformationComponent updated = new TransformationComponent(
        new Vector3f(current.translation()).add(0.0F, 1.0F, 0.0F),
        new Quaternionf(current.leftRotation()),
        new Vector3f(current.scale()),
        new Quaternionf(current.rightRotation()));
stack.set(TRANSFORM, updated);
```

::: warning Copy mutable transformation values
JOML vectors and quaternions are mutable. `TransformationComponent` does not copy constructor
arguments or accessor results, so never mutate `current.translation()`, either rotation, or scale
in place. Copy every value and store a new component as shown above.
:::

::: details Standalone and block-item overloads
For a custom item class, pass its `Function<Item.Properties, Item>` constructor and properties to
`register(path, factory, properties)`. For blocks, `register(block)` uses default properties and
`register(block, properties)` uses the registered block path with the item registry's mod ID. See
the complete [Lumen Pedestal](./blocks.md) registration order.
:::

## Next Steps

- [Add the optional Lumen Pedestal](./blocks.md).
- [Toggle a Light Workshop client action](./keybindings.md).
- [Review all Core registry lifecycles](./registries.md).
