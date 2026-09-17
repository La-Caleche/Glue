package fr.lacaleche.glue.web.bridge;

import fr.lacaleche.glue.web.WebSurface;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes a method to trusted pages, which call it with the bridge's {@code call(name, payload)}.
 *
 * <p>The method runs on Minecraft's client thread and may be private or static. It accepts at most
 * one payload parameter, decoded from JSON with Gson (records work), and optionally a
 * {@link WebSurface} parameter that receives the calling surface. The return value is encoded as
 * JSON; a {@link java.util.concurrent.CompletionStage} settles the page's promise when it completes.
 * A thrown exception rejects the promise with its message. Argument and state exceptions are treated
 * as expected refusals; other exceptions are also logged.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebAction {

    /** The name pages call; defaults to the method name. */
    String value() default "";
}
