package dev.darkspirit69.pendingwhitelist.scheduler;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically removes pending requests that have passed the configured age.
 */
public final class PurgeTask extends BukkitRunnable {

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository storage;

    public PurgeTask(PendingWhitelistPlugin plugin, PendingRepository storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void run() {
        DebugLog.debug("Purge task started");
        if (!plugin.isPurgeEnabled()) {
            return;
        }

        long cutoffMillis = System.currentTimeMillis() - (plugin.getPurgeDays() * 24L * 60L * 60L * 1000L);
        int removed = storage.purgeExpiredEntries(cutoffMillis);

        if (removed > 0) {
            DebugLog.info("Removed " + removed + " expired pending whitelist entries.");
        }
    }
}
