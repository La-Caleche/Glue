package fr.lacaleche.glue.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ModCompatManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/compat");

    public static boolean probeBoolean(String modId, String className, String methodName, boolean isPrivate) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) return false;

        Method method = MethodCache.resolve(className, methodName, isPrivate);
        if (method == null || method.getParameterCount() != 0 || method.getReturnType() != boolean.class) return false;

        try {
            Object result = method.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) throw err;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static Object getSingleton(String modId, String className, String getterName, boolean isPrivateGetter) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) return null;
        return SingletonCache.resolve(className, getterName, isPrivateGetter);
    }

    public static boolean probeInstanceBoolean(
            String modId, String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod) {
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return false;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod);
        if (m == null || m.getParameterCount() != 0 || m.getReturnType() != boolean.class) return false;

        try {
            Object r = m.invoke(inst);
            return r instanceof Boolean && (Boolean) r;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) throw err;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void invokeInstanceVoid(
            String modId, String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod,
            Class<?>[] paramTypes, Object... args) {
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod, paramTypes);
        if (m == null) return;

        try {
            m.invoke(inst, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) throw err;
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeInstance(
            String modId, String className,
            String getterName, boolean isPrivateGetter,
            String methodName, boolean isPrivateMethod,
            Class<?>[] paramTypes, Object[] args,
            Class<T> returnType, T fallback) {
        Objects.requireNonNull(returnType, "returnType");
        Object inst = getSingleton(modId, className, getterName, isPrivateGetter);
        if (inst == null) return fallback;

        Method m = InstanceMethodCache.resolve(className, methodName, isPrivateMethod, paramTypes);
        if (m == null) return fallback;

        try {
            Object r = m.invoke(inst, args);
            return returnType.isInstance(r) ? (T) r : fallback;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) throw err;
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeRuntime(Object inst, String methodName, boolean isPrivateMethod, Class<?>[] paramTypes, Object[] args, Class<T> returnType, T fallback) {
        if (inst == null) return fallback;
        Method m = InstanceMethodCache.resolve(inst.getClass().getName(), methodName, isPrivateMethod, paramTypes);
        if (m == null) return fallback;
        try {
            Object r = m.invoke(inst, args);
            return returnType.isInstance(r) ? (T) r : fallback;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) throw err;
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object inst, String className, String fieldName, boolean isPrivateField, Class<T> fieldType, T fallback) {
        if (inst == null) return fallback;
        java.lang.reflect.Field f = FieldCache.resolve(className, fieldName, isPrivateField);
        if (f == null) return fallback;
        try {
            Object r = f.get(inst);
            return fieldType.isInstance(r) ? (T) r : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void clearCache() {
        MethodCache.clear();
        SingletonCache.clear();
        InstanceMethodCache.clear();
        FieldCache.clear();
    }

    private static final class MethodCache {
        private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
        /**
         * {@code Optional.empty()} records a confirmed-missing lookup so we
         * don't redo {@code Class.forName} on every call.
         */
        private static final ConcurrentMap<String, java.util.Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

        static Method resolve(String className, String methodName, boolean isPrivate) {
            String methodKey = key(className, methodName, new Class<?>[0]);
            java.util.Optional<Method> cached = METHOD_CACHE.get(methodKey);
            if (cached != null) return cached.orElse(null);

            Class<?> clazz = CLASS_CACHE.computeIfAbsent(className, MethodCache::loadClassSafely);
            if (clazz == null) {
                METHOD_CACHE.putIfAbsent(methodKey, java.util.Optional.empty());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Glue] compat lookup miss: class {} not loadable (for method {})", className, methodName);
                }
                return null;
            }

            Method method = lookupMethod(clazz, methodName, isPrivate, new Class<?>[0]);
            if (method == null) {
                METHOD_CACHE.putIfAbsent(methodKey, java.util.Optional.empty());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Glue] compat lookup miss: method {}#{} not found", className, methodName);
                }
                return null;
            }

            METHOD_CACHE.putIfAbsent(methodKey, java.util.Optional.of(method));
            return method;
        }

        static Class<?> loadClassSafely(String className) {
            try {
                return Class.forName(className, false, ModCompatManager.class.getClassLoader());
            } catch (Throwable ignored) {
                return null;
            }
        }

        static Method lookupMethod(Class<?> clazz, String methodName, boolean isPrivate, Class<?>[] paramTypes) {
            try {
                Method m = isPrivate
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

    private static final class SingletonCache {
        /**
         * Distinguishes "getter returned null" from "not yet resolved".
         */
        private static final Object NULL_SENTINEL = new Object();
        private static final ConcurrentMap<String, Object> SINGLETONS = new ConcurrentHashMap<>();

        static Object resolve(String className, String getterName, boolean isPrivateGetter) {
            String key = className + "#" + getterName;
            Object cached = SINGLETONS.get(key);
            if (cached == NULL_SENTINEL) return null;
            if (cached != null) return cached;

            Class<?> clazz = MethodCache.loadClassSafely(className);
            if (clazz == null) return null;

            Method getter = MethodCache.lookupMethod(clazz, getterName, isPrivateGetter, new Class<?>[0]);
            if (getter == null) return null;

            try {
                Object obj = getter.invoke(null);
                SINGLETONS.putIfAbsent(key, obj != null ? obj : NULL_SENTINEL);
                Object stored = SINGLETONS.get(key);
                return stored == NULL_SENTINEL ? null : stored;
            } catch (Throwable ignored) {
                return null;
            }
        }

        static void clear() {
            SINGLETONS.clear();
        }
    }

    private static final class InstanceMethodCache {
        private static final ConcurrentMap<String, java.util.Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

        static Method resolve(String className, String methodName, boolean isPrivate, Class<?>... params) {
            String key = className + "#" + methodName + Arrays.toString(params);
            java.util.Optional<Method> cached = METHOD_CACHE.get(key);
            if (cached != null) return cached.orElse(null);

            Class<?> clazz = MethodCache.loadClassSafely(className);
            if (clazz == null) {
                METHOD_CACHE.putIfAbsent(key, java.util.Optional.empty());
                return null;
            }

            Method found = MethodCache.lookupMethod(clazz, methodName, isPrivate, params);
            if (found == null) {
                METHOD_CACHE.putIfAbsent(key, java.util.Optional.empty());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Glue] compat lookup miss: instance method {}#{} not found", className, methodName);
                }
                return null;
            }

            METHOD_CACHE.putIfAbsent(key, java.util.Optional.of(found));
            return found;
        }

        static void clear() {
            METHOD_CACHE.clear();
        }
    }

    private static final class FieldCache {
        private static final ConcurrentMap<String, java.util.Optional<java.lang.reflect.Field>> FIELD_CACHE = new ConcurrentHashMap<>();

        static java.lang.reflect.Field resolve(String className, String fieldName, boolean isPrivate) {
            String key = className + "#" + fieldName;
            java.util.Optional<java.lang.reflect.Field> cached = FIELD_CACHE.get(key);
            if (cached != null) return cached.orElse(null);

            Class<?> clazz = MethodCache.loadClassSafely(className);
            if (clazz == null) {
                FIELD_CACHE.putIfAbsent(key, java.util.Optional.empty());
                return null;
            }

            java.lang.reflect.Field found = lookupField(clazz, fieldName, isPrivate);
            if (found == null) {
                FIELD_CACHE.putIfAbsent(key, java.util.Optional.empty());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Glue] compat lookup miss: field {}#{} not found", className, fieldName);
                }
                return null;
            }

            FIELD_CACHE.putIfAbsent(key, java.util.Optional.of(found));
            return found;
        }

        static java.lang.reflect.Field lookupField(Class<?> clazz, String fieldName, boolean isPrivate) {
            try {
                java.lang.reflect.Field f = isPrivate ? clazz.getDeclaredField(fieldName) : clazz.getField(fieldName);
                if (isPrivate) {
                    try {
                        f.setAccessible(true);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
                return f;
            } catch (Throwable ignored) {
                return null;
            }
        }

        static void clear() {
            FIELD_CACHE.clear();
        }
    }
}
