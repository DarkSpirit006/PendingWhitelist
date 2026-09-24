package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.command.WlCommand;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.gui.WlGuiListener;
import dev.darkspirit69.pendingwhitelist.listener.JoinListener;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main plugin entry point. */
public final class PendingWhitelistPlugin extends JavaPlugin {

    private PendingRepository pendingStorage;
    private WlCommand wlCommand;
    private UpdateNotifier updateNotifier;
    private Command registeredCommand;
    private Command registeredBedrockCommand;
    private Metrics metrics;
    private final Map<UUID, WlGui> guiViewers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        boolean configMigrated = migrateConfiguration();
        DebugLog.initialize(getLogger(), getConfig().getBoolean("logging.debug", false));
        DebugLog.info("Enabling PendingWhitelist " + getPluginMeta().getVersion()
                + " (debug logging: " + DebugLog.isEnabled() + ")");
        initializeMetrics();
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
        DebugLog.debug("Registering /wl commands");
        registerCommands();

        pendingStorage.schedulePurgeCheck();
        DebugLog.info("PendingWhitelist enabled successfully");
    }

    /**
     * Registers /wl and /wlb directly with Paper's command map.
     * Paper plugins do not read the legacy plugin.yml "commands" section.
     */
    private void registerCommands() {
        CommandMap commandMap = getCommandMap();

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
        if (!commandMap.register("pendingwhitelist", command)) {
            throw new IllegalStateException("Unable to register the /wl command.");
        }
        registeredCommand = command;

        if (FloodgateUtil.isAvailable()) {
            Command bedrockCommand = new Command("wlb") {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return wlCommand.onBedrockCommand(sender, this, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return wlCommand.onTabComplete(sender, this, alias, args);
                }
            };
            bedrockCommand.setDescription("Add Bedrock players to the whitelist through Floodgate.");
            bedrockCommand.setUsage("/wlb add <username> [username ...]");
            bedrockCommand.setPermission("pendingwhitelist.admin");
            if (!commandMap.register("pendingwhitelist", bedrockCommand)) {
                command.unregister(commandMap);
                registeredCommand = null;
                throw new IllegalStateException("Unable to register the /wlb command.");
            }
            registeredBedrockCommand = bedrockCommand;
            DebugLog.debug("Registered /wlb because Floodgate is available");
        } else {
            DebugLog.debug("Floodgate is unavailable; /wlb will not be registered");
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
        DebugLog.info("Disabling PendingWhitelist");
        closeOpenGuis();
        unregisterCommands();
        if (pendingStorage != null) {
            pendingStorage.shutdown();
        }
        shutdownMetrics();
        SkinHeadUtil.shutdown();
        FloodgateUtil.reset();
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

    private void unregisterCommands() {
        CommandMap commandMap;
        try {
            commandMap = getCommandMap();
        } catch (RuntimeException ex) {
            DebugLog.error("Could not access the command map during shutdown.", ex);
            return;
        }

        if (registeredBedrockCommand != null) {
            try {
                registeredBedrockCommand.unregister(commandMap);
                DebugLog.debug("Unregistered /wlb command");
            } catch (RuntimeException ex) {
                DebugLog.error("Could not unregister /wlb during shutdown.", ex);
            } finally {
                registeredBedrockCommand = null;
            }
        }

        if (registeredCommand != null) {
            try {
                registeredCommand.unregister(commandMap);
                DebugLog.debug("Unregistered /wl command");
            } catch (RuntimeException ex) {
                DebugLog.error("Could not unregister /wl during shutdown.", ex);
            } finally {
                registeredCommand = null;
            }
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

    /** Initializes bStats without making metrics a requirement for the plugin. */
    private void initializeMetrics() {
        final int pluginId = 33884;
        DebugLog.debug("Initializing bStats metrics (plugin ID: " + pluginId + ")");

        try {
            metrics = new Metrics(this, pluginId);
            DebugLog.debug("bStats Metrics initialized successfully (plugin ID: " + pluginId + ")");
            logMetricsConfiguration(pluginId);
        } catch (RuntimeException ex) {
            DebugLog.error("Failed to initialize bStats metrics; continuing without metrics.", ex);
            metrics = null;
        }
    }

    /** Reports the effective global bStats setting without exposing the server UUID. */
    private void logMetricsConfiguration(int pluginId) {
        if (!DebugLog.isEnabled()) {
            return;
        }

        File bStatsConfigFile = new File(getDataFolder().getParentFile(), "bStats/config.yml");
        if (!bStatsConfigFile.isFile()) {
            DebugLog.debug("bStats configuration file was not found yet; using bStats defaults.");
            return;
        }

        YamlConfiguration bStatsConfig = YamlConfiguration.loadConfiguration(bStatsConfigFile);
        boolean enabled = bStatsConfig.getBoolean("enabled", true);
        DebugLog.debug("bStats reporting is " + (enabled ? "enabled" : "disabled")
                + " for plugin ID " + pluginId + ". "
                + "Initial submission is intentionally delayed by bStats.");

        if (bStatsConfig.getBoolean("logFailedRequests", false)) {
            DebugLog.debug("bStats is configured to log failed requests.");
        }
        if (bStatsConfig.getBoolean("logResponseStatusText", false)) {
            DebugLog.debug("bStats is configured to log response status text.");
        }
    }

    /** Stops bStats when the plugin is disabled. */
    private void shutdownMetrics() {
        if (metrics == null) {
            return;
        }

        try {
            metrics.shutdown();
            DebugLog.debug("bStats metrics scheduler shut down");
        } catch (RuntimeException ex) {
            DebugLog.error("Could not shut down bStats metrics cleanly.", ex);
        } finally {
            metrics = null;
        }
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
