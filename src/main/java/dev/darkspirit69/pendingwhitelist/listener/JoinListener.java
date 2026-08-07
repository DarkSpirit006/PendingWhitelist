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
        UUID uuid = event.getPlayer().getUniqueId();
        String username = plugin.consumeTemporaryUuidWhitelist(uuid);
        if (username == null) {
            return;
        }

        // Run after the join has completed. Geyser may not have finished
        // publishing the player's profile when PlayerJoinEvent is delivered.
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean removed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + uuid);
            boolean added = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + username);
            if (removed && added) {
                plugin.getLogger().info("Converted the temporary UUID whitelist entry for " + username
                        + " to a username entry after the player joined.");
            } else {
                plugin.getLogger().warning("Could not convert the temporary UUID whitelist entry for " + username
                        + " (remove=" + removed + ", add=" + added + ").");
            }
        });
    }
}
