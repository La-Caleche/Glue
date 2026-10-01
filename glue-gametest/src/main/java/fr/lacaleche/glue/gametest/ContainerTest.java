package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * A handle to one open container screen. All operations run from Fabric's test thread and access
 * the screen only through its client-thread bridge. A replaced or closed screen invalidates the
 * handle, including its slot targets. Coordinates are resolved afresh after layout or scale changes.
 */
@Environment(EnvType.CLIENT)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Test support, not a JUnit test class.
public final class ContainerTest {

    private final ClientTest test;
    private final AbstractContainerScreen<?> screen;

    ContainerTest(ClientTest test, AbstractContainerScreen<?> screen) {
        this.test = test;
        this.screen = screen;
    }

    /** Targets a menu slot index, including crafting/armor slots where present. */
    public SlotTarget slot(int menuIndex) {
        this.test.context().runOnClient(client -> this.requireSlot(client, menuIndex));
        return new SlotTarget(menuIndex);
    }

    /** Targets a player inventory index: 0–8 are the hotbar, 9–35 the main inventory. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // The slot must belong to this exact player's inventory.
    public SlotTarget playerSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            throw new IllegalArgumentException("Player inventory index must be between 0 and 35");
        }
        int menuIndex = this.test.context().computeOnClient(client -> {
            this.requireCurrent(client);
            if (client.player == null) throw new IllegalStateException("No player");
            for (Slot slot : this.screen.getMenu().slots) {
                if (slot.container == client.player.getInventory() && slot.getContainerSlot() == inventoryIndex) {
                    return slot.index;
                }
            }
            throw new IllegalStateException("No player inventory slot " + inventoryIndex + " in this screen");
        });
        return this.slot(menuIndex);
    }

    /** Waits for the cursor stack's item and exact count; components are not compared. */
    public void expectCarried(Item item, int count) {
        validateItemCount(item, count);
        this.test.expect("carried stack", client -> {
            this.requireCurrent(client);
            assertItem(this.screen.getMenu().getCarried(), item, count);
        });
    }

    public void expectCarriedEmpty() {
        this.test.expect("empty carried stack", client -> {
            this.requireCurrent(client);
            assertEmpty(this.screen.getMenu().getCarried());
        });
    }

    /** Sends Escape and waits for this screen to close. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // Screen lifetime is identity-based.
    public void close() {
        this.test.context().runOnClient(this::requireCurrent);
        this.test.context().getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.test.context().waitFor(client -> client.screen != this.screen);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // A replacement screen must invalidate this handle.
    private void requireCurrent(Minecraft client) {
        if (client.screen != this.screen) throw new IllegalStateException("The container screen is no longer open");
    }

    private Slot requireSlot(Minecraft client, int menuIndex) {
        this.requireCurrent(client);
        if (menuIndex < 0 || menuIndex >= this.screen.getMenu().slots.size()) {
            throw new IllegalArgumentException("Invalid menu slot index: " + menuIndex);
        }
        return this.screen.getMenu().getSlot(menuIndex);
    }

    private static void validateItemCount(Item item, int count) {
        Objects.requireNonNull(item, "item");
        if (count <= 0) throw new IllegalArgumentException("count must be positive; use expectEmpty for empty stacks");
    }

    private static void assertItem(ItemStack stack, Item item, int count) {
        if (!stack.is(item) || stack.getCount() != count) {
            throw new AssertionError("Expected " + count + " x " + item + ", got " + stack);
        }
    }

    private static void assertEmpty(ItemStack stack) {
        if (!stack.isEmpty()) throw new AssertionError("Expected an empty stack, got " + stack);
    }

    /** A slot in this container; clicks use Fabric's mouse input, never direct menu mutations. */
    public final class SlotTarget {

        private final int menuIndex;

        private SlotTarget(int menuIndex) {
            this.menuIndex = menuIndex;
        }

        public void click() {
            this.click(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }

        public void rightClick() {
            this.click(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }

        /** Waits for the item and exact count; components are not compared. */
        public void expectItem(Item item, int count) {
            validateItemCount(item, count);
            test.expect("menu slot " + this.menuIndex, client ->
                    assertItem(requireSlot(client, this.menuIndex).getItem(), item, count));
        }

        public void expectEmpty() {
            test.expect("empty menu slot " + this.menuIndex, client ->
                    assertEmpty(requireSlot(client, this.menuIndex).getItem()));
        }

        private void click(int button) {
            CursorPosition position = test.context().computeOnClient(client -> {
                Slot slot = requireSlot(client, this.menuIndex);
                if (!slot.isActive()) throw new IllegalStateException("Menu slot " + this.menuIndex + " is inactive");
                double x = screen.leftPos + slot.x + 8.0;
                double y = screen.topPos + slot.y + 8.0;
                if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
                    throw new IllegalStateException("Menu slot " + this.menuIndex + " is outside the screen");
                }
                // GLFW cursor coordinates are window units, not framebuffer pixels (HiDPI matters).
                return new CursorPosition(x * client.getWindow().getScreenWidth() / screen.width,
                        y * client.getWindow().getScreenHeight() / screen.height);
            });
            // The first cursor callback after a grab change can be discarded by Minecraft.
            test.context().getInput().setCursorPos(position.x + 1, position.y);
            test.context().getInput().setCursorPos(position.x, position.y);
            test.context().getInput().pressMouse(button);
        }
    }

    private record CursorPosition(double x, double y) {
    }
}
