package dev.darkspirit69.pendingwhitelist.scheduler;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.scheduler.BukkitRunnable;

public class PurgeTask extends BukkitRunnable {

    private final PendingWhitelistPlugin plugin;
    private final PendingStorage storage;

    public PurgeTask(PendingWhitelistPlugin plugin, PendingStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void run() {
        if (!plugin.isPurgeEnabled()) {
            return;
        }

        long cutoffMillis = System.currentTimeMillis() - (plugin.getPurgeDays() * 24L * 60L * 60L * 1000L);
        int removed = storage.purgeExpiredEntries(cutoffMillis);

        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " expired pending whitelist entries.");
        }
    }
}
