package fr.lacaleche.glue.registries;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class KeybindingsRegistry extends GlueRegistry {

    private final Map<KeyMapping, Consumer<Minecraft>> keyBindings = new HashMap<>();

    public KeybindingsRegistry(String modId) {
        this(modId, path -> ResourceLocation.fromNamespaceAndPath(modId, path));
    }

    public KeybindingsRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Map.Entry<KeyMapping, Consumer<Minecraft>> entry : this.keyBindings.entrySet()) {
                KeyMapping keyBinding = entry.getKey();
                Consumer<Minecraft> onPress = entry.getValue();

                while (keyBinding.consumeClick()) {
                    onPress.accept(client);
                }
            }
        });
    }

    public KeyMapping register(String name, String category, int keyCode, Consumer<Minecraft> onPress) {
        return register(name, category, keyCode, InputConstants.Type.KEYSYM, onPress);
    }

    public KeyMapping register(String name, String category, int keyCode, InputConstants.Type type,
                               Consumer<Minecraft> onPress) {
        KeyMapping keyBinding = new KeyMapping(
                "key." + this.getModId() + "." + name,
                type,
                keyCode,
                category);

        KeyBindingHelper.registerKeyBinding(keyBinding);

        if (onPress != null) {
            this.keyBindings.put(keyBinding, onPress);
        }
        return keyBinding;
    }
}
