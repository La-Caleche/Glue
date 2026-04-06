package fr.lacaleche.glue.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ModCompatManager {

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
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {}
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
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void clearCache() {
        MethodCache.clear();
        SingletonCache.clear();
        InstanceMethodCache.clear();
    }

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
                    try { m.setAccessible(true); }
                    catch (Throwable ignored) { return null; }
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
            } catch (Throwable ignored) {}
            return null;
        }

        static void clear() {
            SINGLETONS.clear();
        }
    }

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
