---
title: Keybindings
description: Register one remappable client key that toggles a Light Workshop action at end of tick.
artifact: glue-core
modId: glue
environment: client only
---

# Keybindings

Add one **Toggle Light Workshop** key to Minecraft's Controls screen. Pressing it toggles a client-side
Light Workshop action and displays the new state above the hotbar.

First add the client entrypoint beside the existing common entrypoint in `fabric.mod.json`:

```json [fabric.mod.json]
{
  "entrypoints": {
    "main": ["dev.example.lightworkshop.LightWorkshop"],
    "client": ["dev.example.lightworkshop.LightWorkshopClient"]
  }
}
```

Then register the mapping from that client entrypoint.

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class LightWorkshopClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.lightworkshop";
    private final KeybindingsRegistry keybindings =
            new KeybindingsRegistry("lightworkshop");
    private boolean workshopActionEnabled;

    @Override
    public void onInitializeClient() {
        keybindings.register("toggle_workshop", CATEGORY, GLFW.GLFW_KEY_O, client -> {
            workshopActionEnabled = !workshopActionEnabled;
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal(
                        "Light Workshop: " + (workshopActionEnabled ? "on" : "off")), true);
            }
        });
    }
}
```

Add the generated mapping key and the exact category string to the client language file.

```json [assets/lightworkshop/lang/en_us.json]
{
  "key.categories.lightworkshop": "Light Workshop",
  "key.lightworkshop.toggle_workshop": "Toggle Light Workshop"
}
```

**Expected result:** **Options > Controls > Key Binds** contains a **Light Workshop** category with
**Toggle Light Workshop**, initially bound to `O`. Pressing it in a world alternates the action-bar
message between on and off.

<DocImage title="Toggle Light Workshop in Controls" description="The Minecraft 1.21.8 Key Binds screen filtered to the Light Workshop category, showing Toggle Light Workshop bound to the O key without a conflict indicator." />

::: warning Client-only API inside Core
`KeybindingsRegistry` is packaged in `glue-core`, but it is annotated for the client environment
and imports Minecraft client classes. Reference it only from client source and a client entrypoint,
never from common initialization or dedicated-server code.
:::

::: details Press lifecycle and overloads
Constructing each `KeybindingsRegistry` installs one `ClientTickEvents.END_CLIENT_TICK` listener.
A mapping with a callback is polled there, and the callback runs once for every queued press
consumed during that tick.

The four-argument `register(name, category, keyCode, callback)` overload uses
`InputConstants.Type.KEYSYM`. The five-argument overload accepts an explicit input type, including
`InputConstants.Type.MOUSE`. A null callback is allowed when code only needs registration and will
poll the returned `KeyMapping` itself.

The translation key is always `key.<modId>.<name>`. The category string is used exactly as supplied.
:::

## Next Steps

- [Store the probe's active state on an item stack](./items.md#add-one-immutable-setting).
- [Use undo history for editor-like actions](./utilities.md#make-an-action-undoable).
- [Render a custom scene](../rendering/scene-viewport.md).
