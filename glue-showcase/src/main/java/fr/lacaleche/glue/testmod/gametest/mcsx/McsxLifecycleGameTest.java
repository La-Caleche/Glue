package fr.lacaleche.glue.testmod.gametest.mcsx;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.mcsx.client.GameFocus;
import fr.lacaleche.glue.mcsx.client.dock.DockContent;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.require;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireEquals;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.ui;

/**
 * Exercises the overlay mount ownership rules against the live client: a rejected second open that
 * must not poison anything, closing from the ModernUI thread, idempotent close, a stale game-focus
 * release that must not free the gameplay cursor, and a mount-stage failure that has to dispose its
 * partial views once and release the overlay slot for the next workspace.
 */
public final class McsxLifecycleGameTest {

    private McsxLifecycleGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:mcsx-lifecycle", McsxLifecycleGameTest::create);
    }

    private static GameTest create() {
        Dockspace first = plainWorkspace("lifecycle_first");
        Dockspace second = plainWorkspace("lifecycle_second");
        int[] disposals = new int[1];
        Dockspace failing = failingWorkspace(disposals);
        Dockspace afterFailure = plainWorkspace("lifecycle_after_failure");
        int[] immediateCloses = new int[1];
        List<String> immediateOrder = new ArrayList<>();
        Dockspace immediate = closingOnMountWorkspace(immediateCloses, immediateOrder);
        Dockspace afterImmediate = plainWorkspace("lifecycle_after_immediate");
        boolean[] rejectedAfterClose = new boolean[1];

        GameTest test = GameTest.create("glue-test:mcsx-lifecycle")
                .waitForWorld()
                .run("open the first workspace", ctx -> first.open())
                .waitUntil("the first workspace mounts", ctx -> first.getView() != null)
                .waitTicks(5);

        test.run("a second open is rejected and stays harmless", ctx -> {
            boolean rejected = false;
            try {
                second.open();
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            require(rejected, "A second workspace mounted over the first");
            // The rejected instance owns no mount: closing it must not touch the first workspace.
            second.close();
        });
        test.waitTicks(5);
        test.run("assert the rejected close left the first workspace mounted", ctx ->
                require(first.getView() != null, "A rejected workspace's close unmounted the first"));

        ui(test, "close the first workspace from the ModernUI thread", () -> {
            first.close();
            try {
                first.focusPane("pane");
            } catch (IllegalStateException expected) {
                rejectedAfterClose[0] = true;
            }
        });
        test.waitUntil("the first workspace closes", ctx -> first.getView() == null)
                .waitTicks(5)
                .run("runtime work is rejected as soon as close begins", ctx ->
                        require(rejectedAfterClose[0], "A closing workspace still accepted runtime work"))
                .run("closing an already closed workspace is a no-op", ctx -> first.close())
                .run("the rejected workspace opens once the slot is free", ctx -> second.open())
                .waitUntil("the second workspace mounts", ctx -> second.getView() != null)
                .waitTicks(5);

        test.run("close the workspace while the game holds the cursor", ctx -> {
            GameFocus.request();
            require(ctx.client().mouseHandler.isMouseGrabbed(), "Game focus did not grab the cursor");
            second.close();
            require(ctx.client().mouseHandler.isMouseGrabbed(),
                    "Closing while the game held the cursor released it");
        });
        test.waitUntil("the second workspace finishes closing", ctx -> second.getView() == null)
                .waitTicks(5);
        test.run("a stale focus release cannot free the gameplay cursor", ctx -> {
            GameFocus.release();
            require(ctx.client().mouseHandler.isMouseGrabbed(),
                    "A focus release outliving its workspace unlocked the cursor");
        });

        test.run("open a workspace whose pane content fails to mount", ctx -> failing.open());
        // The Fragment mount fails later on the ModernUI thread; the rollback must dispose partial
        // views and release the slot on the client thread, observable as the next open succeeding.
        test.step("the failed mount releases the overlay slot", GameTest.DEFAULT_TIMEOUT, ctx -> {
            try {
                afterFailure.open();
                return true;
            } catch (IllegalStateException stillMounted) {
                return false;
            }
        });
        test.waitUntil("the replacement workspace mounts", ctx -> afterFailure.getView() != null)
                .waitTicks(5)
                .run("assert the failing workspace disposed its created pane exactly once", ctx ->
                        requireEquals(1, disposals[0], "created-pane disposals"))
                .run("close the replacement workspace", ctx -> afterFailure.close())
                .waitUntil("the replacement workspace closes", ctx -> afterFailure.getView() == null)
                .run("close from the mount callback before fragment creation", ctx -> immediate.open())
                .run("assert immediate close delivered its callback once", ctx ->
                        requireEquals(1, immediateCloses[0], "immediate close callbacks"))
                .run("assert mount initialization completed before close callbacks", ctx ->
                        requireEquals(List.of("mount-start", "mount-end", "close"),
                                immediateOrder, "immediate close order"))
                .run("open after the immediate close", ctx -> afterImmediate.open())
                .waitUntil("the final workspace mounts", ctx -> afterImmediate.getView() != null)
                .run("close the final workspace", ctx -> afterImmediate.close())
                .waitUntil("the final workspace closes", ctx -> afterImmediate.getView() == null);
        return test;
    }

    private static Dockspace plainWorkspace(String id) {
        return plainWorkspace(id, () -> {
        });
    }

    private static Dockspace plainWorkspace(String id, Runnable onClose) {
        return Dockspace.builder(ResourceLocation.fromNamespaceAndPath("glue-test", id))
                .persistence(false)
                .pane(DockPane.builder("pane", Component.literal("Pane"), View::new).build())
                .onClose(onClose)
                .build();
    }

    private static Dockspace closingOnMountWorkspace(int[] closes, List<String> order) {
        Dockspace[] workspace = new Dockspace[1];
        workspace[0] = Dockspace.builder(ResourceLocation.fromNamespaceAndPath(
                        "glue-test", "lifecycle_immediate"))
                .persistence(false)
                .pane(DockPane.builder("pane", Component.literal("Pane"), View::new).build())
                .onMount(() -> {
                    order.add("mount-start");
                    workspace[0].close();
                    order.add("mount-end");
                })
                .onClose(() -> {
                    closes[0]++;
                    order.add("close");
                })
                .build();
        return workspace[0];
    }

    /** Two side-by-side leaves so the healthy pane's view exists before the failing one throws. */
    private static Dockspace failingWorkspace(int[] disposals) {
        DockPane created = DockPane.builder("created", Component.literal("Created"), new DockContentCounter(disposals))
                .build();
        DockPane failing = DockPane.builder("failing", Component.literal("Failing"), context -> {
            throw new IllegalStateException("Deliberate mount failure");
        }).build();
        return Dockspace.builder(ResourceLocation.fromNamespaceAndPath("glue-test", "lifecycle_failing"))
                .persistence(false)
                .pane(created)
                .pane(failing)
                .defaultLayout(DockLayouts.layout(DockLayouts.row(
                        DockLayouts.tabs("created"),
                        DockLayouts.tabs("failing")
                )))
                .build();
    }

    private static final class DockContentCounter implements DockContent {

        private final int[] disposals;

        private DockContentCounter(int[] disposals) {
            this.disposals = disposals;
        }

        @Override
        public View create(Context context) {
            return new View(context);
        }

        @Override
        public void dispose(View view) {
            this.disposals[0]++;
        }
    }
}
