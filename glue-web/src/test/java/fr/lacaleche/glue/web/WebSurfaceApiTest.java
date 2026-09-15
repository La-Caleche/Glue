package fr.lacaleche.glue.web;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSurfaceApiTest {

    @Test
    void rejectsInvalidOptionsBeforeOpeningOrLoadingNativeCode() {
        assertThrows(IllegalArgumentException.class, () -> WebSurface.builder(URI.create("relative")));
        WebSurface.Builder builder = WebSurface.builder(URI.create("about:blank"));
        assertThrows(IllegalArgumentException.class, () -> builder.size(0, 100));
        assertThrows(IllegalArgumentException.class, () -> builder.size(100, 4097));
        assertThrows(IllegalArgumentException.class, () -> builder.frameRate(0));
        assertThrows(IllegalArgumentException.class, () -> builder.frameRate(121));
        assertThrows(IllegalArgumentException.class, () -> builder.onMessage(URI.create("file:///page"), message -> {}));
    }

    @Test
    void publicSignaturesDoNotExposeTheBrowserEngineOrInternalTypes() {
        for (Class<?> type : List.of(WebSurface.class, WebSurface.Builder.class, WebMetrics.class,
                WebCursor.class, WebPointerEvent.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                assertPublicType(method.getGenericReturnType());
                for (Type parameter : method.getGenericParameterTypes()) assertPublicType(parameter);
            }
        }
    }

    private static void assertPublicType(Type type) {
        String name = type.getTypeName();
        assertFalse(name.contains("org.cef") || name.contains("jcefgithub") || name.contains(".internal."), name);
    }
}
