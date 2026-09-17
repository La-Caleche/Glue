# Mod Compatibility (Reflection)

`ModCompatManager` provides zero-throw, reflection-based interop with optional mods. All lookups are memoized in thread-safe caches.

## Static Boolean Probe

Check a static no-args boolean method on an optional mod:

```java
boolean isActive = ModCompatManager.probeBoolean(
        "othermod",                              // Fabric mod ID
        "com.other.ModManager",                  // Full class name
        "isActive",                              // Static method name
        false                                    // isPrivate
);
// Returns false if the mod isn't loaded or any reflection fails
```

## Singleton Instance + Boolean

For singleton patterns like `Manager.getInstance().isEnabled()`:

```java
boolean enabled = ModCompatManager.probeInstanceBoolean(
        "othermod",
        "com.other.Manager",
        "getInstance", false,    // Singleton getter
        "isEnabled", false       // Instance method
);
```

## Instance Void Invocation

Call a void method with arguments on a singleton:

```java
ModCompatManager.invokeInstanceVoid(
        "othermod",
        "com.other.InputManager",
        "getInstance", false,
        "mouseMoved", false,
        new Class<?>[]{ double.class, double.class },    // Param types
        mouseX, mouseY                                    // Arguments
);
```

## Generic Instance Invocation

Call a method with a typed return value:

```java
String name = ModCompatManager.invokeInstance(
        "othermod",
        "com.other.Manager",
        "getInstance", false,
        "getName", false,
        new Class<?>[0], new Object[0],
        String.class, "unknown"    // Return type + fallback
);
```

## Cache Management

```java
ModCompatManager.clearCache();  // Clear all reflection caches
```

## Design Principles

- **No hard dependencies:** Everything is reflective. If the target mod isn't loaded, calls return safe fallbacks.
- **Thread-safe:** All caches use `ConcurrentHashMap`.
- **Memoized:** Class, method, and singleton lookups are cached after first resolution.
- **Zero-throw:** All reflection failures are silently caught and return fallback values.
