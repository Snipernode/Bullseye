package tsd.beye.pack.server;

import org.bukkit.entity.Player;

public interface BullseyePackServer {
    String packUrl();

    String hash();

    boolean isPackUploaded();

    void start();

    void stop();

    boolean sendPack(Player player);
}
