# Registry System

All Glue registries extend `GlueRegistry`, which provides a mod-namespaced `id(path)` helper.

## Base Class

```java
public abstract class GlueRegistry {
    private final String modId;
    private final Function<String, ResourceLocation> idFunction;

    public String getModId();
    public ResourceLocation id(String path);
}
```

Every registry is instantiated with your mod ID and an optional ID function:

```java
public static final BlocksRegistry BLOCKS = new BlocksRegistry("mymod", MyMod::id);
```

## Available Registries

| Registry | Wraps | Class |
|---|---|---|
| `BlocksRegistry` | `BuiltInRegistries.BLOCK` | Blocks with properties |
| `ItemsRegistry` | `BuiltInRegistries.ITEM` | Items, block items |
| `BlockEntitiesRegistry` | `BuiltInRegistries.BLOCK_ENTITY_TYPE` | Block entity types |
| `ItemGroupsRegistry` | `BuiltInRegistries.CREATIVE_MODE_TAB` | Creative mode tabs |
| `DataComponentTypesRegistry` | `BuiltInRegistries.DATA_COMPONENT_TYPE` | Custom data components |
| `ParticlesRegistry` | `BuiltInRegistries.PARTICLE_TYPE` | Simple particle types |
| `KeybindingsRegistry` | Fabric `KeyBindingHelper` | Keybinds with callbacks |
| `CoreShaderRegistry` | `RenderPipelines.register()` | Render pipelines |
| `PostShaderRegistry` | `ShaderManager.getPostChain()` | Post-processing shaders |
| `OutlineRendererRegistry` | Custom Glue registry | Block outline renderers |
| `BlocksRendererRegistry` | Fabric `BlockRenderLayerMap` | Cutout layers, tints |
| `ScreenHandlersRegistry` | `BuiltInRegistries.MENU` | Extended screen handlers |

## Pattern

All registries follow the same pattern:

```java
public class MyRegistries {
    // One instance per registry type per mod
    public static final BlocksRegistry BLOCKS = new BlocksRegistry("mymod", MyMod::id);
    public static final ItemsRegistry ITEMS = new ItemsRegistry("mymod", MyMod::id);

    // Register entries as static fields (loaded at class init)
    public static final Block MY_BLOCK = BLOCKS.register("my_block", MyBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F));

    public static final Item MY_BLOCK_ITEM = ITEMS.register(MY_BLOCK, new Item.Properties());

    // Call from onInitializeClient to trigger class loading
    public static void init() {}
}
```
