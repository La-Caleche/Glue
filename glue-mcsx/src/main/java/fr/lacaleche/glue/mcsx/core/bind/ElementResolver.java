package fr.lacaleche.glue.mcsx.core.bind;

import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxText;
import fr.lacaleche.glue.mcsx.core.mcsx.Tags;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.VariantStyleResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The third resolver, completing the set: {@link BindingResolver} answers <em>ref → value</em> and
 * {@link VariantStyleResolver} answers <em>class string → StyleSpec</em>; this one answers
 * <em>element × scope → the thing it binds to</em> — its handler, its writable signal, the scopes it
 * introduces, its text parts.
 *
 * <p>Platform-free and stateless: the {@link Scope} is a parameter, never a field, so one instance
 * serves a whole bind and every deferred re-resolution inside an effect. It holds the rules that are
 * easiest to get wrong (a one-arg handler binds to the nearest <em>loop</em> item, not the innermost
 * scope; a forwarded handler binds at the call site, not in the callee) — which is exactly why they
 * live here, on the testable side of the boundary, rather than in the view binder.
 */
public final class ElementResolver {

    private final BindingResolver bindings;
    private final VariantStyleResolver styles;

    public ElementResolver(BindingResolver bindings, VariantStyleResolver styles) {
        this.bindings = bindings;
        this.styles = styles;
    }

    /**
     * {@code {ref}} → a live value supplier (dotted paths ok); reading it tracks the dependency.
     *
     * <p>The scope is captured, not read at call time. A supplier outlives the build — it is
     * re-invoked from an effect long after the binder has left this part of the tree — so a
     * {@code ref} naming a {@code <for>} item ({@code cond={item.open}}) would otherwise resolve
     * against whatever scope happens to be current, i.e. {@code null}, and throw.
     */
    public Supplier<Object> binding(String ref, Scope scope, McsxElement element) {
        return () -> bindings.evaluate(ref, scope, element);
    }

    /** {@code {ref}} → the raw writable {@link Signal} at the leaf, or {@code null} if read-only. */
    public Signal<?> signal(String ref, Scope scope, McsxElement element) {
        return bindings.resolveSignal(ref, scope, element);
    }

    /**
     * Resolves a click handler by name, or null if none matches. A {@code name} may be a controller
     * method directly, or a scope prop holding a method name (how a component forwards a caller's
     * {@code onClick={handler}} down to its root, e.g. {@code <button onClick={onClick}>}). Returning
     * null — rather than throwing — lets an unfilled optional handler prop be a no-op.
     */
    public Runnable handlerOrNull(String name, Scope scope) {
        String methodName = name;
        for (Scope s = scope; s != null; s = s.parent()) {
            if (!s.name().equals(name)) {
                continue;
            }
            // A forwarded handler arrives already bound to the caller's scope.
            if (s.value() instanceof Runnable forwarded) {
                return forwarded;
            }
            if (s.value() instanceof String literal) {
                methodName = literal;
                break;
            }
        }
        Method noArg = bindings.findMethod(methodName, 0);
        if (noArg != null) {
            return () -> bindings.invoke(noArg, null);
        }
        Method oneArg = bindings.findMethod(methodName, 1);
        if (oneArg != null) {
            Object item = loopItem(scope);
            return () -> bindings.invoke(oneArg, item);
        }
        return null;
    }

    /**
     * The current {@code <for>} item, for one-arg handlers (§9.7). The nearest <em>loop</em> scope,
     * not the innermost scope of any kind: a {@code select=}/{@code <state>} scope pushed between the
     * row and the handler must not shadow the row item. Null outside any loop.
     */
    public Object loopItem(Scope scope) {
        Scope loop = scope != null ? scope.nearestLoopItem() : null;
        return loop != null ? BindingResolver.unwrap(loop.value()) : null;
    }

    /**
     * What a click on this element does, or null if nothing: an explicit {@code onClick={…}}, else a
     * controller method annotated for the element's {@code id}, else the {@code toggle=}/{@code
     * select=} state write. The whole decision is platform-free; only attaching it to a View is not.
     */
    public Runnable clickAction(McsxElement element, Scope scope) {
        McsxAttribute click = bindingAttribute(element, "onClick");
        if (click != null) {
            return handlerOrNull(click.value(), scope);
        }
        String id = element.attribute("id");
        if (id != null) {
            Method annotated = bindings.findOnClickMethod(id);
            if (annotated != null) {
                Object item = loopItem(scope);
                return () -> bindings.invoke(annotated, item);
            }
        }
        return stateWriter(element, scope);
    }

