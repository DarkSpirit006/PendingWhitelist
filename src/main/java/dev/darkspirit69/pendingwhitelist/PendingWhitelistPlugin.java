package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.gui.WlGuiListener;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;

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
        registerCommand();

        pendingStorage.schedulePurgeCheck();

    }


    /**
     * Registers /wl directly with Paper's command map.
     * Paper plugins do not read the legacy plugin.yml "commands" section.
     */
    private void registerCommand() {
        Command command = new Command("wl") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return wlCommand.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return wlCommand.onTabComplete(sender, this, alias, args);
            }
        };
        command.setDescription("Manage pending whitelist entries and open the admin GUI.");
        command.setUsage("/wl <pl|list|add|remove|rpl|on|off|reload|version>");
        command.setPermission("pendingwhitelist.admin");

        CommandMap commandMap = getCommandMap();
        if (!commandMap.register("pendingwhitelist", command)) {
            throw new IllegalStateException("Unable to register the /wl command.");
        }
    }

    private CommandMap getCommandMap() {
        try {
            Method method = getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(getServer());
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalStateException("Unable to access the server command map.", ex);
        }
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
