package fr.lacaleche.glue.mcsx.client.style.internal;

import java.util.List;

public record StyleRule(
        StyleSelector selector,
        List<StyleDeclaration> declarations,
        int specificity,
        int sourceOrder
) {
}
