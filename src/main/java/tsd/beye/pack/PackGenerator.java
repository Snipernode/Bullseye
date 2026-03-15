package tsd.beye.pack;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import tsd.beye.Bullseye;
import tsd.beye.service.ResourcePackService;

public class PackGenerator {
    private final Bullseye plugin;
    private final ResourcePackService resourcePackService;
    private CompletableFuture<Void> packGenFuture = CompletableFuture.completedFuture(null);

    public PackGenerator(Bullseye plugin, ResourcePackService resourcePackService) {
        this.plugin = plugin;
        this.resourcePackService = resourcePackService;
    }

    public CompletableFuture<Void> getPackGenFuture() {
        return packGenFuture;
    }

    public void regeneratePack() {
        generatePack(false);
    }

    public void generatePack(boolean async) {
        if (async) {
            packGenFuture = CompletableFuture.runAsync(resourcePackService::rebuildPack);
            return;
        }

        resourcePackService.rebuildPack();
        packGenFuture = CompletableFuture.completedFuture(null);
    }

    public Path outputPath() {
        String outputFile = plugin.getConfig().getString("resource-pack.output-file", "generated/bullseye-pack.zip");
        return plugin.getDataFolder().toPath().resolve(outputFile);
    }
}
