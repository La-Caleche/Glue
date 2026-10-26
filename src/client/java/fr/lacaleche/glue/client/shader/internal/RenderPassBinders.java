package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.systems.RenderPass;
import fr.lacaleche.glue.client.shader.RenderPassBinder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;

import java.util.Map;
import java.util.WeakHashMap;

/** The binders of runtime RenderTypes, looked up from every composite draw. Render thread only. */
@Environment(EnvType.CLIENT)
public final class RenderPassBinders {

    private static final Map<RenderType, RenderPassBinder> BINDERS = new WeakHashMap<>();

    private RenderPassBinders() {
    }

    public static void register(RenderType type, RenderPassBinder binder) {
        BINDERS.put(type, binder);
    }

    public static void unregister(RenderType type) {
        BINDERS.remove(type);
    }

    public static void bind(RenderType type, RenderPass pass) {
        if (BINDERS.isEmpty()) return;
        RenderPassBinder binder = BINDERS.get(type);
        if (binder != null)
            binder.bind(pass);
    }
}
