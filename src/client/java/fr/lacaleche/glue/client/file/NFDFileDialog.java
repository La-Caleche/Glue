package fr.lacaleche.glue.client.file;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Native file dialog implementation using LWJGL's NativeFileDialog (NFD) bindings.
 * Opens the OS-native file picker on a background thread (or main thread on macOS).
 *
 * <p>NFD keeps its state per thread, so one thread owns the whole lifecycle: the initialization,
 * every dialog, and the matching {@code NFD_Quit}. That thread is a daemon worker everywhere except
 * macOS, where the native picker only opens on the main thread. Callers do not have to be on it
 * &mdash; every entry point dispatches there &mdash; and a call made once the client is shutting down
 * comes back as a failed future rather than reopening a lifecycle that has already been closed.</p>
 */
class NFDFileDialog implements FileDialogInterface {
    private static final ThreadLocal<Boolean> initialized = ThreadLocal.withInitial(() -> false);

    /** Orders dialog submission against shutdown; see {@link #submit} and {@link #shutdown()}. */
    private static final Object lifecycleLock = new Object();
    private static final Set<CompletableFuture<?>> pending = new HashSet<>();
    private static boolean stopping;

    /**
     * The thread every NFD call runs on. Resolved lazily: loading this class must not reach for the
     * running client, so that the parts of it that decide nothing native stay testable.
     */
    private static final class Dialogs {
        // The UI can only be opened on the main thread on Mac. However, this will cause
        // the rest of the game to hang, so only do that if we have to.
        @Nullable
        static final ExecutorService WORKER = Minecraft.ON_OSX ? null : newWorker();
        static final Executor EXECUTOR = WORKER != null ? WORKER : Minecraft.getInstance();

        private static ExecutorService newWorker() {
            // A daemon thread: a native dialog cannot be interrupted, so a picker the user walked
            // away from would otherwise keep the JVM alive long after the game window has closed.
            return Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "File Dialog Thread");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private static int tryInit() {
        if (initialized.get()) return NativeFileDialog.NFD_OKAY;

        int result = NativeFileDialog.NFD_Init();
        if (result != NativeFileDialog.NFD_OKAY) {
            throw new IllegalStateException(
                    nativeMessage("initializing NativeFileDialog", NativeFileDialog::NFD_GetError));
        }

        initialized.set(true);
        return result;
    }

    /**
     * Probes NFD on the thread that will own the dialogs, so what {@link #shutdown()} releases is
     * the initialization the dialogs themselves use. NFD keeps that state per thread, and on macOS
     * the native side also mutates the application's activation policy, so a probe run on whichever
     * thread happened to ask for the first dialog would be both unpaired and consequential.
     */
    @Override
    public void init() {
        ExecutorService worker = Dialogs.WORKER;
        if (worker == null) {
            // macOS owns dialogs on the client thread, and the probe cannot be dispatched there and
            // awaited: Minecraft's executor queues instead of running inline whenever the caller is
            // already inside a client-thread task — which is how UI code marshals — so the wait would
            // block the only thread that could satisfy it. The first dialog initializes on that
            // thread as part of running there, and reports a failure through its own future.
            return;
        }

        try {
            CompletableFuture.runAsync(NFDFileDialog::tryInit, worker).join();
        } catch (CompletionException wrapped) {
            // The caller reports the cause; the executor's wrapper adds nothing to it.
            throw wrapped.getCause() instanceof RuntimeException failure ? failure : wrapped;
        }
    }

