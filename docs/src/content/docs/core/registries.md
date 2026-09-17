---
title: Registries
description: Register the Lumen Probe first, then choose Core wrappers or client reloadable registries by lifecycle.
artifact: glue-core; glue-render
modId: glue; glue-render
environment: client and server; reloadable registries are client only
---

# Registries

The smallest useful registration is one item. This creates `lightworkshop:lumen_probe` immediately
when `WorkshopItems` initializes.

```java [WorkshopItems.java]
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

Call the holder once from the common entrypoint.

```java [LightWorkshop.java]
@Override
public void onInitialize() {
    WorkshopItems.initialize();
}
```

**Expected result:** the game registry contains `lightworkshop:lumen_probe`. Finish its language,
model, and texture files in the [Lumen Probe workshop](../workshop/probe.md), then run
`/give @s lightworkshop:lumen_probe`.

## Pick the Wrapper That Owns the Task

Each common wrapper stores a mod ID, converts a path into a `ResourceLocation`, and performs one
specific registration.

| Wrapper | Result |
| --- | --- |
| `ItemsRegistry` | Registers standalone items and block items after assigning their item key. |
| `BlocksRegistry` | Constructs and registers a block after assigning its block key. |
| `BlockEntitiesRegistry` | Registers a prebuilt type, or builds one whose factory receives its own type. |
| `ItemGroupsRegistry` | Builds and registers a `CreativeModeTab`. |
| `DataComponentTypesRegistry` | Registers a component from codecs or a builder operator. |
| `ParticlesRegistry` | Creates and registers a `SimpleParticleType`. |
| `ScreenHandlersRegistry` | Registers an `ExtendedScreenHandlerType` with its opening-data codec. |
| `RegistryKeys` | Creates a namespaced `ResourceKey<Registry<T>>`; it does not register contents. |

The one-argument constructors use
`ResourceLocation.fromNamespaceAndPath(modId, path)`. Use the two-argument constructor only when
the mod centralizes ID creation:

```java
ItemsRegistry items = new ItemsRegistry(LightWorkshop.MOD_ID, LightWorkshop::id);
```

The custom function controls `id(path)`; `getModId()` still returns the constructor's mod ID.

## Add a Simple Particle

The particle wrapper has one focused operation:

```java
private static final ParticlesRegistry PARTICLES = new ParticlesRegistry("lightworkshop");

public static final SimpleParticleType LUMEN_SPARK =
        PARTICLES.register("lumen_spark");
```

**Expected result:** `lightworkshop:lumen_spark` is registered. Registration alone does not make it
visible; a client particle factory and particle description/texture resources are still required.

## Add a Menu Type

`ScreenHandlersRegistry` decodes opening data before calling the menu factory. This compact example
reuses Core's `BlockPosPayload` codec:

```java
private static final ScreenHandlersRegistry MENUS =
        new ScreenHandlersRegistry("lightworkshop");

public static final ExtendedScreenHandlerType<ProbeMenu, BlockPosPayload> PROBE_MENU =
        MENUS.register(
                "probe",
                (syncId, inventory, data) ->
                        new ProbeMenu(syncId, inventory, data.blockPos()),
                BlockPosPayload.PACKET_CODEC);
```

The menu constructor in this example receives the synchronization ID, player inventory, and
decoded block position. Client screen registration and opening the menu remain separate Fabric
tasks.

## Understand Reloadable Client Registries

`ReloadableRegistry<T>` belongs to `glue-render` and does **not** extend `GlueRegistry`. It combines
two layers:

- Java entries registered with `register(id, value)` remain until the client exits.
- `reload(entries)` atomically replaces the entire JSON layer and increments `version()`.
- A JSON value wins when both layers contain the same ID.

Use `getOrRegister` when code can provide a permanent fallback for a data-driven client resource:

```java
private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
        "lightworkshop", "probe_glow");

public static GluePipeline probeGlow() {
    return GlueClientRegistries.PIPELINES.getOrRegister(
            GLOW,
            () -> GluePipeline.entity(
                    GLOW,
                    ResourceLocation.fromNamespaceAndPath("lightworkshop", "core/entity"),
                    ResourceLocation.fromNamespaceAndPath("lightworkshop", "core/probe_glow")));
}
```

The supplier runs only on a miss and must be non-null and return non-null. A later JSON entry
overrides the fallback; removing that JSON entry on a later reload reveals the Java fallback again.

::: details Reloadable registry locations and operations
| Registry | Value | Resource-pack location |
| --- | --- | --- |
| `GlueClientRegistries.PIPELINES` | `GluePipeline` | `assets/<namespace>/glue/pipelines/<name>.json` |
| `GlueClientRegistries.POST_CHAINS` | `PostShaderHandle` | `assets/<namespace>/glue/post_chains/<name>.json` |
| `GlueClientRegistries.OUTLINE_RENDERERS` | `GlueOutlineRenderer` | `assets/<namespace>/glue/outlines/<name>.json` |
| `GlueClientRegistries.TIMED_EFFECT_DEFINITIONS` | `TimedEffectDefinition` | `assets/<namespace>/glue/post_effects/<name>.json` |

`get(id)` is nullable. `containsKey(id)` checks both layers. `getAll()` returns an unmodifiable
merged map with JSON priority; `size()` counts distinct merged IDs; iteration uses the same merged
entries. `getName()` returns the diagnostic name. Constructing a registry does not discover files:
a resource reload listener must parse resources and call `reload(entries)`.
:::

::: details Client registration helpers
`KeybindingsRegistry` is client-only despite living in `glue-core`; see
[Keybindings](./keybindings.md). The following helpers live in `glue-render` and must be used from
client initialization:

| Helper | Behavior |
| --- | --- |
| `BlocksRendererRegistry` | Configures cutout layers and block color providers; it has no mod ID and does not extend `GlueRegistry`. |
| `CoreShaderRegistry` | `registerRaw` registers with Minecraft and tracks only in that helper; `registerPipeline` adds a permanent `GluePipeline` entry. |
| `PostShaderRegistry` | Adds permanent `PostShaderHandle` entries. |
| `OutlineRendererRegistry` | Adds permanent `GlueOutlineRenderer` entries. |
| `TimedEffectRegistry` | Adds permanent `TimedEffectDefinition` entries. |
:::

## Next Steps

- [Finish the Lumen Probe](../workshop/probe.md).
- [Add items, a creative tab, and custom data](./items.md).
- [Register the optional pedestal and its block entity](./blocks.md).
- [Build client pipelines](../rendering/pipelines.md).
