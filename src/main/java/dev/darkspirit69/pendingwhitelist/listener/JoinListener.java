package dev.darkspirit69.pendingwhitelist.listener;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

public class JoinListener implements Listener {

    private final PendingStorage pendingStorage;
    private final PendingWhitelistPlugin plugin;

    public JoinListener(PendingWhitelistPlugin plugin, PendingStorage pendingStorage) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_WHITELIST) {
            return;
        }

        String username = event.getPlayer().getName();
        UUID uuid = event.getPlayer().getUniqueId();

        if (username == null || username.isBlank()) {
            username = event.getPlayer().getAddress() != null ? event.getPlayer().getAddress().getHostName() : null;
        }

        if (username == null || username.isBlank()) {
            return;
        }

        pendingStorage.recordAttempt(username, uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String username = plugin.consumeTemporaryUuidWhitelist(event.getPlayer().getUniqueId());
        if (username == null) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + uuid);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + username);
        plugin.getLogger().info("Converted the temporary UUID whitelist entry for " + username
                + " to a username entry after the player joined.");
    }
}
