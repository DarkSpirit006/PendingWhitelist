package dev.darkspirit69.pendingwhitelist.listener;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

/** Records rejected whitelist joins and sends the review notification to staff. */
public final class JoinListener implements Listener {

    private final PendingStorage pendingStorage;
    private final UpdateNotifier updateNotifier;

    public JoinListener(PendingStorage pendingStorage, UpdateNotifier updateNotifier) {
        this.pendingStorage = pendingStorage;
        this.updateNotifier = updateNotifier;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_WHITELIST) {
            return;
        }

        String username = event.getPlayer().getName();
        UUID uuid = event.getPlayer().getUniqueId();

        if (username == null || username.isBlank()) {
            return;
        }

        FloodgateUtil.Identity identity = FloodgateUtil.resolveOnlineIdentity(event.getPlayer());
        if (identity != null) {
            username = identity.username();
            uuid = identity.floodgateUuid();
        }

        pendingStorage.recordAttempt(username, uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateNotifier.notifyIfUpdateAvailable(event.getPlayer());
    }
}
