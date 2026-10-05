package fr.lacaleche.glue.client.debug;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.client.debug.internal.DeveloperPages;
import fr.lacaleche.glue.client.debug.internal.Framebuffers;
import fr.lacaleche.glue.client.debug.internal.FramebuffersPage;
import fr.lacaleche.glue.client.debug.internal.RaycastPage;
import fr.lacaleche.glue.client.ui.UiNumberField;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.client.ui.UiScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The developer menu: a {@link UiScreen} of the pages mods register, grouped by mod with Glue's first,
 * opened with F8 in a world or on the title screen. Glue registers its framebuffer viewer and raycast
 * overlay here. Pages register during client initialisation; the menu builds new pages each time it
 * opens. Client thread only.
 */
public final class DeveloperMenu {

    private static final KeyMapping KEY = new KeyMapping("key.glue.developer_menu", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8, "key.categories.glue");
    private static final DeveloperPages PAGES = new DeveloperPages();
    private static final String GLUE = "glue";

    private DeveloperMenu() {
    }

    /**
     * Adds a page to the menu, listed under the mod named by the id's namespace.
     *
     * @param needsWorld whether the page is unavailable while no world is loaded, as on the title screen
     * @param page creates the page each time the menu opens
     * @throws IllegalStateException once the client has started
     * @throws IllegalArgumentException if the id is already registered
     */
    public static void register(ResourceLocation id, boolean needsWorld, Supplier<UiPage> page) {
        PAGES.register(id, needsWorld, page);
    }

    /** Opens the menu over the current screen, which it returns to; this takes the framebuffers off the HUD. */
    public static void open() {
        Framebuffers.INSTANCE.setOnHud(false);
        Minecraft minecraft = Minecraft.getInstance();
        List<Map.Entry<String, List<DeveloperPages.Entry>>> mods = new ArrayList<>(PAGES.groups().entrySet());
        mods.sort(Comparator.comparing(mod -> !GLUE.equals(mod.getKey())));
        List<UiScreen.Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<DeveloperPages.Entry>> mod : mods) {
            groups.add(new UiScreen.Group(modName(mod.getKey()),
                    mod.getValue().stream().map(DeveloperMenu::page).toList()));
        }
        if (groups.isEmpty()) return;

        minecraft.setScreen(new MenuScreen(minecraft.screen, groups));
    }

    @ApiStatus.Internal
    public static void bootstrap() {
        KeyBindingHelper.registerKeyBinding(KEY);
        Framebuffers.INSTANCE.register();
        register(ResourceLocation.fromNamespaceAndPath(GLUE, "framebuffers"), true, FramebuffersPage::new);
        RaycastPage.register();
        register(ResourceLocation.fromNamespaceAndPath(GLUE, "raycast"), true, RaycastPage::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY.consumeClick()) {
                if (client.screen == null) open();
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen)) return;

            ScreenKeyboardEvents.afterKeyPress(screen).register((current, key, scancode, modifiers) -> {
                if (KEY.matches(key, scancode)) open();
            });
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> PAGES.close());
    }

    private static UiPage page(DeveloperPages.Entry entry) {
        UiPage page = entry.page().get();
        return entry.needsWorld() ? new WorldPage(page) : page;
    }

    private static Component modName(String namespace) {
        return Component.literal(FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .orElse(namespace));
    }

    private static final class MenuScreen extends UiScreen {

        private MenuScreen(@Nullable Screen parent, List<Group> groups) {
            super(Component.translatable("glue.developer_menu.title"), parent, groups);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scancode, int modifiers) {
            if (KEY.matches(key, scancode) && !typing(this.getFocused())) {
                this.onClose();
                return true;
            }
            return super.keyPressed(key, scancode, modifiers);
        }

        private static boolean typing(@Nullable GuiEventListener focused) {
            if (focused instanceof EditBox || focused instanceof UiNumberField field && field.isEditing()) return true;

            return focused instanceof ContainerEventHandler container && typing(container.getFocused());
        }
    }

    /** A page that needs a world, unavailable while none is loaded. */
    private record WorldPage(UiPage page) implements UiPage {

        @Override
        public Component title() {
            return this.page.title();
        }

        @Override
        public void build(UiPageBuilder builder) {
            this.page.build(builder);
        }

        @Override
        public void close() {
            this.page.close();
        }

        @Override
        public @Nullable Component unavailableReason() {
            if (Minecraft.getInstance().level == null) return Component.translatable("glue.developer_menu.needs_world");

            return this.page.unavailableReason();
        }
    }
}
