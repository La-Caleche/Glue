package fr.lacaleche.glue.gametest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional discovery metadata for a concrete Fabric client gametest. Unannotated implementations
 * are also discovered, including through inheritance. Metadata belongs to the annotated class
 * only; it is not inherited from a fixture or enclosing class.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ClientTestSpec {

    /** Overrides the kebab-case class name, with its ClientTest/GameTest/Test suffix removed. */
    String value() default "";

    /** Excludes this test from the default selection; use for manual, network or shaderpack tests. */
    boolean explicitOnly() default false;
}
