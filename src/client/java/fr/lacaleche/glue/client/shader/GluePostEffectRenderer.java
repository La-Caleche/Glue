package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.registries.ReloadableRegistry;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinition;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/post");

    private final CrossFrameResourcePool resourcePool;
    private final List<PostShaderHandle> toggleEffects = new ArrayList<>();
    private final List<TimedPostEffect> timedEffects = new ArrayList<>();
    private boolean registered = false;

    /**
     * Instances baked lazily from {@link GlueClientRegistries#TIMED_EFFECT_DEFINITIONS},
     * keyed by id. Each is added to {@link #timedEffects} exactly once. Invalidated
     * when the registry's {@link ReloadableRegistry#version() version} changes.
     */
    private final Map<ResourceLocation, TimedPostEffect> dataDrivenEffects = new HashMap<>();
    private int dataDrivenVersion = -1;

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
     * Removes a previously-added timed effect so it is no longer ticked or
     * rendered. Useful for effects with a transient lifetime (e.g. baked from
     * data definitions that are dropped on resource reload).
     *
     * @return {@code true} if the effect was present and removed
     */
    public boolean removeTimed(TimedPostEffect effect) {
        return timedEffects.remove(effect);
    }

    /**
     * Triggers a data-driven timed effect by its id, resolving it from
     * {@link GlueClientRegistries#TIMED_EFFECT_DEFINITIONS}. The effect is baked
     * and attached to this renderer on first use, then the same instance is
     * reused (re-triggered) on subsequent calls. The cache is dropped
     * automatically after a resource reload, so edited JSON is picked up.
     *
     * <p>No-op (with a warning) if the id is not registered, or (with an error)
     * if baking fails — e.g. the referenced post chain is missing.</p>
     */
    public void triggerTimed(ResourceLocation id) {
        TimedPostEffect effect = resolveDataDriven(id);
        if (effect != null) {
            effect.trigger();
        }
    }

    /** Whether the data-driven effect for {@code id} is baked and currently playing. */
    public boolean isTimedActive(ResourceLocation id) {
        refreshDataDrivenIfStale();
        TimedPostEffect effect = dataDrivenEffects.get(id);
        return effect != null && effect.isActive();
    }

    /** Stops the data-driven effect for {@code id} if it is currently baked. */
    public void stopTimed(ResourceLocation id) {
        TimedPostEffect effect = dataDrivenEffects.get(id);
        if (effect != null) {
            effect.stop();
        }
    }

    private TimedPostEffect resolveDataDriven(ResourceLocation id) {
        refreshDataDrivenIfStale();

        TimedPostEffect cached = dataDrivenEffects.get(id);
        if (cached != null) {
            return cached;
        }

        TimedEffectDefinition def = GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.get(id);
        if (def == null) {
            LOGGER.warn("[Glue] Timed effect '{}' not found in registry", id);
            return null;
        }
        try {
            TimedPostEffect effect = def.bake();
            dataDrivenEffects.put(id, effect);
            timedEffects.add(effect);
            return effect;
        } catch (Exception e) {
            LOGGER.error("[Glue] Failed to bake timed effect '{}'", id, e);
            return null;
        }
    }

    /** Drops baked data-driven effects (and detaches them) when the registry has reloaded. */
    private void refreshDataDrivenIfStale() {
        int current = GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.version();
        if (current == dataDrivenVersion) {
            return;
        }
        for (TimedPostEffect effect : dataDrivenEffects.values()) {
            effect.stop();
            timedEffects.remove(effect);
        }
        dataDrivenEffects.clear();
        dataDrivenVersion = current;
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
