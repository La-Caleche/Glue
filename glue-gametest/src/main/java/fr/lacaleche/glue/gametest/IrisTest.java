package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** A test-thread shader toggle restoring the initial state on close. Requires Iris at runtime. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class IrisTest implements AutoCloseable {

    private final ClientGameTestContext context;
    private final ClientTest game;
    private final boolean initial;
    private boolean closed;

    public IrisTest(ClientGameTestContext context) {
        this.context = context;
        this.game = new ClientTest(context);
        if (!IrisHooks.loaded()) throw new IllegalStateException("This test requires Iris at runtime");
        this.initial = context.computeOnClient(client -> IrisHooks.shaderPackInUse());
    }

    /** Restores the shader state while the test's world is still loaded. */
    @Override
    public void close() {
        if (this.closed) return;
        this.setEnabled(this.initial);
        this.closed = true;
    }

    public void setEnabled(boolean enabled) {
        if (this.closed) throw new IllegalStateException("The Iris test scope is closed");
        boolean changed = this.context.computeOnClient(client -> {
            if (IrisHooks.shaderPackInUse() == enabled) return false;
            IrisHooks.setShadersEnabled(enabled);
            if (IrisHooks.shaderPackInUse() != enabled) {
                throw new AssertionError("Iris did not activate the requested state; enabling requires a valid shaderpack");
            }
            return true;
        });
        if (changed) this.game.waitForWorldFrames(10);
    }
}
