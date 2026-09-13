package fr.lacaleche.glue.mcsx.client.style;

import fr.lacaleche.glue.mcsx.Mcsx;
import fr.lacaleche.glue.mcsx.client.internal.text.MinecraftText;
import icyllis.modernui.core.Core;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class McsxStylesheetLoader implements IdentifiableResourceReloadListener {

    private static final ResourceLocation FABRIC_ID = Mcsx.id("stylesheets");
    private static final String DIRECTORY = "mcsx/styles";
    private static final String EXTENSION = ".mcss";
    private final BooleanSupplier hasUiDispatcher;
    private final Executor uiDispatcher;
    private final Consumer<Map<ResourceLocation, Stylesheet>> publication;

    public McsxStylesheetLoader() {
        this(
                () -> Core.getUiThread() != null,
                Core::postOnUiThread,
                stylesheets -> {
                    Stylesheets.install(stylesheets);
                    MinecraftText.resourcesReloaded();
                }
        );
    }

    McsxStylesheetLoader(
            BooleanSupplier hasUiDispatcher,
            Executor uiDispatcher,
            Consumer<Map<ResourceLocation, Stylesheet>> publication
    ) {
        this.hasUiDispatcher = Objects.requireNonNull(hasUiDispatcher, "hasUiDispatcher");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public CompletableFuture<Void> reload(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager manager,
            Executor preparationExecutor,
            Executor applyExecutor
    ) {
        return CompletableFuture.supplyAsync(() -> this.prepare(manager), preparationExecutor)
                .thenCompose(barrier::wait)
                .thenComposeAsync(this::apply, applyExecutor);
    }

    /**
     * Reads and parses every stylesheet, propagating a malformed source as a reload failure so the
     * resource-pack error screen reports the offending file, line and column.
     */
    private Map<ResourceLocation, Stylesheet> prepare(ResourceManager manager) {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(
                DIRECTORY,
                id -> id.getPath().endsWith(EXTENSION)
        ).entrySet()) {
            ResourceLocation resource = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                sources.put(this.toStylesheetId(resource), this.read(reader));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read MCSX stylesheet " + resource, exception);
            }
        }
        return parseSources(sources);
    }

    private CompletableFuture<Void> apply(Map<ResourceLocation, Stylesheet> stylesheets) {
        Runnable publish = () -> {
            try {
                this.publication.accept(stylesheets);
            } catch (RuntimeException exception) {
                Mcsx.LOGGER.error("Failed to publish MCSX resource reload", exception);
            }
        };
        if (!this.hasUiDispatcher.getAsBoolean()) {
            this.publication.accept(stylesheets);
            return CompletableFuture.completedFuture(null);
        }

        try {
            // ModernUI may be waiting for render to consume a frame, so reload cannot await this.
            this.uiDispatcher.execute(publish);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static Map<ResourceLocation, Stylesheet> parseSources(Map<ResourceLocation, String> sources) {
        Map<ResourceLocation, Stylesheet> candidate = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> entry : sources.entrySet()) {
            candidate.put(
                    entry.getKey(),
                    StylesheetParser.parse(entry.getKey(), entry.getValue())
            );
        }
        return Map.copyOf(candidate);
    }

    private ResourceLocation toStylesheetId(ResourceLocation resource) {
        String path = resource.getPath();
        String prefix = DIRECTORY + "/";
        String logicalPath = path.substring(prefix.length(), path.length() - EXTENSION.length());
        return ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), logicalPath);
    }

    private String read(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }
}
