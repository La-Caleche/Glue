package fr.lacaleche.glue.web;

import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.app.WebAppStatus;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.bridge.WebSlotRenderer;
import fr.lacaleche.glue.web.host.WebAnchor;
import fr.lacaleche.glue.web.host.WebHud;
import fr.lacaleche.glue.web.host.WebOverlay;
import fr.lacaleche.glue.web.host.WebScreen;
import fr.lacaleche.glue.web.host.WebWidget;
import fr.lacaleche.glue.web.internal.browser.SurfaceOptions;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSurfaceApiTest {

    private static final WebSlotRenderer NO_DRAWING = (graphics, slot) -> { };

    @Test
    void rejectsInvalidOptionsBeforeOpeningOrLoadingNativeCode() {
        assertThrows(IllegalArgumentException.class, () -> WebSurface.builder(URI.create("relative")));
        WebSurface.Builder builder = WebSurface.builder(URI.create("about:blank"));
        assertThrows(IllegalArgumentException.class, () -> builder.size(0, 100));
        assertThrows(IllegalArgumentException.class, () -> builder.size(100, 4097));
        assertThrows(IllegalArgumentException.class, () -> builder.scale(0));
        assertThrows(IllegalArgumentException.class, () -> builder.scale(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.frameRate(0));
        assertThrows(IllegalArgumentException.class, () -> builder.frameRate(121));
        assertThrows(IllegalArgumentException.class, () -> builder.trust(URI.create("file:///page")));
    }

    @Test
    void rejectsInvalidBridgeDeclarations() {
        WebSurface.Builder builder = WebSurface.builder(URI.create("https://demo.glue/"));
        assertThrows(IllegalArgumentException.class, () -> builder.bind(new Object()));
        assertThrows(IllegalArgumentException.class, () -> builder.state("bad key", () -> 1));
        assertThrows(NullPointerException.class, () -> builder.state("health", (Supplier<?>) null));
        builder.state("health", () -> 20);
        assertThrows(IllegalArgumentException.class, () -> builder.state("health", () -> 20));
        assertThrows(IllegalArgumentException.class, () -> builder.slot("item:0", NO_DRAWING));
        builder.slot("item", NO_DRAWING);
        assertThrows(IllegalArgumentException.class, () -> builder.slotBehind("item", NO_DRAWING));
        builder.bind(new Actions());
        assertThrows(IllegalArgumentException.class, () -> builder.bind(new Actions()));
        builder.withoutBridge();
        assertThrows(IllegalStateException.class, builder::open);
    }

    @Test
    void offsetsRequireAnAnchor() {
        WebOverlay.Builder overlay = WebOverlay.builder(URI.create("https://demo.glue/"));
        assertThrows(IllegalStateException.class, () -> overlay.offset(4, 4));
        assertThrows(IllegalArgumentException.class, () -> overlay.anchor(WebAnchor.TOP, 0, 10));
        overlay.anchor(WebAnchor.TOP, 10, 10).offset(4, 4);
    }

    @Test
    void publicSignaturesDoNotExposeTheBrowserEngineOrInternalTypes() {
        for (Class<?> type : List.of(WebSurface.class, WebSurface.Builder.class, WebBuilder.class, WebApp.class,
                WebApp.Builder.class, WebAppStatus.class, WebAppStatus.Release.class,
                WebScreen.class, WebScreen.Builder.class, WebHud.class, WebHud.Builder.class, WebOverlay.class,
                WebOverlay.Builder.class, WebWidget.class, WebWidget.Builder.class, WebSlot.class,
                WebSlotRenderer.class, WebAnchor.class, WebMetrics.class, WebCursor.class, WebPointerEvent.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                assertPublicType(method.getGenericReturnType());
                for (Type parameter : method.getGenericParameterTypes()) assertPublicType(parameter);
            }
        }
    }

    @Test
    void hostSurfaceOptionsAreSnapshotsOfTheirBuilder() {
        WebSurface.Builder builder = WebSurface.builder(URI.create("https://demo.glue/"))
                .frameRate(30).state("health", () -> 20).slot("item", NO_DRAWING).bind(new Actions());
        WebSurface.Builder snapshot = builder.surfaceBuilder();
        builder.frameRate(60).state("armor", () -> 5).slotBehind("map", NO_DRAWING).withoutBridge();

        SurfaceOptions options = snapshot.options(100, 80, 2);
        assertEquals(30, options.frameRate());
        assertEquals(List.of("health"), List.copyOf(options.bridge().states().keySet()));
        assertEquals(List.of("item"), List.copyOf(options.bridge().slots().keySet()));
        assertEquals("demo.glue", options.bridge().origin().host());
        assertFalse(options.bridge().actions().isEmpty());
        assertThrows(IllegalStateException.class, builder::surfaceBuilder);
    }

    private static void assertPublicType(Type type) {
        String name = type.getTypeName();
        assertFalse(name.contains("org.cef") || name.contains("jcefgithub") || name.contains(".internal."), name);
    }

    static final class Actions {

        @WebAction
        void ping() {
        }
    }
}