    /**
     * {@code toggle={signal}} flips a Boolean on click; {@code select={signal} value="a"} writes a
     * choice. Without these a component could render a checkbox but never check it — it can read a
     * caller's signal, and it cannot write one. This is what lets checkbox/switch/radio be markup.
     */
    public Runnable stateWriter(McsxElement element, Scope scope) {
        List<Runnable> writers = new ArrayList<>(2);

        McsxAttribute select = bindingAttribute(element, "select");
        if (select != null) {
            String value = stringAttribute(element, "value", scope);
            if (value == null) {
                throw new McsxBindException("select={…} requires value=\"…\"",
                        element.line(), element.column());
            }
            @SuppressWarnings("unchecked")
            Signal<String> choice = (Signal<String>) writableSignal(select, scope, element);
            writers.add(() -> choice.set(value));
        }

        McsxAttribute toggle = bindingAttribute(element, "toggle");
        if (toggle != null) {
            @SuppressWarnings("unchecked")
            Signal<Boolean> flag = (Signal<Boolean>) writableSignal(toggle, scope, element);
            writers.add(() -> flag.set(!Boolean.TRUE.equals(flag.get())));
        }

        if (writers.isEmpty()) {
            return null;
        }
        // Both may sit on one element: a select option picks its value AND closes the menu.
        return () -> writers.forEach(Runnable::run);
    }

    /** The signal behind a writing attribute; a read-only binding there is an authoring error. */
    public Signal<?> writableSignal(McsxAttribute attribute, Scope scope, McsxElement element) {
        Signal<?> signal = signal(attribute.value(), scope, element);
        if (signal == null) {
            throw new McsxBindException(attribute.name() + "={" + attribute.value()
                    + "} needs a writable Signal", element.line(), element.column());
        }
        return signal;
    }

