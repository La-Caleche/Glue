package fr.lacaleche.glue.mcsx.client.style.internal;

public record StyleDeclaration(
        String property,
        StyleValue value,
        int line,
        int column,
        int sourceOrder
) {
}
