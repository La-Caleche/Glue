# Registry System

All Glue registries extend `GlueRegistry`, which provides a mod-namespaced `id(path)` helper.

## Base Class

```java
public abstract class GlueRegistry {
    public String getModId();
    public ResourceLocation id(String path);
}
```

Every registry is instantiated with your mod ID and an optional ID function:

```java
public static final BlocksRegistry BLOCKS = new BlocksRegistry("mymod", MyMod::id);
```

## Available Registries

### Standard Registries (Vanilla wrappers)

| Registry | Wraps | Purpose |
|---|---|---|
| `BlocksRegistry` | `BuiltInRegistries.BLOCK` | Blocks with properties |
| `ItemsRegistry` | `BuiltInRegistries.ITEM` | Items, block items |
| `BlockEntitiesRegistry` | `BuiltInRegistries.BLOCK_ENTITY_TYPE` | Block entity types |
| `ItemGroupsRegistry` | `BuiltInRegistries.CREATIVE_MODE_TAB` | Creative mode tabs |
| `DataComponentTypesRegistry` | `BuiltInRegistries.DATA_COMPONENT_TYPE` | Custom data components |
| `ParticlesRegistry` | `BuiltInRegistries.PARTICLE_TYPE` | Simple particle types |
| `ScreenHandlersRegistry` | `BuiltInRegistries.MENU` | Extended screen handlers |
| `KeybindingsRegistry` | Fabric `KeyBindingHelper` | Keybinds with callbacks |
| `BlocksRendererRegistry` | Fabric `BlockRenderLayerMap` | Cutout layers, tints |

### Client Shader Registries (ReloadableRegistry-backed)

These registries support **both** Java registration and JSON data-driven loading. See [ReloadableRegistry](#reloadableregistry) below.

| Registry | Stores | JSON path |
|---|---|---|
| `CoreShaderRegistry` | `RenderPipeline` / `GluePipeline` | `glue/pipelines/<name>.json` |
| `PostShaderRegistry` | `PostShaderHandle` | `glue/post_chains/<name>.json` |
| `OutlineRendererRegistry` | `GlueOutlineRenderer` | `glue/outlines/<name>.json` |
| `TimedEffectRegistry` | `TimedEffectDefinition` | `glue/post_effects/<name>.json` |

## Pattern

All registries follow the same pattern:

```java
public class MyRegistries {
    public static final BlocksRegistry BLOCKS = new BlocksRegistry("mymod", MyMod::id);
    public static final ItemsRegistry ITEMS = new ItemsRegistry("mymod", MyMod::id);

    public static final Block MY_BLOCK = BLOCKS.register("my_block", MyBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F));

    public static final Item MY_BLOCK_ITEM = ITEMS.register(MY_BLOCK, new Item.Properties());

    // Call from onInitializeClient to trigger class loading
    public static void init() {}
}
```

---

## ReloadableRegistry

`ReloadableRegistry<T>` is the unified storage behind all client shader registries. It has two layers:

- **Java layer** — entries registered via Java code at mod init. Permanent, survive F3+T reloads.
- **JSON layer** — entries loaded from JSON files on each resource reload. Atomically replaced — deleted files simply disappear, no stale entries.

Lookup via `.get(id)` checks JSON first, then Java. This means JSON can override Java entries with the same ID (resource pack overrides).

### GlueClientRegistries

All instances live in `GlueClientRegistries`:

```java
GlueClientRegistries.PIPELINES              // ReloadableRegistry<GluePipeline>
GlueClientRegistries.POST_CHAINS            // ReloadableRegistry<PostShaderHandle>
GlueClientRegistries.OUTLINE_RENDERERS      // ReloadableRegistry<GlueOutlineRenderer>
GlueClientRegistries.TIMED_EFFECT_DEFINITIONS // ReloadableRegistry<TimedEffectDefinition>
```

### Lookup

```java
// Get a specific entry (returns null if not found)
GluePipeline pipeline = GlueClientRegistries.PIPELINES.get(myId);

// Check existence
boolean exists = GlueClientRegistries.PIPELINES.containsKey(myId);

// Iterate all entries (Java + JSON merged)
for (Map.Entry<ResourceLocation, GluePipeline> entry : GlueClientRegistries.PIPELINES) {
    // ...
}
```

### F3+T Behavior

On resource reload, each JSON loader calls `registry.reload(newEntries)` which atomically replaces the entire JSON layer. If a JSON file is deleted from a resource pack and F3+T is pressed, the entry disappears cleanly — no crash, no stale data.

Java-registered entries are never affected by reloads.