    /**
     * Releases NFD's native state and retires the dialog thread. The release is queued behind
     * whatever that thread is doing and never waited on, so a dialog the user still has open cannot
     * hold up the client's shutdown &mdash; the worker is a daemon precisely so an abandoned picker
     * cannot outlive the game either.
     *
     * <p>Under the lifecycle lock, so no dialog can be accepted into the queue behind the quit: one
     * that ran after it would reinitialize NFD with nothing left to release it. Dialogs already
     * accepted but not yet started are cancelled, which is what stops a task queued on macOS's client
     * thread from doing the same. Repeated calls do nothing.</p>
     */
    @Override
    public void shutdown() {
        synchronized (lifecycleLock) {
            if (stopping) return;
            stopping = true;

            List<CompletableFuture<?>> accepted = List.copyOf(pending);
            pending.clear();
            // Cancellation cannot interrupt a native picker already on screen; that one stays ahead
            // of the quit and finishes normally.
            accepted.forEach(future -> future.cancel(false));

            ExecutorService worker = Dialogs.WORKER;
            if (worker == null) {
                // macOS runs dialogs on the client thread, which is the thread shutting the client down.
                quit();
                return;
            }

            worker.execute(NFDFileDialog::quit);
            worker.shutdown();
        }
    }

    /**
     * Hands a dialog to its owning thread, unless the client is already shutting down &mdash; in
     * which case the caller gets the failed future the public contract promises rather than a
     * synchronous rejection from a retired executor. Accepted dialogs are tracked only so that
     * shutdown can cancel the ones that have not started.
     */
    private static CompletableFuture<Optional<String>> submit(Supplier<Optional<String>> dialog) {
        synchronized (lifecycleLock) {
            if (stopping) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Native file dialogs are shutting down"));
            }

            CompletableFuture<Optional<String>> future = CompletableFuture.supplyAsync(dialog, Dialogs.EXECUTOR);
            pending.add(future);
            // Registered after the add on purpose: an inline completion runs this immediately and
            // removes what was just put in, rather than leaving it behind.
            future.whenComplete((path, failure) -> {
                synchronized (lifecycleLock) {
                    pending.remove(future);
                }
            });
            return future;
        }
    }

    /** NFD initializes per thread, so it has to be released on the thread that initialized it. */
    private static void quit() {
        if (!initialized.get()) return;

        initialized.set(false);
        NativeFileDialog.NFD_Quit();
    }

    @Override
    public CompletableFuture<Optional<String>> showSaveDialog(@Nullable String defaultPath,
                                                              @Nullable String defaultName, FileDialogs.FileFilter... filters) {
        return submit(() -> saveDialogSync(defaultPath, defaultName, filters));
    }

