package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the render-loop lifecycle for a collection of post-processing effects.
 *
 * <p>Handles all boilerplate automatically: shadow-pass guarding, partial-tick
 * retrieval, resource-pool end-of-frame, and toggle/timed dispatch. Mods only
 * need to register their effects and call {@link #register()} once.</p>
 *
 * <pre>{@code
 * GluePostEffectRenderer renderer = new GluePostEffectRenderer();
 * renderer.addToggle(MyShaders.BLUR);
 * renderer.addTimed(MY_IMPACT_EFFECT);
 * renderer.register(); // call once during mod init
 * }</pre>
 *
 * <p>The internal {@link CrossFrameResourcePool} has a default depth of 3.
 * Use {@link #GluePostEffectRenderer(int)} if you need a different depth.</p>
 */
@Environment(EnvType.CLIENT)
public class GluePostEffectRenderer {

    private final CrossFrameResourcePool resourcePool;
    private final List<PostShaderHandle> toggleEffects = new ArrayList<>();
    private final List<TimedPostEffect> timedEffects = new ArrayList<>();
    private boolean registered = false;

    /**
     * Creates a renderer with a 3-frame resource pool.
     */
    public GluePostEffectRenderer() {
        this(3);
    }

    /**
     * Creates a renderer with the specified resource pool depth.
     */
    public GluePostEffectRenderer(int poolDepth) {
        this.resourcePool = new CrossFrameResourcePool(poolDepth);
    }

    /**
     * Attaches this renderer to the render and tick event hooks.
     * Must be called exactly once, from your mod's client initializer.
     *
     * @throws IllegalStateException if called more than once on the same instance
     */
    public GluePostEffectRenderer register() {
        if (registered) {
            throw new IllegalStateException("GluePostEffectRenderer.register() called twice");
        }
        registered = true;
        RenderEvents.POST_WORLD_RENDER.register(this::onRender);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        return this;
    }

    /**
     * Adds a toggle effect. It renders every frame while toggled on.
     */
    public GluePostEffectRenderer addToggle(PostShaderHandle handle) {
        toggleEffects.add(handle);
        return this;
    }

    /**
     * Adds a timed effect. It renders only while {@link TimedPostEffect#isActive()}.
     */
    public GluePostEffectRenderer addTimed(TimedPostEffect effect) {
        timedEffects.add(effect);
        return this;
    }

    /**
     * Toggles a previously-added handle on or off.
     *
     * @return the new state — {@code true} means the effect is now active
     */
    public boolean toggle(PostShaderHandle handle) {
        if (!toggleEffects.remove(handle)) {
            toggleEffects.add(handle);
            return true;
        }
        return false;
    }

    /**
     * Returns whether a given handle is currently toggled on.
     */
    public boolean isToggled(PostShaderHandle handle) {
        return toggleEffects.contains(handle);
    }

    private void onTick(Minecraft mc) {
        if (mc.isPaused()) return;
        for (TimedPostEffect effect : timedEffects) {
            effect.tick();
        }
    }

    private void onRender() {
        if (RenderCompat.isRenderingShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderTarget target = mc.getMainRenderTarget();

        for (PostShaderHandle handle : toggleEffects) {
            handle.apply(target, resourcePool);
        }
        for (TimedPostEffect effect : timedEffects) {
            effect.render(mc, resourcePool, partialTick);
        }

        resourcePool.endFrame();
    }
}
