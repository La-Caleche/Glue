package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.Ui;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DockPaneFactoriesTest {

    private final Context context = new HeadlessContext();

    @BeforeAll
    static void initializeModernUi() {
        if (ModernUI.getInstance() == null) new ModernUI();
    }

    @Test
    void uiAdapterSuppliesScopedUiAndDefaultsDisposalToNoOp() {
        AtomicReference<Ui> suppliedUi = new AtomicReference<>();
        DockContent content = DockContent.ui(ui -> {
            suppliedUi.set(ui);
            return ui.column();
        });

        View view = content.create(this.context);
        content.dispose(view);

        assertInstanceOf(fr.lacaleche.glue.mcsx.client.component.Column.class, view);
        assertSame(this.context, view.getContext());
        assertNotNull(suppliedUi.get());
    }

    @Test
    void conciseFactoriesKeepExplicitComponentTitleSemantics() {
        DockPane literal = DockPane.literal("literal", "gui.done", Ui::column);
        DockPane translatable = DockPane.translatable("translated", "gui.done", Ui::column);

        assertEquals(Component.literal("gui.done"), literal.title());
        assertEquals(Component.translatable("gui.done"), translatable.title());
    }

    @Test
    void uiBuilderConfiguresContentAndTrailingHeaderWithoutLambdaAmbiguity() {
        DockPane pane = DockPane.builderTranslatable("tools", "example.tools", ui -> ui.column())
                .trailingHeaderUi(ui -> ui.row())
                .build();

        assertSame(this.context, pane.content().create(this.context).getContext());
        assertSame(this.context, pane.trailingHeader().create(this.context).getContext());
    }

    private static final class HeadlessContext extends Context {

        private final Resources resources = Resources.getSystem();
        private final Resources.Theme theme = this.resources.newTheme();

        @Override
        public Resources getResources() {
            return this.resources;
        }

        @Override
        public void setTheme(ResourceId resourceId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return this.theme;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }
}
