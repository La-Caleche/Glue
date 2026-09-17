---
title: Build the Lumen Probe
description: Register a named item with Glue Core and Minecraft's existing amethyst-shard texture.
artifact: glue-core
modId: glue
environment: client and server
---

# Build the Lumen Probe

Complete [Installation](../getting-started.md) first. This step uses only `glue-core` and ends with
`lightworkshop:lumen_probe` in your inventory. No custom texture or block is required.

## 1. Register the Item

`ItemsRegistry` assigns the item key to the properties before calling `Item::new`.

```java [src/main/java/dev/example/lightworkshop/registry/WorkshopItems.java]
package dev.example.lightworkshop.registry;

import fr.lacaleche.glue.registries.ItemsRegistry;
import net.minecraft.world.item.Item;

public final class WorkshopItems {

    private static final ItemsRegistry ITEMS = new ItemsRegistry("lightworkshop");
    public static final Item LUMEN_PROBE = ITEMS.register(
            "lumen_probe", Item::new, new Item.Properties().stacksTo(1));

    private WorkshopItems() {
    }

    public static void initialize() {
    }
}
```

The empty `initialize()` method deliberately forces the static registrations to run at mod startup.
It is not a deferred registration queue.

## 2. Initialize the Holder

Use this common entrypoint. Its `id` helper is also available to later examples:

```java [src/main/java/dev/example/lightworkshop/LightWorkshop.java]
package dev.example.lightworkshop;

import dev.example.lightworkshop.registry.WorkshopItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LightWorkshop implements ModInitializer {

    public static final String MOD_ID = "lightworkshop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WorkshopItems.initialize();
        LOGGER.info("Light Workshop is ready");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

The `main` entrypoint in `fabric.mod.json` must point to `dev.example.lightworkshop.LightWorkshop`.

## 3. Add the Resources

Name the item:

```json [src/main/resources/assets/lightworkshop/lang/en_us.json]
{
  "item.lightworkshop.lumen_probe": "Lumen Probe"
}
```

Minecraft 1.21.8 needs an item definition selecting the model:

```json [src/main/resources/assets/lightworkshop/items/lumen_probe.json]
{
  "model": {
    "type": "minecraft:model",
    "model": "lightworkshop:item/lumen_probe"
  }
}
```

Use a vanilla texture so the first run is complete without additional artwork:

```json [src/main/resources/assets/lightworkshop/models/item/lumen_probe.json]
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/amethyst_shard"
  }
}
```

To add custom artwork later, put a PNG at `assets/lightworkshop/textures/item/lumen_probe.png` and
change `layer0` to `lightworkshop:item/lumen_probe`.

## 4. Run It

::: code-group
```bash [Unix]
./gradlew classes runClient
```
```powershell [PowerShell]
.\gradlew.bat classes runClient
```
:::

Join a singleplayer world and run `/give @s lightworkshop:lumen_probe`. The item is named **Lumen
Probe**, stacks to one, and looks like an amethyst shard.

| Problem | Check |
|---|---|
| Unknown item | The common entrypoint calls `WorkshopItems.initialize()` and the mod ID is `lightworkshop`. |
| Raw translation key | The language file is under `assets/lightworkshop/lang/` and contains the exact item key. |
| Missing model | Both `items/lumen_probe.json` and `models/item/lumen_probe.json` exist with the paths above. |

Next: [add the probe HUD](./rendering.md). A [creative tab or data component](../core/items.md) is optional.
