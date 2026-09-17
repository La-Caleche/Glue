package fr.lacaleche.glue.web.internal.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.WebSurface;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Validated {@link WebAction} methods, invoked with a JSON payload on the client thread. */
public final class ActionTable {

    public static final ActionTable EMPTY = new ActionTable(Map.of());
    static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    private final Map<String, Action> actions;

    private ActionTable(Map<String, Action> actions) {
        this.actions = actions;
    }

    /** Fails fast on a handler without actions, a duplicate name or an unsupported signature. */
    public static ActionTable compile(List<Object> handlers) {
        if (handlers.isEmpty()) return EMPTY;

        Map<String, Action> actions = new LinkedHashMap<>();
        for (Object handler : handlers) {
            int declared = 0;
            for (Class<?> type = handler.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    WebAction annotation = method.getAnnotation(WebAction.class);
                    if (annotation == null || method.isBridge() || method.isSynthetic()) continue;

                    Action action = Action.of(handler, method, annotation.value());
                    if (actions.putIfAbsent(action.name(), action) != null) {
                        throw new IllegalArgumentException("Web action is declared twice: " + action.name());
                    }
                    declared++;
                }
            }
            if (declared == 0) {
                throw new IllegalArgumentException(handler.getClass().getName() + " declares no @WebAction method");
            }
        }
        return new ActionTable(Collections.unmodifiableMap(actions));
    }

    public boolean isEmpty() {
        return this.actions.isEmpty();
    }

    public Set<String> names() {
        return this.actions.keySet();
    }

    /** Never throws: unknown actions, decoding errors and handler exceptions complete exceptionally. */
    CompletableFuture<JsonElement> invoke(String name, JsonElement payload, WebSurface surface) {
        Action action = this.actions.get(name);
        if (action == null) return CompletableFuture.failedFuture(new UnknownActionException(name));

        try {
            Object result = action.invoke(payload, surface);
            if (result instanceof CompletionStage<?> stage) return stage.toCompletableFuture().thenApply(ActionTable::json);
            return CompletableFuture.completedFuture(json(result));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    static JsonElement json(Object value) {
        return value == null ? JsonNull.INSTANCE : Bridge.GSON.toJsonTree(value);
    }

    static final class UnknownActionException extends RuntimeException {

        UnknownActionException(String name) {
            super("Unknown web action: " + name);
        }
    }

    private record Action(String name, MethodHandle handle, Type payload, int payloadIndex, int surfaceIndex,
                          int arity) {

        static Action of(Object handler, Method method, String declaredName) {
            String name = declaredName.isEmpty() ? method.getName() : declaredName;
            if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid web action name: " + name);

            Type payload = null;
            int payloadIndex = -1;
            int surfaceIndex = -1;
            Class<?>[] parameters = method.getParameterTypes();
            for (int index = 0; index < parameters.length; index++) {
                if (parameters[index] == WebSurface.class) {
                    if (surfaceIndex >= 0) throw new IllegalArgumentException("Web action " + name + " declares two surfaces");
                    surfaceIndex = index;
                } else {
                    if (payloadIndex >= 0) {
                        throw new IllegalArgumentException("Web action " + name + " accepts at most one payload parameter");
                    }
                    payloadIndex = index;
                    payload = method.getGenericParameterTypes()[index];
                }
            }

            try {
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                if (!Modifier.isStatic(method.getModifiers())) handle = handle.bindTo(handler);
                return new Action(name, handle, payload, payloadIndex, surfaceIndex, parameters.length);
            } catch (IllegalAccessException | RuntimeException exception) {
                throw new IllegalArgumentException("Web action " + name + " is not accessible", exception);
            }
        }

        Object invoke(JsonElement json, WebSurface surface) throws Throwable {
            Object[] arguments = new Object[this.arity];
            if (this.payloadIndex >= 0) arguments[this.payloadIndex] = this.decode(json);
            if (this.surfaceIndex >= 0) arguments[this.surfaceIndex] = surface;
            return this.handle.invokeWithArguments(arguments);
        }

        private Object decode(JsonElement json) {
            Object value;
            try {
                value = Bridge.GSON.fromJson(json == null ? JsonNull.INSTANCE : json, this.payload);
            } catch (JsonParseException exception) {
                throw new IllegalArgumentException("Invalid payload for web action " + this.name, exception);
            }
            if (value == null && this.payload instanceof Class<?> type && type.isPrimitive()) {
                throw new IllegalArgumentException("Web action " + this.name + " requires a payload");
            }
            return value;
        }
    }
}
