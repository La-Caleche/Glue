package fr.lacaleche.glue.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utilities for lightweight, reflection-based interoperability with optional mods.
 * <p>
 * Design goals:
 * <ul>
 *   <li>No hard runtime dependency on target mods (everything is reflective).</li>
 *   <li>Zero-throw: all failures return safe fallbacks (false/null).</li>
 *   <li>Memoized class/method/instance lookups for cheap hot-path calls.</li>
 *   <li>Thread-safe caches with small, stable string keys.</li>
 * </ul>
 *
 * <h3>Common patterns supported</h3>
 * <ul>
 *   <li><b>Static boolean flags</b> (e.g., {@code isEnabled()}): {@link #probeBoolean(String, String, String, boolean)}</li>
 *   <li><b>Singleton instance + instance boolean no-args</b> (e.g., {@code getInstance().isActive()}):
 *       {@link #probeInstanceBoolean(String, String, String, boolean, String, boolean)}</li>
 *   <li><b>Singleton instance + instance void with args</b> (e.g., {@code getInstance().mouseMoved(x, y)}):
 *       {@link #invokeInstanceVoid(String, String, String, boolean, String, boolean, Class[], Object...)}</li>
 *   <li><b>Fully generic</b> invoke with a typed return and fallback:
 *       {@link #invokeInstance(String, String, String, boolean, String, boolean, Class[], Object[], Class, Object)}</li>
 * </ul>
 *
 * <p><b>All methods first check {@link FabricLoader#isModLoaded(String)} and short-circuit if the mod is absent.</b></p>
 */
public class ModCompatManager {

    /**
     * Invoke a <b>static</b> {@code boolean} method with no parameters on a target class.
     *
     * @param modId      the Fabric mod id; if not loaded, returns {@code false}
     * @param className  fully-qualified class name containing the static method
     * @param methodName static method name
     * @param isPrivate  true if the method is private (accessible via setAccessible), false if public
     * @return the boolean result, or {@code false} on any failure/mod not present
     */
    public static boolean probeBoolean(String modId, String className, String methodName, boolean isPrivate) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) return false;

        Method method = MethodCache.resolve(className, methodName, isPrivate);
        if (method == null || method.getParameterCount() != 0 || method.getReturnType() != boolean.class) return false;

        try {
            Object result = method.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Resolve a singleton instance via a <b>static</b> no-args getter (e.g. {@code getInstance()}).
     *
     * @param modId           the Fabric mod id; if not loaded, returns {@code null}
     * @param className       fully-qualified class name
     * @param getterName      static getter method name returning the singleton instance
     * @param isPrivateGetter true if the getter is private
     * @return the singleton instance or {@code null} if anything fails
     */
    public static Object getSingleton(String modId, String className, String getterName, boolean isPrivateGetter) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) return null;
        return SingletonCache.resolve(className, getterName, isPrivateGetter);
    }

    /**
     * Call an <b>instance</b> {@code boolean} method with no parameters on a singleton, returning {@code false} on failure.
     *
     * @param modId           the Fabric mod id
     * @param className       fully-qualified class that owns both the singleton getter and the instance method
     * @param getterName      static singleton getter name
     * @param isPrivateGetter true if the getter is private
     * @param methodName      instance method name
     * @param isPrivateMethod true if the instance method is private
     * @return boolean result, or {@code false} on any failure/mod not present
     */
    public static boolean probeInstanceBoolean(
            String modId,
            String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod
    ) {
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return false;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod /* no params */);
        if (m == null || m.getParameterCount() != 0 || m.getReturnType() != boolean.class) return false;

        try {
            Object r = m.invoke(inst);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Invoke an <b>instance void</b> method with parameters on a singleton (no-throw).
     *
     * @param modId           the Fabric mod id
     * @param className       fully-qualified class that owns both the singleton getter and the instance method
     * @param getterName      static singleton getter name
     * @param isPrivateGetter true if the getter is private
     * @param methodName      instance method name
     * @param isPrivateMethod true if the method is private
     * @param paramTypes      exact parameter types (use primitives where appropriate)
     * @param args            arguments to pass
     */
    public static void invokeInstanceVoid(
            String modId,
            String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod,
            Class<?>[] paramTypes, Object... args
    ) {
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod, paramTypes);
        if (m == null) return;

        try {
            m.invoke(inst, args);
        } catch (Throwable ignored) {
            // Swallow by design
        }
    }

    /**
     * Fully-generic instance invocation on a singleton with a typed return and fallback.
     *
     * @param modId           the Fabric mod id
     * @param className       fully-qualified class that owns both the singleton getter and the instance method
     * @param getterName      static singleton getter name
     * @param isPrivateGetter true if the getter is private
     * @param methodName      instance method name
     * @param isPrivateMethod true if the method is private
     * @param paramTypes      exact parameter types (use primitives where appropriate)
     * @param args            arguments to pass
     * @param returnType      expected return type
     * @param fallback        value returned on any failure
     * @param <T>             return type
     * @return the typed result or {@code fallback} on error
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeInstance(
            String modId,
            String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod,
            Class<?>[] paramTypes, Object[] args,
            Class<T> returnType, T fallback
    ) {
        Objects.requireNonNull(returnType, "returnType");
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return fallback;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod, paramTypes);
        if (m == null) return fallback;

        try {
            Object r = m.invoke(inst, args);
            return returnType.isInstance(r) ? (T) r : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * Clears all internal caches (classes, methods, singleton instances).
     * <p>Call this when classloaders change (e.g., after a resource/config hot-reload) and you need fresh reflection.</p>
     */
    public static void clearCache() {
        MethodCache.clear();
        SingletonCache.clear();
        InstanceMethodCache.clear();
    }

    /**
     * Cache for <b>static</b> no-arg methods (used by {@link #probeBoolean}).
     * <p>Also holds a small class cache to avoid repeated class lookups.</p>
     */
    private static final class MethodCache {
        private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
        private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

        static Method resolve(String className, String methodName, boolean isPrivate) {
            String methodKey = key(className, methodName, new Class<?>[0]);

            Method cached = METHOD_CACHE.get(methodKey);
            if (cached != null) return cached;

            Class<?> clazz = CLASS_CACHE.computeIfAbsent(className, MethodCache::loadClassSafely);
            if (clazz == null) return null;

            Method method = lookupMethod(clazz, methodName, isPrivate, new Class<?>[0]);
            if (method == null) return null;

            METHOD_CACHE.putIfAbsent(methodKey, method);
            return METHOD_CACHE.get(methodKey);
        }

        private static Class<?> loadClassSafely(String className) {
            try {
                return Class.forName(className, false, ModCompatManager.class.getClassLoader());
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method lookupMethod(Class<?> clazz, String methodName, boolean isPrivate, Class<?>[] paramTypes) {
            try {
                final Method m = isPrivate
                        ? clazz.getDeclaredMethod(methodName, paramTypes)
                        : clazz.getMethod(methodName, paramTypes);

                if (isPrivate && !m.canAccess(null)) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static String key(String className, String methodName, Class<?>[] params) {
            return className + "#" + methodName + Arrays.toString(params);
        }

        static void clear() {
            CLASS_CACHE.clear();
            METHOD_CACHE.clear();
        }
    }

    /**
     * Cache for <b>singleton instances</b> obtained from a static no-args getter
     * (e.g., {@code SomeManager.getInstance()}).
     */
    private static final class SingletonCache {
        private static final ConcurrentMap<String, Object> SINGLETONS = new ConcurrentHashMap<>();

        static Object resolve(String className, String getterName, boolean isPrivateGetter) {
            String key = className + "#" + getterName;

            Object inst = SINGLETONS.get(key);
            if (inst != null) return inst;

            Class<?> clazz = MethodCache.loadClassSafely(className);
            if (clazz == null) return null;

            Method getter = MethodCache.lookupMethod(clazz, getterName, isPrivateGetter, new Class<?>[0]);
            if (getter == null) return null;

            try {
                Object obj = getter.invoke(null);
                if (obj != null) {
                    SINGLETONS.putIfAbsent(key, obj);
                    return SINGLETONS.get(key);
                }
            } catch (Throwable ignored) {
                // fallthrough
            }
            return null;
        }

        static void clear() {
            SINGLETONS.clear();
        }
    }

    /**
     * Cache for <b>instance</b> methods (with an arbitrary parameter signature).
     * <p>Used for both {@link #probeInstanceBoolean} and the generic invoke helpers.</p>
     */
    private static final class InstanceMethodCache {
        private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

        static Method resolve(String className, String methodName, boolean isPrivate, Class<?>... params) {
            String key = className + "#" + methodName + Arrays.toString(params);

            Method cached = METHOD_CACHE.get(key);
            if (cached != null) return cached;

            Class<?> clazz = MethodCache.loadClassSafely(className);
            if (clazz == null) return null;

            Method found = MethodCache.lookupMethod(clazz, methodName, isPrivate, params);
            if (found == null) return null;

            METHOD_CACHE.putIfAbsent(key, found);
            return METHOD_CACHE.get(key);
        }

        static void clear() {
            METHOD_CACHE.clear();
        }
    }
}