    public Optional<String> saveDialogSync(@Nullable String defaultPath,
                                           @Nullable String defaultName, FileDialogs.FileFilter... filters) {
        tryInit();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.callocPointer(1);
            NFDFilterItem.Buffer filter = createFilters(filters, stack);

            int result = NativeFileDialog.NFD_SaveDialog(out, filter, defaultPath, defaultName);

            if (result == NativeFileDialog.NFD_OKAY) {
                String returnVal = out.getStringUTF8(0);
                NativeFileDialog.NFD_FreePath(out.get(0));

                return Optional.of(returnVal);
            }
            return cancellationOrFailure(result, "saving a file", NativeFileDialog::NFD_GetError);
        }
    }

    @Override
    public CompletableFuture<Optional<String>> showOpenDialog(@Nullable String defaultPath, FileDialogs.FileFilter... filters) {
        return submit(() -> openDialogSync(defaultPath, filters));
    }

    public Optional<String> openDialogSync(@Nullable String defaultPath, FileDialogs.FileFilter... filters) {
        tryInit();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.callocPointer(1);
            NFDFilterItem.Buffer filter = createFilters(filters, stack);

            int result = NativeFileDialog.NFD_OpenDialog(out, filter, defaultPath);

            if (result == NativeFileDialog.NFD_OKAY) {
                String returnVal = out.getStringUTF8(0);
                NativeFileDialog.NFD_FreePath(out.get(0));

                return Optional.of(returnVal);
            }
            return cancellationOrFailure(result, "opening a file", NativeFileDialog::NFD_GetError);
        }
    }

    @Override
    public CompletableFuture<Optional<String>> showOpenFolderDialog(@Nullable String defaultPath) {
        return submit(() -> pickFolderSync(defaultPath));
    }

    public Optional<String> pickFolderSync(@Nullable String defaultPath) {
        tryInit();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.callocPointer(1);

            int result = NativeFileDialog.NFD_PickFolder(out, defaultPath);

            if (result == NativeFileDialog.NFD_OKAY) {
                String returnVal = out.getStringUTF8(0);
                NativeFileDialog.NFD_FreePath(out.get(0));

                return Optional.of(returnVal);
            }
            return cancellationOrFailure(result, "selecting a folder", NativeFileDialog::NFD_GetError);
        }
    }

    /**
     * Reads a result that is not a chosen path. Only {@code NFD_CANCEL} is the user declining the
     * dialog; a native error — or a result these bindings do not define — is a failure, and the
     * public contract is that a caller can tell the two apart. Thrown here, it becomes the
     * exceptional completion of the future the dialog was requested through.
     *
     * @param action      what was being attempted, for the message ("saving a file")
     * @param errorDetail NFD's own account of the failure, consulted only for {@code NFD_ERROR}
     */
    static Optional<String> cancellationOrFailure(int result, String action, Supplier<String> errorDetail) {
        if (result == NativeFileDialog.NFD_CANCEL) return Optional.empty();

        if (result == NativeFileDialog.NFD_ERROR) {
            throw new IllegalStateException(nativeMessage(action, errorDetail));
        }
        throw new IllegalStateException("Unexpected NativeFileDialog result " + result + " while " + action);
    }

    private static String nativeMessage(String action, Supplier<String> errorDetail) {
        String detail = errorDetail.get();
        return detail == null || detail.isBlank()
                ? "Native file dialog failed while " + action
                : "Native file dialog failed while " + action + ": " + detail;
    }

    /** One filter as NFD takes it: a friendly name and a non-empty comma-joined specification. */
    record FilterSpec(String name, String spec) {
    }

    /**
     * Normalizes the caller's filters into the ones NFD can be given. NFD documents that every
     * specification must be non-empty — an empty one is undefined behaviour — and it adds a wildcard
     * entry to every dialog itself, so a filter that normalizes to nothing (a bare {@code "*"}, or no
     * extensions at all) is dropped rather than emitted as an empty specification.
     */
    static List<FilterSpec> normalize(FileDialogs.FileFilter[] fileFilters) {
        if (fileFilters == null) return List.of();

        List<FilterSpec> specs = new ArrayList<>(fileFilters.length);
        for (FileDialogs.FileFilter filter : fileFilters) {
            if (filter == null) continue;

            String spec = String.join(",", extensionsOf(filter));
            if (!spec.isEmpty()) specs.add(new FilterSpec(filter.name(), spec));
        }
        return specs;
    }

    private static List<String> extensionsOf(FileDialogs.FileFilter filter) {
        if (filter.extensions() == null) return List.of();

        List<String> tokens = new ArrayList<>();
        for (String extension : filter.extensions()) {
            if (extension == null) continue;

            // Split before normalizing: a caller that wrote "png,jpg" as one string would otherwise
            // reach NFD as a single token, and "png," would carry an empty one past the blank check.
            for (String token : extension.split(",", -1)) {
                String normalized = normalizeExtension(token);
                if (!normalized.isEmpty()) tokens.add(normalized);
            }
        }
        return tokens;
    }

    /**
     * Reduces one extension to what NFD wants: no leading wildcards or dots. Stripping repeatedly is
     * what makes every conventional spelling of "all files" &mdash; {@code *}, {@code *.*}, {@code .*},
     * {@code **} &mdash; come out empty and be dropped, instead of surviving as a wildcard token NFD
     * would have to interpret.
     */
    private static String normalizeExtension(String extension) {
        String normalized = extension.trim();
        while (normalized.startsWith("*") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private static NFDFilterItem.Buffer createFilters(FileDialogs.FileFilter[] fileFilters, MemoryStack stack) {
        List<FilterSpec> specs = normalize(fileFilters);
        if (specs.isEmpty()) return null;

        NFDFilterItem.Buffer buffer = NFDFilterItem.malloc(specs.size(), stack);
        for (int index = 0; index < specs.size(); index++) {
            FilterSpec spec = specs.get(index);
            NFDFilterItem item = buffer.get(index);

            item.name(stack.UTF8(spec.name()));
            item.spec(stack.UTF8(spec.spec()));
        }
        return buffer;
    }
}
