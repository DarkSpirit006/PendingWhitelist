package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.gui.WlGuiListener;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Main plugin entry point; wires the command, listeners, storage, and update checker together. */
public final class PendingWhitelistPlugin extends JavaPlugin {

    private PendingStorage pendingStorage;
    private WlCommand wlCommand;
    private UpdateNotifier updateNotifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        SkinHeadUtil.initialize();

        this.pendingStorage = new PendingStorage(this);
        this.updateNotifier = new UpdateNotifier(this);
        this.wlCommand = new WlCommand(this, pendingStorage, updateNotifier);

        pendingStorage.loadFromDisk();
        getServer().getPluginManager().registerEvents(new JoinListener(pendingStorage, updateNotifier), this);
        getServer().getPluginManager().registerEvents(new WlGuiListener(this, pendingStorage), this);
        PluginCommand command = getCommand("wl");
        if (command == null) {
            throw new IllegalStateException("The wl command is missing from plugin.yml");
        }
        command.setExecutor(wlCommand);
        command.setTabCompleter(wlCommand);

        pendingStorage.schedulePurgeCheck();

    }

    @Override
    public void onDisable() {
        if (pendingStorage != null) {
            pendingStorage.flushSynchronously();
        }
        SkinHeadUtil.shutdown();
    }

    public PendingStorage getPendingStorage() {
        return pendingStorage;
    }

    public String getInstalledVersion() {
        return getPluginMeta().getVersion();
    }

    public boolean isPurgeEnabled() {
        return getConfig().getBoolean("purge.enabled", true);
    }

    public int getPurgeDays() {
        return Math.max(1, getConfig().getInt("purge.days", 30));
    }

    public int getConfiguredPageSize() {
        return Math.max(1, getConfig().getInt("page-size", 10));
    }

}
