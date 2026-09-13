---
title: Build the Lumen Probe
description: Complete the first Light Workshop milestone by registering, naming, modeling, and testing one Glue Core item.
artifact: glue-core
modId: glue
environment: client and server
---

# Build the Lumen Probe

This milestone ends with a real `lightworkshop:lumen_probe` item in your hand. It uses only
`glue-core`, common initialization, and the minimum item resources for Minecraft 1.21.8.

Before continuing, finish [Getting Started](../getting-started.md) and confirm the development client
launches with `Light Workshop is ready` in the log.

## 1. Register the Item

Create one common registry holder. `ItemsRegistry` supplies the `lightworkshop` namespace and sets
the item key before `Item::new` runs.

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

The empty method is intentional: calling it forces this holder's static fields to initialize at a
clear lifecycle point.

## 2. Initialize the Holder

Update the common entrypoint created during setup.

```java [src/main/java/dev/example/lightworkshop/LightWorkshop.java]
@Override
public void onInitialize() {
    WorkshopItems.initialize();
    LOGGER.info("Light Workshop is ready");
}
```

Import `dev.example.lightworkshop.registry.WorkshopItems`. Keep this call in the common entrypoint
so the same item registry exists on clients and dedicated servers.

## 3. Name the Probe

Create the language file. The key follows Minecraft's `item.<namespace>.<path>` convention.

```json [src/main/resources/assets/lightworkshop/lang/en_us.json]
{
  "item.lightworkshop.lumen_probe": "Lumen Probe"
}
```

## 4. Point to the Item Model

Minecraft 1.21.8 uses an item definition to select the rendered model.

```json [src/main/resources/assets/lightworkshop/items/lumen_probe.json]
{
  "model": {
    "type": "minecraft:model",
    "model": "lightworkshop:item/lumen_probe"
  }
}
```

The selected model uses the generated-item parent and one texture layer.

```json [src/main/resources/assets/lightworkshop/models/item/lumen_probe.json]
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "lightworkshop:item/lumen_probe"
  }
}
```

Add a PNG at
`src/main/resources/assets/lightworkshop/textures/item/lumen_probe.png`. A 16x16 texture is enough
for the milestone; use transparency around the probe silhouette.

## 5. Compile and Test

Compile before launching so Java and copied resources fail independently.

::: code-group
```bash [Unix]
./gradlew classes
./gradlew runClient
```

```powershell [PowerShell]
.\gradlew.bat classes
.\gradlew.bat runClient
```
:::

Join a development world and run:

```text
/give @s lightworkshop:lumen_probe
```

**Expected result:** the command succeeds, the item name is **Lumen Probe**, the stack limit is one,
and the held/inventory icon uses your texture rather than Minecraft's missing-model pattern.

<DocImage title="Lumen Probe milestone result" description="A Minecraft 1.21.8 player holding the named Lumen Probe while the hotbar shows its custom transparent texture and chat shows a successful give command for lightworkshop:lumen_probe." />

::: details If the result is missing
- **Unknown item:** confirm `WorkshopItems.initialize()` runs and the namespace/path are exactly `lightworkshop` and `lumen_probe`.
- **Raw translation key:** confirm the file is `assets/lightworkshop/lang/en_us.json` and the key is `item.lightworkshop.lumen_probe`.
- **Missing-model pattern:** confirm both JSON paths, both `lightworkshop:item/lumen_probe` references, and the PNG path use the same lowercase spelling.
- **Glue fails to resolve:** return to
  [repository credentials](../getting-started.md#_1-add-repository-access) and confirm the selected
  Glue version exists.
:::

::: details Registration lifecycle
`ItemsRegistry.register` performs the built-in registry call immediately. Do not call it from a
later world event or more than once. The holder pattern makes registration happen once while the
common Fabric entrypoint initializes.
:::

## Next Steps

- [Put the probe in a creative tab](../core/items.md#put-the-probe-in-a-creative-tab).
- [Add a small immutable probe component](../core/items.md#add-one-immutable-setting).
- [Build the optional Lumen Pedestal](../core/blocks.md).
- [Learn why the registry wrapper works](../core/registries.md).
