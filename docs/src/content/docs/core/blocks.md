---
title: Blocks and Block Entities
description: Add a complete Lumen Pedestal block and block item, then extend it with state or client rendering only when needed.
artifact: glue-core; glue-render
modId: glue; glue-render
environment: client and server; rendering helpers are client only
---

# Blocks and Block Entities

Add an optional **Lumen Pedestal** that can be placed, picked, and given by ID. Register the block
first and its block item second so both use `lightworkshop:lumen_pedestal`.

```java [WorkshopBlocks.java]
package dev.example.lightworkshop.registry;

import dev.example.lightworkshop.block.PedestalBlock;
import fr.lacaleche.glue.registries.BlocksRegistry;
import fr.lacaleche.glue.registries.ItemsRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class WorkshopBlocks {
    private static final BlocksRegistry BLOCKS = new BlocksRegistry("lightworkshop");
    private static final ItemsRegistry ITEMS = new ItemsRegistry("lightworkshop");

    public static final Block LUMEN_PEDESTAL = BLOCKS.register(
            "lumen_pedestal", PedestalBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.AMETHYST));
    public static final Item LUMEN_PEDESTAL_ITEM = ITEMS.register(LUMEN_PEDESTAL);

    private WorkshopBlocks() {
    }

    public static void initialize() {
    }
}
```

The first pedestal class needs no behavior yet. Keeping it separate gives later rendering and block
entity milestones a stable place to add their opt-ins.

```java [src/main/java/dev/example/lightworkshop/block/PedestalBlock.java]
package dev.example.lightworkshop.block;

import net.minecraft.world.level.block.Block;

public final class PedestalBlock extends Block {
    public PedestalBlock(Properties properties) {
        super(properties);
    }
}
```

Call `WorkshopBlocks.initialize()` from `LightWorkshop.onInitialize()`. `BlocksRegistry` assigns the
block key before construction; the block-item overload derives the registered block's path and uses
the `ItemsRegistry` mod ID.

## Complete the Block Checklist

Registration is only the Java half of a finished block. Add these resources before testing:

- `assets/lightworkshop/blockstates/lumen_pedestal.json` selects the block model.
- `assets/lightworkshop/models/block/lumen_pedestal.json` describes the placed model.
- `assets/lightworkshop/items/lumen_pedestal.json` selects the item model on Minecraft 1.21.8.
- `assets/lightworkshop/models/item/lumen_pedestal.json` describes the inventory model.
- `assets/lightworkshop/textures/block/lumen_pedestal.png` supplies the texture.
- `assets/lightworkshop/lang/en_us.json` defines `block.lightworkshop.lumen_pedestal`.
- `data/lightworkshop/loot_table/blocks/lumen_pedestal.json` makes the block drop itself when that is the intended behavior.
- The creative-tab display callback accepts `WorkshopBlocks.LUMEN_PEDESTAL_ITEM` if it should appear beside the probe.

**Expected result:** `/give @s lightworkshop:lumen_pedestal` returns the block item, placing it
creates the pedestal, and breaking it in survival follows the loot table.

<DocImage title="Completed Lumen Pedestal block" description="A placed lightworkshop:lumen_pedestal in a Minecraft 1.21.8 world with the matching named block item selected in the hotbar and no missing-model texture." />

::: details Block-item overloads
`ItemsRegistry.register(block)` uses new default `Item.Properties`. The
`register(block, properties)` overload accepts custom item settings. Both derive the item's path
from the registered block and namespace it with the item registry's mod ID.
:::

## Add Stored State with a Block Entity

Only add a block entity when the pedestal needs per-position state or ticking behavior. The most
useful registry overload gives the constructor the `BlockEntityType` being built.

```java [LumenPedestalBlockEntity.java]
public final class LumenPedestalBlockEntity extends BlockEntity {
    public LumenPedestalBlockEntity(
            BlockEntityType<LumenPedestalBlockEntity> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }
}
```

```java [WorkshopBlockEntities.java]
private static final BlockEntitiesRegistry BLOCK_ENTITIES =
        new BlockEntitiesRegistry("lightworkshop");

public static final BlockEntityType<LumenPedestalBlockEntity> LUMEN_PEDESTAL =
        BLOCK_ENTITIES.register(
                "lumen_pedestal",
                LumenPedestalBlockEntity::new,
                WorkshopBlocks.LUMEN_PEDESTAL);
```

Initialize the block-entity holder after `WorkshopBlocks`. The alternate
`register(path, blockEntityType)` overload accepts a type built elsewhere.

::: details The block class must create its entity
Registering a `BlockEntityType` does not turn a plain `Block` into an entity block. The block must
implement Minecraft's entity-block contract and create `LumenPedestalBlockEntity` for the matching
state. Add that behavior only with the block entity, not to the simple first version.
:::

## Configure Client Rendering

Cutout layers and block tints belong to `glue-render`. Construct `BlocksRendererRegistry` without a
mod ID and call it only from `ClientModInitializer`:

```java [LightWorkshopClient.java]
private final BlocksRendererRegistry blockRenderers = new BlocksRendererRegistry();

@Override
public void onInitializeClient() {
    blockRenderers.registerCutout(WorkshopBlocks.LUMEN_PEDESTAL);
    blockRenderers.registerTint(WorkshopBlocks.LUMEN_PEDESTAL, 0xFFD36A);
}
```

`registerCutout(Block...)` assigns `ChunkSectionLayer.CUTOUT`. A tint is visible only on model faces
with a tint index. Tint overloads accept a fixed RGB integer, a `BlockColor`, or a reference block;
the reference overload returns `reference.defaultMapColor().col` rather than copying a dynamic
color provider.

::: warning Client module boundary
Do not load `BlocksRendererRegistry` or any other `glue-render` class from common code. Register a
custom block-entity renderer from the client entrypoint as well.
:::

For nonstandard selection visuals, use [Block Outlines](../rendering/block-outlines.md). That guide
owns renderer setup, resources, and draw behavior. For a facing pedestal shape, continue to
[Core Utilities](./utilities.md#rotate-a-block-shape).

::: details Shared opt-ins used by the outline system
`GlueBlock` is a common-side opt-in interface whose default renderer ID is `glue:base`. When
`glue-render` is
installed, it also makes block-break particles use the block support shape. `IHaveBigOutline` marks
a block whose collision shape should participate in Glue's additional client raycast, including
parts outside the normal 1x1x1 cell; it does not provide the shape itself.
:::

## Next Steps

- [Add the pedestal item to a creative tab](./items.md#put-the-probe-in-a-creative-tab).
- [Rotate its collision shape](./utilities.md#rotate-a-block-shape).
- [Choose a block outline](../rendering/block-outlines.md).
- [Choose a render pipeline](../rendering/pipelines.md) for custom client geometry.
