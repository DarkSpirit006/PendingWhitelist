package dev.darkspirit69.pendingwhitelist.listener;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Records rejected whitelist joins and sends the review notification to staff.
 */
public final class JoinListener implements Listener {

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository pendingStorage;
    private final UpdateNotifier updateNotifier;

    public JoinListener(
            PendingWhitelistPlugin plugin, PendingRepository pendingStorage, UpdateNotifier updateNotifier) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
        this.updateNotifier = updateNotifier;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        DebugLog.debug("PlayerLoginEvent: result=" + event.getResult() + ", asynchronous=" + event.isAsynchronous());
        if (event.getResult() != PlayerLoginEvent.Result.KICK_WHITELIST) {
            return;
        }

        String username = event.getPlayer().getName();
        UUID uuid = event.getPlayer().getUniqueId();

        if (username == null || username.isBlank()) {
            return;
        }

        String finalUsername = username;
        UUID finalUuid = uuid;
        Runnable record = () -> recordAttempt(finalUsername, finalUuid);
        if (!event.isAsynchronous()) {
            record.run();
            return;
        }

        try {
            plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                record.run();
                return null;
            }).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DebugLog.warn("Interrupted while recording a pending whitelist attempt.");
        } catch (ExecutionException ex) {
            DebugLog.error("Could not record a pending whitelist attempt.", ex.getCause());
        }
    }

    private void recordAttempt(String username, UUID uuid) {
        FloodgateUtil.Identity identity = FloodgateUtil.resolveOnlineIdentity(uuid);
        if (identity != null) {
            username = identity.username();
            uuid = identity.floodgateUuid();
        }
        DebugLog.debug("Recording rejected whitelist attempt for " + username + " (uuid=" + uuid + ")");
        pendingStorage.recordAttempt(username, uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DebugLog.debug("PlayerJoinEvent received for " + event.getPlayer().getName());
        updateNotifier.notifyIfUpdateAvailable(event.getPlayer());
    }
}
