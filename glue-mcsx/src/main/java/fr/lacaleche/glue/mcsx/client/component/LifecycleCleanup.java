package fr.lacaleche.glue.mcsx.client.component;

final class LifecycleCleanup {

    private LifecycleCleanup() {
    }

    static RuntimeException attempt(RuntimeException failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException exception) {
            if (failure == null) return exception;

            failure.addSuppressed(exception);
        }
        return failure;
    }

    static void finish(RuntimeException failure) {
        if (failure != null) throw failure;
    }
}
