package fr.lacaleche.glue.mcsx.core.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller class to the {@code .mcsx} document it drives, by resource id
 * ({@code "namespace:path"}, resolving to {@code assets/<namespace>/ui/<path>.mcsx}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UIController {
    String value();
}
