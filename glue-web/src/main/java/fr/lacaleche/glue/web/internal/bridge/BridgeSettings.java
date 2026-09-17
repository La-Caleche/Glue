package fr.lacaleche.glue.web.internal.bridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What a surface exposes to trusted pages.
 *
 * @param origin the only origin allowed to use the bridge, or null for no bridge
 */
public record BridgeSettings(WebOrigin origin, ActionTable actions, Map<String, Supplier<?>> states,
                             Map<String, SlotBinding> slots) {

    public static final BridgeSettings NONE = new BridgeSettings(null, ActionTable.EMPTY, Map.of(), Map.of());

    public BridgeSettings {
        Objects.requireNonNull(actions, "actions");
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        slots = Map.copyOf(slots);
    }

}
