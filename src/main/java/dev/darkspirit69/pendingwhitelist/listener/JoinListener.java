package dev.darkspirit69.pendingwhitelist.listener;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

public class JoinListener implements Listener {

    private final PendingStorage pendingStorage;

    public JoinListener(PendingStorage pendingStorage) {
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
}
