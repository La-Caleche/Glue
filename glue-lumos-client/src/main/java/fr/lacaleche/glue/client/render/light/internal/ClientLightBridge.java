package fr.lacaleche.glue.client.render.light.internal;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightAttachment;
import fr.lacaleche.glue.lumos.LightHandle;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.lumos.LumosClientBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Map;

/** Wires {@link Lumos}'s client-side calls to the renderer's light list and the server sync mirror. */
@Environment(EnvType.CLIENT)
public final class ClientLightBridge implements LumosClientBridge {

    @Override
    public void spawn(Light light) {
        LightManager.getInstance().add(light);
    }

    @Override
    public void despawn(Light light) {
        LightManager.getInstance().remove(light);
    }

    @Override
    public LightHandle attach(Light light, LightAttachment attachment) {
        return LightManager.getInstance().attach(light, attachment);
    }

    @Override
    public List<Light> active() {
        return LightManager.getInstance().snapshot();
    }

    @Override
    public Map<Long, Light> placed() {
        return ClientPersistentLights.current();
    }

    @Override
    public void requestPlace(Light light) {
        ClientPersistentLights.requestPlace(light);
    }

    @Override
    public void requestUpdate(long id, Light light) {
        ClientPersistentLights.requestUpdate(id, light);
    }

    @Override
    public void requestRemove(long id) {
        ClientPersistentLights.requestRemove(id);
    }
}
