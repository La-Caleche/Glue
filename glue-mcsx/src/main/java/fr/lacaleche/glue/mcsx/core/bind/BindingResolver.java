package fr.lacaleche.glue.mcsx.core.bind;

import fr.lacaleche.glue.mcsx.core.controller.OnClick;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * The reflection engine behind {@code {ref}} bindings (§9.7): resolves a dotted path against the
 * scope chain and the controller's fields/methods, unwrapping {@link Signal}/{@link Computed}
 * holders at each step so reading tracks reactive dependencies. Stateless — the {@link Scope} is
 * always passed in, never held — which is what makes it headless-testable and lets a caller
 * capture a scope once and evaluate against it long after the build has moved on.
 */
public final class BindingResolver {

    private static final Object NOT_FOUND = new Object();

    private final ScreenController controller;

    public BindingResolver(ScreenController controller) {
        this.controller = controller;
    }

    /** Evaluates a dotted {@code ref} to its current (unwrapped) value. */
    public Object evaluate(String ref, Scope scope, McsxElement element) {
        String[] path = ref.split("\\.");
        Object current = unwrap(resolveHead(path[0], scope, element));
        for (int i = 1; i < path.length; i++) {
            if (current == null) {
                throw new McsxBindException("null while resolving '" + ref + "'",
                        element.line(), element.column());
            }
            current = unwrap(navigate(current, path[i], element));
        }
        return current;
    }

    /** {@code ref} → the raw writable {@link Signal} at the leaf, or {@code null} if read-only. */
    public Signal<?> resolveSignal(String ref, Scope scope, McsxElement element) {
        String[] path = ref.split("\\.");
        Object holder;
        if (path.length == 1) {
            holder = resolveHead(path[0], scope, element);
        } else {
            Object current = unwrap(resolveHead(path[0], scope, element));
            for (int i = 1; i < path.length - 1; i++) {
                requireNonNullPath(current, ref, element);
                current = unwrap(navigate(current, path[i], element));
            }
            requireNonNullPath(current, ref, element);
            holder = navigate(current, path[path.length - 1], element);
        }
        return holder instanceof Signal<?> signal ? signal : null;
    }

    /** The head of a path: the innermost scope entry with that name, else a controller field. */
    public Object resolveHead(String head, Scope scope, McsxElement element) {
        for (Scope s = scope; s != null; s = s.parent()) {
            if (s.name().equals(head)) {
                return s.value();
            }
        }
        Object field = controllerField(head);
        if (field != NOT_FOUND) {
            return field;
        }
        throw new McsxBindException("cannot resolve '" + head + "'", element.line(), element.column());
    }

    private Object controllerField(String name) {
        for (Class<?> c = controller.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(controller);
            } catch (NoSuchFieldException ignored) {
                // walk up
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return NOT_FOUND;
    }

    /** One path step: a no-arg method first (records, getters), then a field. */
    public Object navigate(Object target, String name, McsxElement element) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // try a field instead
            } catch (ReflectiveOperationException e) {
                throw new McsxBindException("error reading '" + name + "': " + e.getMessage(),
                        element.line(), element.column());
            }
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // walk up
            } catch (IllegalAccessException e) {
                throw new McsxBindException("error reading '" + name + "'", element.line(), element.column());
            }
        }
        throw new McsxBindException("cannot navigate to '" + name + "' on "
                + target.getClass().getSimpleName(), element.line(), element.column());
    }

    private static void requireNonNullPath(Object value, String ref, McsxElement element) {
        if (value == null) {
            throw new McsxBindException("null while resolving '" + ref + "'",
                    element.line(), element.column());
        }
    }

    public Method findMethod(String name, int parameterCount) {
        for (Class<?> c = controller.getClass(); c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    public Method findOnClickMethod(String id) {
        for (Class<?> c = controller.getClass(); c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                OnClick annotation = method.getAnnotation(OnClick.class);
                if (annotation != null && annotation.value().equals(id)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    public void invoke(Method method, Object argument) {
        try {
            if (method.getParameterCount() == 0) {
                method.invoke(controller);
            } else {
                method.invoke(controller, argument);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("handler '" + method.getName() + "' failed", e);
        }
    }

    /** Peels a reactive holder to its current value; reading tracks the dependency. */
    public static Object unwrap(Object value) {
        if (value instanceof Signal<?> signal) {
            return signal.get();
        }
        if (value instanceof Computed<?> computed) {
            return computed.get();
        }
        if (value instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        return value;
    }
}
