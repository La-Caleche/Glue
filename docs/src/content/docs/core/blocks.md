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

Registration is only the Java half. This minimal resource set gives the pedestal a full-cube model
using vanilla amethyst, an inventory model and a survival drop. Custom artwork can come later.

::: code-group
```json [assets/lightworkshop/blockstates/lumen_pedestal.json]
{
  "variants": {
    "": { "model": "lightworkshop:block/lumen_pedestal" }
  }
}
```
```json [assets/lightworkshop/models/block/lumen_pedestal.json]
{
  "parent": "minecraft:block/cube_all",
  "textures": { "all": "minecraft:block/amethyst_block" }
}
```
```json [assets/lightworkshop/items/lumen_pedestal.json]
{
  "model": {
    "type": "minecraft:model",
    "model": "lightworkshop:block/lumen_pedestal"
  }
}
```
```json [data/lightworkshop/loot_table/blocks/lumen_pedestal.json]
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{ "type": "minecraft:item", "name": "lightworkshop:lumen_pedestal" }],
    "conditions": [{ "condition": "minecraft:survives_explosion" }]
  }]
}
```
```json [data/minecraft/tags/block/mineable/pickaxe.json]
{
  "replace": false,
  "values": ["lightworkshop:lumen_pedestal"]
}
```
:::

Paths are relative to `src/main/resources/`. Merge
`"block.lightworkshop.lumen_pedestal": "Lumen Pedestal"` into `assets/lightworkshop/lang/en_us.json`.
The item definition points directly to the block model, so this version needs no separate item model
or PNG. Add `WorkshopBlocks.LUMEN_PEDESTAL_ITEM` to the creative tab if desired.

**Expected result:** `/give @s lightworkshop:lumen_pedestal` returns the block item, placing it
creates the pedestal, and breaking it in survival follows the loot table.

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

The block must also create its entity. Replace the plain block class with this version when adding
the registered type (add `implements GlueBlock` and its outline method as well if using that feature):

```java [PedestalBlock.java]
package dev.example.lightworkshop.block;

import dev.example.lightworkshop.registry.WorkshopBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PedestalBlock extends Block implements EntityBlock {
    public PedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WorkshopBlockEntities.LUMEN_PEDESTAL.create(pos, state);
    }
}
```

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
