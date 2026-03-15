package tsd.beye.pack.server;

import org.bukkit.entity.Player;
import tsd.beye.Bullseye;
import tsd.beye.service.ResourcePackService;

public class SelfHostServer implements BullseyePackServer {
    private final Bullseye plugin;
    private final ResourcePackService resourcePackService;

    public SelfHostServer(Bullseye plugin, ResourcePackService resourcePackService) {
        this.plugin = plugin;
        this.resourcePackService = resourcePackService;
    }

    @Override
    public String packUrl() {
        if (resourcePackService.isHostingEnabled()) {
            return resourcePackService.buildHostedUrl();
        }
        return plugin.getConfig().getString("resource-pack.url", "");
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
