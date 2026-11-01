package tsd.ttt.pack.server;

import org.bukkit.entity.Player;
import tsd.ttt.TTT;
import tsd.ttt.service.ResourcePackService;

public class SelfHostServer implements TTTPackServer {
    private final TTT plugin;
    private final ResourcePackService resourcePackService;

    public SelfHostServer(TTT plugin, ResourcePackService resourcePackService) {
        this.plugin = plugin;
        this.resourcePackService = resourcePackService;
    }

    @Override
    public String packUrl() {
        if (resourcePackService.isHostingRunning()) {
            return resourcePackService.buildHostedUrl();
        }

        String configured = plugin.getConfig().getString("resource-pack.url", "");
        return configured == null ? "" : configured;
    }

    @Override
    public String hash() {
        return resourcePackService.getLatestHashHex();
    }

    @Override
    public boolean isPackUploaded() {
        return resourcePackService.hasPackFile();
    }

    @Override
    public void start() {
        resourcePackService.startOrRestartHosting();
    }

    @Override
    public void stop() {
        resourcePackService.shutdown();
    }

    @Override
    public boolean sendPack(Player player) {
        return resourcePackService.sendPack(player);
    }
}
