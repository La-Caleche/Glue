# Keybindings

Glue's `KeybindingsRegistry` handles keybind registration and automatically polls for key presses via `ClientTickEvents`.

## Setup

```java
public static final KeybindingsRegistry KEYS = new KeybindingsRegistry("mymod", MyMod::id);
```

The constructor registers a `ClientTickEvents.END_CLIENT_TICK` listener that checks all registered keybinds each tick.

## Registering Keybinds

```java
public static final KeyMapping TOGGLE_EFFECT = KEYS.register(
        "toggle_effect",           // → key.mymod.toggle_effect
        "key.categories.mymod",    // Translation key for the category
        GLFW.GLFW_KEY_B,           // Default key
        client -> {
            // Called when the key is pressed
            boolean state = MyEffects.toggle();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Effect: " + (state ? "ON" : "OFF")),
                        true);
            }
        });
```

## With Input Type

```java
public static final KeyMapping MOUSE_ACTION = KEYS.register(
        "mouse_action",
        "key.categories.mymod",
        GLFW.GLFW_MOUSE_BUTTON_4,
        InputConstants.Type.MOUSE,
        client -> { /* ... */ });
```

## Initialization

Call from `onInitializeClient()` to trigger class loading:

```java
MyKeybinds.registerKeybinds(); // Empty method — static fields do the work
```

## Translations

Add to `en_us.json`:

```json
{
    "key.categories.mymod": "My Mod",
    "key.mymod.toggle_effect": "Toggle Effect"
}
```
