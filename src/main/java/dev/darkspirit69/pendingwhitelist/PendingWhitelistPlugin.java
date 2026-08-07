package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.PluginUpdater;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingWhitelistPlugin extends JavaPlugin {

    private PendingStorage pendingStorage;
    private WlCommand wlCommand;
    private PluginUpdater pluginUpdater;
    private final Map<UUID, String> temporaryWhitelistNames = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pendingStorage = new PendingStorage(this);
        this.pluginUpdater = new PluginUpdater(this);
        this.wlCommand = new WlCommand(this, pendingStorage, pluginUpdater);

        pendingStorage.loadFromDisk();
        getServer().getPluginManager().registerEvents(new JoinListener(this, pendingStorage), this);
        getCommand("wl").setExecutor(wlCommand);
        getCommand("wl").setTabCompleter(wlCommand);

        pendingStorage.schedulePurgeCheck();

        if (isUpdateEnabled()) {
            pluginUpdater.scheduleChecks();
        } else {
            getLogger().info("Automatic update checks are disabled in config.yml.");
        }
    }

    @Override
    public void onDisable() {
        pendingStorage.flushSynchronously();
    }

    public PendingStorage getPendingStorage() {
        return pendingStorage;
    }

    public String getInstalledVersion() {
        return getDescription().getVersion();
    }

    public void trackTemporaryUuidWhitelist(UUID uuid, String username) {
        if (uuid != null && username != null && !username.isBlank()) {
            temporaryWhitelistNames.put(uuid, username);
            getLogger().info("Temporarily whitelisted " + username + " by UUID for Geyser compatibility.");
        }
    }

    public String consumeTemporaryUuidWhitelist(UUID uuid) {
        return temporaryWhitelistNames.remove(uuid);
    }

    public int getConfiguredPageSize() {
        return Math.max(1, getConfig().getInt("page-size", 10));
    }

    public boolean isPurgeEnabled() {
        return getConfig().getBoolean("purge.enabled", true);
    }

    public int getPurgeDays() {
        return Math.max(1, getConfig().getInt("purge.days", 30));
    }

    public boolean isUpdateEnabled() {
        return getConfig().getBoolean("update.enabled", true);
    }

    public int getUpdateCheckIntervalHours() {
        return Math.max(1, getConfig().getInt("update.check-interval-hours", 24));
    }
}
