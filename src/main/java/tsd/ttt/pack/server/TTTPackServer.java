package tsd.ttt.pack.server;

import org.bukkit.entity.Player;

public interface TTTPackServer {
    String packUrl();

    String hash();

    boolean isPackUploaded();

    void start();

    void stop();

    boolean sendPack(Player player);
}
