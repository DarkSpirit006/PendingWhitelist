package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class PendingWhitelistPlugin extends JavaPlugin {

    private PendingStorage pendingStorage;
    private WlCommand wlCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pendingStorage = new PendingStorage(this);
        this.wlCommand = new WlCommand(this, pendingStorage);

        pendingStorage.loadFromDisk();
        getServer().getPluginManager().registerEvents(new JoinListener(pendingStorage), this);
        getCommand("wl").setExecutor(wlCommand);
        getCommand("wl").setTabCompleter(wlCommand);

        pendingStorage.schedulePurgeCheck();
    }

    @Override
    public void onDisable() {
        pendingStorage.flushSynchronously();
    }

    public PendingStorage getPendingStorage() {
        return pendingStorage;
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
}