    /**
     * Dismissing an overlay with no {@code onClose} handler writes its {@code open} signal. Merely
     * removing the layer would leave {@code open} true, so the gate would never fire again and the
     * overlay could never reopen. Null when {@code open} is a read-only binding.
     */
    public Runnable dismissWriter(McsxAttribute openAttr, Scope scope, McsxElement element) {
        Signal<?> raw = signal(openAttr.value(), scope, element);
        if (raw == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Signal<Boolean> flag = (Signal<Boolean>) raw;
        return () -> flag.set(false);
    }

    /**
     * Exposes {@code selected} to the subtree of a {@code select={signal} value="a"} element: true
     * when the signal currently holds this element's value. Reading it inside a styling effect tracks
     * the signal, so a radio restyles itself without the caller computing anything — the comparison
     * lives here rather than in the markup, where bindings must stay dumb.
     */
    public Scope selectionScope(McsxElement element, Scope scope) {
        McsxAttribute select = bindingAttribute(element, "select");
        String value = select == null ? null : stringAttribute(element, "value", scope);
        if (value == null) {
            return scope;
        }
        Supplier<Object> current = binding(select.value(), scope, element);
        Supplier<Object> selected = () -> value.equals(current.get());
        return Scope.of("selected", selected, scope);
    }

    /**
     * {@code <state name="open" initial="false"/>} declares a {@link Signal} local to this element's
     * subtree. Without it a component has no state of its own — a Select could not remember whether
     * its menu is open, and every caller would have to hold that signal for it.
     *
     * <p>{@code initial} is a Boolean when it reads {@code true}/{@code false}, otherwise a String;
     * those are the two types {@code toggle=} and {@code select=} write.
     */
    public Scope stateScope(McsxElement element, Scope parent) {
        Scope result = parent;
        for (McsxContent child : element.children()) {
            if (!(child instanceof McsxElement state) || !state.tag().equals("state")) {
                continue;
            }
            String name = state.attribute("name");
            if (name == null || name.isBlank()) {
                throw new McsxBindException("<state> requires name=\"…\"", state.line(), state.column());
            }
            String initial = state.attribute("initial");
            Object seed = "true".equals(initial) || "false".equals(initial)
                    ? Boolean.valueOf(initial) : initial;
            result = Scope.of(name, new Signal<>(seed), result);
        }
        return result;
    }

    /**
     * The value passed for a component's binding prop ({@code prop={ref}}). A ref naming a controller
     * handler method is forwarded by name so the component can wire it lazily in its own scope (e.g.
     * {@code onClick={onClick}}); anything else resolves to its live holder/value.
     */
    public Object propValue(String ref, Scope scope, McsxElement element) {
        if (ref.indexOf('.') < 0
                && (bindings.findMethod(ref, 0) != null || bindings.findMethod(ref, 1) != null)) {
            // Bind the handler HERE, at the call site, so a one-arg method still receives the
            // enclosing <for> item. A component replaces the scope with its own props, so a forwarded
            // method name resolved later would pick up a prop instead of the loop variable.
            return handlerOrNull(ref, scope);
        }
        if (ref.indexOf('.') < 0) {
            return bindings.resolveHead(ref, scope, element);
        }
        return binding(ref, scope, element);
    }

    /** The literal and {@code {{binding}}} parts of a text run, in order; a binding becomes a supplier. */
    public void collectParts(McsxText text, McsxElement owner, Scope scope, List<Object> parts) {
        for (McsxText.Part part : text.parts()) {
            parts.add(part.binding() ? binding(part.value(), scope, owner) : part.value());
        }
    }

    /** True when some part is a live {@code {{binding}}}, so the text has to re-render on change. */
    public static boolean isDynamic(List<Object> parts) {
        return parts.stream().anyMatch(Supplier.class::isInstance);
    }

    public static CharSequence compose(List<Object> parts) {
        StringBuilder builder = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Supplier<?> supplier) {
                builder.append(String.valueOf(supplier.get()));
            } else {
                builder.append((String) part);
            }
        }
        return builder.toString();
    }

    /**
     * A text-valued attribute: its literal value, or — when written as {@code name={ref}} — the ref
     * resolved against the current scope. That is what lets a component forward one of its props on
     * to a base tag, as {@code <input placeholder={placeholder}/>} does.
     */
    public String stringAttribute(McsxElement element, String name, Scope scope) {
        String literal = element.attribute(name);
        if (literal != null) {
            return literal;
        }
        McsxAttribute bound = bindingAttribute(element, name);
        return bound != null ? styles.interpolatedValue(bound.value(), element, scope) : null;
    }

    /** An attribute by name, binding or literal. {@link McsxElement#attribute} sees literals only. */
    public static McsxAttribute attributeNamed(McsxElement element, String name) {
        for (McsxAttribute attribute : element.attributes()) {
            if (attribute.name().equals(name)) {
                return attribute;
            }
        }
        return null;
    }

    /** An attribute by name, only when written as a {@code {ref}} binding. */
    public static McsxAttribute bindingAttribute(McsxElement element, String name) {
        for (McsxAttribute attribute : element.attributes()) {
            if (attribute.name().equals(name) && attribute.binding()) {
                return attribute;
            }
        }
        return null;
    }

    /** True when the {@code class} carries a {@code {name}} hole, so it can change after bind time. */
    public static boolean hasInterpolatedClass(McsxElement element) {
        String classes = element.attribute("class");
        return classes != null && classes.indexOf('{') >= 0;
    }

    /** {@code <variants>}/{@code <case>}/{@code <state>} are declarative config, not rendered content. */
    public static boolean isVariantConfig(McsxElement element) {
        return Tags.CONFIG.contains(element.tag());
    }

    /** A required {@code name={ref}} binding, or a positioned error naming what the tag needs. */
    public static McsxAttribute requireBinding(McsxElement element, String name, String expected) {
        McsxAttribute attribute = bindingAttribute(element, name);
        if (attribute == null) {
            throw new McsxBindException(expected, element.line(), element.column());
        }
        return attribute;
    }

    /** A required literal attribute, or a positioned error naming what the tag needs. */
    public static String requireAttribute(McsxElement element, String name, String expected) {
        String value = element.attribute(name);
        if (value == null || value.isBlank()) {
            throw new McsxBindException(expected, element.line(), element.column());
        }
        return value;
    }
}
