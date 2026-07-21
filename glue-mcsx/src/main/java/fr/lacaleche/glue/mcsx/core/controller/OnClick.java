package fr.lacaleche.glue.mcsx.core.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as the click handler for the element whose {@code id} matches
 * {@link #value()}. (The alternative wiring is an {@code onClick={method}} binding in the document.)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnClick {
    String value();
}
