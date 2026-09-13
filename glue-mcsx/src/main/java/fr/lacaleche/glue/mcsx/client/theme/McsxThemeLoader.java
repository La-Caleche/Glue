package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.Mcsx;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeDefinition;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeJsonParser;
import icyllis.modernui.core.Core;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
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
public final class McsxThemeLoader implements IdentifiableResourceReloadListener {

    private static final ResourceLocation FABRIC_ID = Mcsx.id("themes");
    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("mcsx/themes");
    private final BooleanSupplier hasUiDispatcher;
    private final Executor uiDispatcher;
    private final Consumer<Map<ResourceLocation, Theme>> publication;

    public McsxThemeLoader() {
        this(
                () -> Core.getUiThread() != null,
                Core::postOnUiThread,
                Themes::install
        );
    }

    McsxThemeLoader(
            BooleanSupplier hasUiDispatcher,
            Executor uiDispatcher,
            Consumer<Map<ResourceLocation, Theme>> publication
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

    private Map<ResourceLocation, Theme> prepare(ResourceManager manager) {
        Map<ResourceLocation, ThemeDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : CONVERTER.listMatchingResources(manager).entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation id = CONVERTER.fileToId(file);
            try (Reader reader = entry.getValue().openAsReader()) {
                definitions.put(id, ThemeJsonParser.parse(id, reader));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read MCSX theme " + file, exception);
            }
        }
        return ThemeResolver.resolve(definitions);
    }

    private CompletableFuture<Void> apply(Map<ResourceLocation, Theme> themes) {
        Runnable publish = () -> {
            try {
                this.publication.accept(themes);
            } catch (RuntimeException exception) {
                Mcsx.LOGGER.error("Failed to publish MCSX theme reload", exception);
            }
        };
        if (!this.hasUiDispatcher.getAsBoolean()) {
            this.publication.accept(themes);
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
}
