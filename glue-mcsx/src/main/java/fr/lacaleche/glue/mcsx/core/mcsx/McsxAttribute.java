package fr.lacaleche.glue.mcsx.core.mcsx;

/**
 * A single attribute on an element.
 *
 * @param name    the attribute name
 * @param value   for a literal attribute, the string value (or {@code ""} for a boolean
 *                attribute); for a binding, the reference expression inside the braces
 * @param binding {@code true} when the attribute was written as {@code name={ref}}
 */
public record McsxAttribute(String name, String value, boolean binding) {
}
