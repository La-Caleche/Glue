package fr.lacaleche.glue.lumos;

import java.util.List;
import java.util.Map;

/**
 * How {@link Lumos} reaches the client renderer without the common module depending on it. The Lumos
 * client initializer installs the implementation; on a server none exists, which is exactly what makes
 * the client-side half of {@link Lumos} a set of no-ops there.
 */
public interface LumosClientBridge {

    void spawn(Light light);

    void despawn(Light light);

    LightHandle attach(Light light, LightAttachment attachment);

    /** Every light the renderer will draw this frame. */
    List<Light> active();

    /** The world lights the server has synced to this client. */
    Map<Long, Light> placed();

    void requestPlace(Light light);

    void requestUpdate(long id, Light light);

    void requestRemove(long id);
}
