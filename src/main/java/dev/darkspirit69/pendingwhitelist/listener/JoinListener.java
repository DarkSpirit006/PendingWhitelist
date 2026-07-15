package dev.darkspirit69.pendingwhitelist.listener;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

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
        if (username == null || username.isBlank()) {
            return;
        }

        pendingStorage.recordAttempt(username);
    }
}
