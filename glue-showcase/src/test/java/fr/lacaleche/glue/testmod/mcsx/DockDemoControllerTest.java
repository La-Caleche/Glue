package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.TailwindParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DockDemoControllerTest {

    @Test
    void dataDrivenDockClassesAreValid() throws ReflectiveOperationException {
        DockDemoController controller = new DockDemoController();

        for (DockDemoController.SceneRow row : sceneRows(controller)) {
            assertValid(row.classes());
            assertValid(row.color());
        }
        for (DockDemoController.LogLine line : logLines(controller)) {
            assertValid(line.color());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<DockDemoController.SceneRow> sceneRows(DockDemoController controller)
            throws ReflectiveOperationException {
        Field field = DockDemoController.class.getDeclaredField("sceneRows");
        field.setAccessible(true);
        return (List<DockDemoController.SceneRow>) field.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static List<DockDemoController.LogLine> logLines(DockDemoController controller)
            throws ReflectiveOperationException {
        Field field = DockDemoController.class.getDeclaredField("logLines");
        field.setAccessible(true);
        Signal<List<DockDemoController.LogLine>> signal =
                (Signal<List<DockDemoController.LogLine>>) field.get(controller);
        return signal.get();
    }

    private static void assertValid(String classes) {
        assertDoesNotThrow(() -> TailwindParser.parseStrict(classes), classes);
    }
}
