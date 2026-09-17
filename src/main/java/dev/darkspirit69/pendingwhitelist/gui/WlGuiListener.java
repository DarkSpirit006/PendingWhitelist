package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.List;
import java.util.UUID;

/** Handles clicks and navigation for the plugin GUI. */
public final class WlGuiListener implements Listener {

    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 47;
    private static final int BACK_SLOT = 49;
    private static final int CONFIG_BACK_SLOT = 22;
    private static final int CONFIG_VERSION_SLOT = 25;

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository pendingStorage;

    public WlGuiListener(PendingWhitelistPlugin plugin, PendingRepository pendingStorage) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof WlGui gui)
                || !plugin.isGuiViewer(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        DebugLog.debug("Inventory click: player=" + player.getName()
                + ", slot=" + event.getRawSlot() + ", click=" + event.getClick());
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        handleClick(player, gui, slot, event.isRightClick(), event.isShiftClick());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof WlGui gui
                && event.getPlayer() instanceof Player player) {
            if (gui.onClose(player.getUniqueId(), event.getInventory())) {
                DebugLog.debug("GUI closed: player=" + player.getName() + ", view=" + gui.getView());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof WlGui)
                || !plugin.isGuiViewer(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        DebugLog.debug("Inventory drag: player=" + player.getName());
    }

    private void handleClick(Player player, WlGui gui, int slot, boolean rightClick, boolean shiftClick) {
        switch (gui.getView()) {
            case MAIN -> handleMain(player, gui, slot);
            case ADD -> handleAdd(player, gui, slot, rightClick, shiftClick);
            case WHITELISTED -> handleWhitelisted(player, gui, slot, rightClick, shiftClick);
            case CONFIG -> handleConfig(player, gui, slot, rightClick);
        }
    }

    private void handleMain(Player player, WlGui gui, int slot) {
        switch (slot) {
            case 11 -> {
                SoundUtil.click(player);
                gui.openAdd(player);
            }
            case 13 -> {
                SoundUtil.click(player);
                gui.openWhitelisted(player, false);
            }
            case 15 -> {
                SoundUtil.click(player);
                gui.openConfig(player);
            }
            case 22 -> {
                SoundUtil.click(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private void handleAdd(Player player, WlGui gui, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == BACK_SLOT) {
            SoundUtil.click(player);
            gui.openMain(player);
            return;
        }
        if (slot == PREVIOUS_SLOT && gui.getPage() > 1) {
            SoundUtil.click(player);
            gui.openAddPage(player, gui.getPage() - 1);
            return;
        }
        if (slot == NEXT_SLOT && gui.getPage() < gui.getAddPageCount()) {
            SoundUtil.click(player);
            gui.openAddPage(player, gui.getPage() + 1);
            return;
        }
        if (slot < 0 || slot >= 36) {
            return;
        }
        WlGui.AddCandidate candidate = gui.getAddCandidateAtSlot(slot);
        if (candidate == null) {
            return;
        }
        if (rightClick) {
            if (candidate.pending()) {
                removePending(player, candidate);
                gui.invalidateAddCandidates();
                gui.openAddPage(player, Math.min(gui.getPage(), gui.getAddPageCount()));
            }
            return;
        }
        if (shiftClick) {
            bulkAdd(player, gui);
            gui.invalidateAddCandidates();
            gui.openAddPage(player, Math.min(gui.getPage(), gui.getAddPageCount()));
            return;
        }
        addPlayer(player, candidate);
        gui.invalidateAddCandidates();
        gui.openAddPage(player, Math.min(gui.getPage(), gui.getAddPageCount()));
    }

    private void removePending(Player player, WlGui.AddCandidate candidate) {
        UUID uuid = candidate.player().getUniqueId();
        boolean removed = pendingStorage.removePendingOnly(uuid.toString());
        if (removed) {
            SoundUtil.success(player);
            TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Removed " + MessageStyle.VALUE_LEGACY
                    + candidate.name() + " " + MessageStyle.SECONDARY_LEGACY + "from pending players.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.ERROR_LEGACY + "Could not remove " + MessageStyle.VALUE_LEGACY
                    + candidate.name() + " " + MessageStyle.ERROR_LEGACY + "from pending players.");
        }
    }

    private void addPlayer(Player player, WlGui.AddCandidate candidate) {
        String name = candidate.name();
        if (name == null || name.isBlank()) {
            name = candidate.player() != null ? candidate.player().getName() : null;
        }
        if (name == null || name.isBlank()) {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.ERROR_LEGACY + "Could not add player: no username is known.");
            return;
        }
        if (candidate.player() != null && pendingStorage.addToWhitelist(candidate.player().getUniqueId(), name)) {
            removePendingAfterWhitelistAdd(candidate, name);
            SoundUtil.success(player);
            TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Added " + MessageStyle.VALUE_LEGACY
                    + name + " " + MessageStyle.SECONDARY_LEGACY + "to the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.ERROR_LEGACY + "Could not add " + MessageStyle.VALUE_LEGACY
                    + name + " " + MessageStyle.ERROR_LEGACY + "to the whitelist.");
        }
    }

    private void removePendingAfterWhitelistAdd(WlGui.AddCandidate candidate, String fallbackName) {
        if (candidate == null || candidate.player() == null) {
            return;
        }
        UUID candidateUuid = candidate.player().getUniqueId();
        if (candidateUuid != null && pendingStorage.removePendingOnly(candidateUuid.toString())) {
            return;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            pendingStorage.removePendingOnly(fallbackName);
        }
    }

    private String playerWord(int count) {
        return count == 1 ? "player" : "players";
    }

    private void bulkAdd(Player player, WlGui gui) {
        int changed = 0;
        for (WlGui.AddCandidate candidate : gui.getVisibleAddCandidates()) {
            String name = candidate.name();
            if ((name == null || name.isBlank()) && candidate.player() != null) {
                name = candidate.player().getName();
            }
            if (name != null && !name.isBlank() && candidate.player() != null
                    && pendingStorage.addToWhitelist(candidate.player().getUniqueId(), name)) {
                removePendingAfterWhitelistAdd(candidate, name);
                changed++;
            }
        }
        if (changed > 0) {
            SoundUtil.success(player);
            TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Added " + MessageStyle.VALUE_LEGACY
                    + changed + " " + MessageStyle.SECONDARY_LEGACY + playerWord(changed)
                    + " to the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.WARNING_LEGACY + "No players were added to the whitelist.");
        }
    }

    private void handleWhitelisted(Player player, WlGui gui, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == BACK_SLOT) {
            SoundUtil.click(player);
            gui.openMain(player);
            return;
        }
        if (slot == PREVIOUS_SLOT && gui.getPage() > 1) {
            SoundUtil.click(player);
            gui.openWhitelistedPage(player, gui.getPage() - 1);
            return;
        }
        if (slot == NEXT_SLOT && gui.getPage() < gui.getWhitelistedPageCount()) {
            SoundUtil.click(player);
            gui.openWhitelistedPage(player, gui.getPage() + 1);
            return;
        }
        if (slot < 0 || slot >= WlGui.PLAYER_SLOTS) {
            return;
        }
        if (shiftClick) {
            bulkWhitelistRemove(player, gui);
            gui.openWhitelistedPage(player, Math.min(gui.getPage(), gui.getWhitelistedPageCount()));
            return;
        }
        WlGui.WhitelistEntry entry = gui.getWhitelistedEntryAtSlot(slot);
        if (entry == null) {
            return;
        }
        String name = entry.name();
        OfflinePlayer target = entry.player();
        if (target.isWhitelisted()) {
            target.setWhitelisted(false);
            SoundUtil.success(player);
            TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Removed " + MessageStyle.VALUE_LEGACY
                    + name + " " + MessageStyle.SECONDARY_LEGACY + "from the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.ERROR_LEGACY + "Could not remove " + MessageStyle.VALUE_LEGACY
                    + name + " " + MessageStyle.ERROR_LEGACY + "from the whitelist.");
        }
        gui.openWhitelistedPage(player, Math.min(gui.getPage(), gui.getWhitelistedPageCount()));
    }

    private void bulkWhitelistRemove(Player player, WlGui gui) {
        List<WlGui.WhitelistEntry> entries = gui.getVisibleWhitelistedEntries();
        int changed = 0;
        for (WlGui.WhitelistEntry entry : entries) {
            OfflinePlayer target = entry.player();
            if (target.isWhitelisted()) {
                target.setWhitelisted(false);
                changed++;
            }
        }
        if (changed > 0) {
            SoundUtil.success(player);
            TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Removed " + MessageStyle.VALUE_LEGACY
                    + changed + " " + MessageStyle.SECONDARY_LEGACY + playerWord(changed)
                    + " from the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.WARNING_LEGACY + "No players were removed from the whitelist.");
        }
    }

    private void handleConfig(Player player, WlGui gui, int slot, boolean rightClick) {
        switch (slot) {
            case 10 -> togglePurge(player);
            case 12 -> updatePurgeDays(player, rightClick ? -5 : 5);
            case 14 -> updatePageSize(player, rightClick ? -1 : 1);
            case 16 -> toggleJoinNotifications(player);
            case 19 -> toggleDebugLogging(player);
            case 21 -> toggleWhitelist(player);
            case 23 -> reloadConfiguration(player, gui);
            case CONFIG_VERSION_SLOT -> checkVersion(player, gui);
            case CONFIG_BACK_SLOT -> {
                SoundUtil.click(player);
                gui.openMain(player);
                return;
            }
            default -> {
            }
        }
        if (slot != 23 && slot != CONFIG_VERSION_SLOT) {
            gui.openConfig(player);
        }
    }

    private void toggleWhitelist(Player player) {
        boolean enabled = !plugin.getServer().hasWhitelist();
        plugin.getServer().setWhitelist(enabled);
        SoundUtil.success(player);
        TextUtil.send(player, (enabled ? MessageStyle.SUCCESS_LEGACY : MessageStyle.WARNING_LEGACY)
                + (enabled ? "Whitelist enabled." : "Whitelist disabled."));
    }

    private void reloadConfiguration(Player player, WlGui gui) {
        SoundUtil.click(player);
        if (!plugin.reloadConfiguration()) {
            SoundUtil.failure(player);
            TextUtil.send(player, MessageStyle.ERROR_LEGACY + "Could not reload configuration. "
                    + "Check the server console.");
            return;
        }
        SoundUtil.success(player);
        TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Configuration reloaded.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                gui.openConfig(player);
            }
        });
    }

    private void checkVersion(Player player, WlGui gui) {
        SoundUtil.click(player);
        gui.startVersionCheck();
        gui.openConfig(player);
        plugin.getUpdateNotifier().checkForGui(player, result -> {
            gui.finishVersionCheck(result);
            if (player.isOnline() && gui.getView() == WlGui.View.CONFIG) {
                gui.openConfig(player);
            }
        });
    }

    private void toggleDebugLogging(Player player) {
        boolean value = !plugin.isDebugLoggingEnabled();
        plugin.getConfig().set("logging.debug", value);
        plugin.saveConfig();
        plugin.refreshDebugLogging();
        SoundUtil.success(player);
        TextUtil.send(player, (value ? MessageStyle.SUCCESS_LEGACY : MessageStyle.WARNING_LEGACY)
                + (value ? "Debug logging enabled." : "Debug logging disabled."));
    }

    private void toggleJoinNotifications(Player player) {
        boolean value = !plugin.isJoinAttemptNotificationsEnabled();
        plugin.getConfig().set("notifications.join-attempts", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, (value ? MessageStyle.SUCCESS_LEGACY : MessageStyle.WARNING_LEGACY)
                + (value ? "Join attempt notifications enabled." : "Join attempt notifications disabled."));
    }

    private void togglePurge(Player player) {
        boolean value = !plugin.isPurgeEnabled();
        plugin.getConfig().set("purge.enabled", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, (value ? MessageStyle.SUCCESS_LEGACY : MessageStyle.WARNING_LEGACY)
                + (value ? "Automatic purge enabled." : "Automatic purge disabled."));
    }

    private void updatePurgeDays(Player player, int delta) {
        int value = Math.max(1, Math.min(3650, plugin.getPurgeDays() + delta));
        plugin.getConfig().set("purge.days", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Purge age set to " + MessageStyle.VALUE_LEGACY
                + value + MessageStyle.SECONDARY_LEGACY + " days.");
    }

    private void updatePageSize(Player player, int delta) {
        int value = Math.max(1, Math.min(45, plugin.getConfiguredPageSize() + delta));
        plugin.getConfig().set("page-size", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, MessageStyle.SUCCESS_LEGACY + "Page size set to " + MessageStyle.VALUE_LEGACY
                + value + MessageStyle.SECONDARY_LEGACY + ".");
    }

}
