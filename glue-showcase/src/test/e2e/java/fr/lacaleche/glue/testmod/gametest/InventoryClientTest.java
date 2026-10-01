package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.gametest.ClientTest;
import fr.lacaleche.glue.gametest.ContainerTest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Real inventory input: client prediction, server synchronization and screenshots. */
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Fabric discovers runTest, not JUnit annotations.
public final class InventoryClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientTest game = new ClientTest(context);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                player.inventoryMenu.broadcastChanges();
            });
            world.getClientWorld().waitForChunksRender();
            game.expect("survival mode synchronized", client -> {
                if (client.player == null || client.player.hasInfiniteMaterials()) {
                    throw new AssertionError("The client has not entered survival mode");
                }
            });

            int initialTicks = context.computeOnClient(client -> client.player.tickCount);
            game.expect("assertions advance the game until their condition holds", client -> {
                if (client.player.tickCount < initialTicks + 2) throw new AssertionError("Two player ticks have not elapsed");
            });

            ContainerTest inventory = game.openInventory();
            inventory.playerSlot(0).expectItem(Items.DIAMOND, 3);
            inventory.playerSlot(1).expectEmpty();
            inventory.playerSlot(0).click();
            inventory.playerSlot(0).expectEmpty();
            inventory.expectCarried(Items.DIAMOND, 3);
            context.takeScreenshot("inventory-picked-up");

            inventory.playerSlot(1).rightClick();
            inventory.playerSlot(1).expectItem(Items.DIAMOND, 1);
            inventory.expectCarried(Items.DIAMOND, 2);
            inventory.playerSlot(1).click();
            inventory.playerSlot(1).expectItem(Items.DIAMOND, 3);
            inventory.expectCarriedEmpty();
            context.takeScreenshot("inventory-moved");

            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ItemStack stack = player.getInventory().getItem(1);
                if (!player.getInventory().getItem(0).isEmpty() || !stack.is(Items.DIAMOND) || stack.getCount() != 3) {
                    throw new AssertionError("The server did not receive the inventory move");
                }
            });

            ContainerTest.SlotTarget destination = inventory.playerSlot(2);
            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
                client.resizeDisplay();
            });
            // Reuse the handle after a resize and a recipe-book layout shift.
            inventory.playerSlot(1).click();
            destination.click();
            destination.expectItem(Items.DIAMOND, 3);
            inventory.expectCarriedEmpty();
            context.takeScreenshot("inventory-resized-recipe-book");

            inventory.close();
            context.waitForScreen(null);
            try {
                destination.click();
            } catch (IllegalStateException expected) {
                return;
            }
            throw new AssertionError("A closed container handle must reject clicks");
        }
    }
}
