package fr.lacaleche.glue.mcsx.client.internal.compat;

import fr.lacaleche.glue.compat.ModCompatManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Field;

/** Optional Axiom state used by MCSX without linking Axiom classes. */
@Environment(EnvType.CLIENT)
public final class AxiomCompatManager extends ModCompatManager {

    private static final String AXIOM = "axiom";
    private static final String CLIENT_EVENTS = "com.moulberry.axiom.ClientEvents";
    private static final String EDITOR_UI = "com.moulberry.axiom.editor.EditorUI";

    private static volatile boolean toggleKeyResolved;
    private static KeyMapping toggleKey;

    private AxiomCompatManager() {
    }

    public static boolean isEditorUiEnabled() {
        return probeBoolean(AXIOM, EDITOR_UI, "isEnabled", false);
    }

    public static boolean matchesEditorUiToggle(int key, int scanCode) {
        KeyMapping mapping = editorUiToggleKey();
        return mapping != null && mapping.matches(key, scanCode);
    }

    public static boolean ownsCursor() {
        return isEditorUiEnabled();
    }

    private static KeyMapping editorUiToggleKey() {
        if (!FabricLoader.getInstance().isModLoaded(AXIOM)) return null;
        if (toggleKeyResolved) return toggleKey;

        synchronized (AxiomCompatManager.class) {
            if (toggleKeyResolved) return toggleKey;

            try {
                Class<?> clientEvents = Class.forName(CLIENT_EVENTS, true,
                        AxiomCompatManager.class.getClassLoader());
                Field field = clientEvents.getField("toggleEditorUiKeyBind");
                Object value = field.get(null);
                if (value instanceof KeyMapping mapping) toggleKey = mapping;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
            toggleKeyResolved = true;
            return toggleKey;
        }
    }
}
