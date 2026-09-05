package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.gui.WlGuiListener;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main plugin entry point; wires the command, listeners, storage, and update
 * checker together.
 */
public final class PendingWhitelistPlugin extends JavaPlugin {

    private PendingRepository pendingStorage;
    private WlCommand wlCommand;
    private UpdateNotifier updateNotifier;
    private Command registeredCommand;
    private final Map<UUID, WlGui> guiViewers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        boolean configMigrated = migrateConfiguration();
        DebugLog.initialize(getLogger(), getConfig().getBoolean("logging.debug", false));
        DebugLog.info("Enabling PendingWhitelist " + getPluginMeta().getVersion()
                + " (debug logging: " + DebugLog.isEnabled() + ")");
        if (configMigrated) {
            DebugLog.info("Updated config.yml with missing configuration defaults");
        }
        DebugLog.debug("Loading configuration");
        SkinHeadUtil.initialize(this);

        this.pendingStorage = new PendingStorage(this);
        this.updateNotifier = new UpdateNotifier(this);
        this.wlCommand = new WlCommand(this, pendingStorage, updateNotifier);

        DebugLog.debug("Loading pending entries from disk");
        pendingStorage.loadFromDisk();
        getServer().getPluginManager().registerEvents(new JoinListener(this, pendingStorage, updateNotifier), this);
        getServer().getPluginManager().registerEvents(new WlGuiListener(this, pendingStorage), this);
        DebugLog.debug("Registering /wl command");
        registerCommand();

        pendingStorage.schedulePurgeCheck();
        DebugLog.info("PendingWhitelist enabled successfully");
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
        registeredCommand = command;
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
        DebugLog.info("Disabling PendingWhitelist");
        closeOpenGuis();
        unregisterCommand();
        if (pendingStorage != null) {
            pendingStorage.shutdown();
        }
        SkinHeadUtil.shutdown();
        DebugLog.info("PendingWhitelist disabled");
    }

    /**
     * Reloads plugin configuration and refreshes runtime state without replacing
     * the Paper plugin instance.
     */
    public boolean reloadConfiguration() {
        if (!isEnabled()) {
            return false;
        }

        DebugLog.info("Reload requested");
        closeOpenGuis();

        if (pendingStorage != null) {
            pendingStorage.flushSynchronously();
        }

        reloadConfig();
        boolean configMigrated = migrateConfiguration();
        DebugLog.initialize(getLogger(), getConfig().getBoolean("logging.debug", false));
        if (configMigrated) {
            DebugLog.info("Updated config.yml with missing configuration defaults");
        }

        pendingStorage.schedulePurgeCheck();
        DebugLog.info("PendingWhitelist reloaded successfully (debug logging: " + DebugLog.isEnabled() + ")");
        return true;
    }

    public void trackGuiViewer(UUID playerId, WlGui gui) {
        guiViewers.put(playerId, gui);
    }

    public boolean untrackGuiViewer(UUID playerId, WlGui gui) {
        if (guiViewers.get(playerId) == gui) {
            guiViewers.remove(playerId);
            return true;
        }
        return false;
    }

    public boolean isGuiViewer(UUID playerId) {
        return guiViewers.containsKey(playerId);
    }

    private void closeOpenGuis() {
        int closed = 0;
        for (UUID playerId : Set.copyOf(guiViewers.keySet())) {
            Player player = getServer().getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
                closed++;
            }
        }
        guiViewers.clear();
        if (closed > 0) {
            DebugLog.debug("Closed " + closed + " PendingWhitelist GUI(s)");
        }
    }

    private void unregisterCommand() {
        if (registeredCommand == null) {
            return;
        }
        try {
            registeredCommand.unregister(getCommandMap());
            DebugLog.debug("Unregistered /wl command");
        } catch (RuntimeException ex) {
            DebugLog.error("Could not unregister /wl during shutdown.", ex);
        } finally {
            registeredCommand = null;
        }
    }

    private boolean migrateConfiguration() {
        FileConfiguration config = getConfig();
        boolean changed = false;

        changed |= addDefaultConfigValue(config, "logging.debug", false);
        changed |= addDefaultConfigValue(config, "page-size", 10);
        changed |= addDefaultConfigValue(config, "notifications.join-attempts", true);
        changed |= addDefaultConfigValue(config, "notifications.join-attempt-cooldown-seconds", 60);
        changed |= addDefaultConfigValue(config, "purge.enabled", true);
        changed |= addDefaultConfigValue(config, "purge.days", 30);

        if (changed) {
            saveConfig();
        }
        return changed;
    }

    private boolean addDefaultConfigValue(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public PendingRepository getPendingStorage() {
        return pendingStorage;
    }

    public String getInstalledVersion() {
        return getPluginMeta().getVersion();
    }

    public UpdateNotifier getUpdateNotifier() {
        return updateNotifier;
    }

    public boolean isDebugLoggingEnabled() {
        return DebugLog.isEnabled();
    }

    public void refreshDebugLogging() {
        DebugLog.initialize(getLogger(), getConfig().getBoolean("logging.debug", false));
        DebugLog.info("Debug logging is now " + (DebugLog.isEnabled() ? "enabled" : "disabled"));
    }

    public boolean isJoinAttemptNotificationsEnabled() {
        return getConfig().getBoolean("notifications.join-attempts", true);
    }

    public int getJoinAttemptNotificationCooldownSeconds() {
        return Math.max(0, getConfig().getInt("notifications.join-attempt-cooldown-seconds", 60));
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
