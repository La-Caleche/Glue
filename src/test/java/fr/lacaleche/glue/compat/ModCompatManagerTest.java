package fr.lacaleche.glue.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModCompatManagerTest {

    @BeforeEach
    void clearCacheBetweenTests() {
        ModCompatManager.clearCache();
    }

    @Test
    public void invokeRuntime_nullInstance_returnsFallback() {
        String result = ModCompatManager.invokeRuntime(
                null, "toString", false, new Class<?>[0], new Object[0], String.class, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    public void invokeRuntime_validNoArgMethod_returnsResult() {
        String result = ModCompatManager.invokeRuntime(
                "hello", "toString", false, new Class<?>[0], new Object[0], String.class, "fallback");
        assertEquals("hello", result);
    }

    @Test
    public void invokeRuntime_nonexistentMethod_returnsFallback() {
        String result = ModCompatManager.invokeRuntime(
                "hello", "noSuchMethod", false, new Class<?>[0], new Object[0], String.class, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    public void invokeRuntime_wrongExpectedReturnType_returnsFallback() {
        Integer result = ModCompatManager.invokeRuntime(
                "hello", "toString", false, new Class<?>[0], new Object[0], Integer.class, -1);
        assertEquals(-1, result);
    }

    @Test
    public void invokeRuntime_methodWithArgs_returnsComputedResult() {
        Fixture f = new Fixture("test");
        Integer result = ModCompatManager.invokeRuntime(
                f, "square", false, new Class<?>[]{int.class}, new Object[]{7}, Integer.class, -1);
        assertEquals(49, result);
    }

    @Test
    public void invokeRuntime_validNoArgMethodOnFixture_returnsResult() {
        Fixture f = new Fixture("world");
        String result = ModCompatManager.invokeRuntime(
                f, "greet", false, new Class<?>[0], new Object[0], String.class, "fallback");
        assertEquals("hello from world", result);
    }

    @Test
    public void getFieldValue_nullInstance_returnsFallback() {
        String result = ModCompatManager.getFieldValue(
                null, Fixture.class.getName(), "label", false, String.class, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    public void getFieldValue_publicField_returnsValue() {
        Fixture f = new Fixture("world");
        String result = ModCompatManager.getFieldValue(
                f, Fixture.class.getName(), "label", false, String.class, "fallback");
        assertEquals("world", result);
    }

    @Test
    public void getFieldValue_privateField_returnsValue() {
        Fixture f = new Fixture("x");
        Integer result = ModCompatManager.getFieldValue(
                f, Fixture.class.getName(), "secret", true, Integer.class, -1);
        assertEquals(42, result);
    }

    @Test
    public void getFieldValue_nonexistentField_returnsFallback() {
        Fixture f = new Fixture("x");
        String result = ModCompatManager.getFieldValue(
                f, Fixture.class.getName(), "noSuchField", false, String.class, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    public void getFieldValue_wrongExpectedFieldType_returnsFallback() {
        Fixture f = new Fixture("hello");
        Integer result = ModCompatManager.getFieldValue(
                f, Fixture.class.getName(), "label", false, Integer.class, -1);
        assertEquals(-1, result);
    }

    @Test
    public void clearCache_always_doesNotThrow() {
        assertDoesNotThrow(ModCompatManager::clearCache);
    }

    @Test
    public void clearCache_afterCachePrimed_subsequentLookupStillWorks() {
        Fixture f = new Fixture("test");
        ModCompatManager.invokeRuntime(
                f, "greet", false, new Class<?>[0], new Object[0], String.class, "fallback");
        ModCompatManager.clearCache();
        String result = ModCompatManager.invokeRuntime(
                f, "greet", false, new Class<?>[0], new Object[0], String.class, "fallback");
        assertEquals("hello from test", result);
    }

    @SuppressWarnings("unused")
    static class Fixture {
        private final int secret = 42;
        public String label;

        Fixture(String label) {
            this.label = label;
        }

        public String greet() {
            return "hello from " + label;
        }

        public int square(int n) {
            return n * n;
        }
    }
}
