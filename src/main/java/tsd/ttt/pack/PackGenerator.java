package tsd.ttt.pack;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import tsd.ttt.TTT;
import tsd.ttt.service.ResourcePackService;

public class PackGenerator {
    private final TTT plugin;
    private final ResourcePackService resourcePackService;
    private CompletableFuture<Void> packGenFuture = CompletableFuture.completedFuture(null);

    public PackGenerator(TTT plugin, ResourcePackService resourcePackService) {
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
        String outputFile = plugin.getConfig().getString("resource-pack.output-file", "generated/ttt-pack.zip");
        return plugin.getDataFolder().toPath().resolve(outputFile);
    }
}
