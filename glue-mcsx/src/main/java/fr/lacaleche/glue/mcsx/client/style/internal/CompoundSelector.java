package fr.lacaleche.glue.mcsx.client.style.internal;

import java.util.Set;

public record CompoundSelector(
        String type,
        Set<String> classes,
        Set<String> states,
        String part
) {
}
