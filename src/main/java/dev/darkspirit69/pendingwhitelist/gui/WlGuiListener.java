package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;
import java.util.UUID;

/** Handles clicks and navigation for the PendingWhitelist inventories. */
public final class WlGuiListener implements Listener {

    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 47;
    private static final int BACK_SLOT = 49;

    private final PendingWhitelistPlugin plugin;
    private final PendingStorage pendingStorage;

    public WlGuiListener(PendingWhitelistPlugin plugin, PendingStorage pendingStorage) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof WlGui gui)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        handleClick(player, gui, slot, event.isRightClick(), event.isShiftClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WlGui) {
            event.setCancelled(true);
        }
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
            TextUtil.send(player, "&aRemoved &f" + candidate.name() + " &afrom pending players.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, "&cCould not remove &f" + candidate.name() + "&c from pending players.");
        }
    }

    private void addPlayer(Player player, WlGui.AddCandidate candidate) {
        String name = candidate.name();
        if (name == null || name.isBlank()) {
            name = candidate.player().getName();
        }
        if (name == null || name.isBlank()) {
            SoundUtil.failure(player);
            TextUtil.send(player, "&cThis player has no known username.");
            return;
        }
        if (candidate.player() != null && pendingStorage.addToWhitelist(candidate.player().getUniqueId(), name)) {
            pendingStorage.removePendingOnly(name);
            SoundUtil.success(player);
            TextUtil.send(player, "&aAdded &f" + name + " &7to the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, "&cCould not add &f" + name + "&c to the whitelist.");
        }
    }

    private void bulkAdd(Player player, WlGui gui) {
        int changed = 0;
        for (int slot = 0; slot < 36; slot++) {
            WlGui.AddCandidate candidate = gui.getAddCandidateAtSlot(slot);
            if (candidate == null) {
                continue;
            }
            String name = candidate.name();
            if ((name == null || name.isBlank()) && candidate.player() != null) {
                name = candidate.player().getName();
            }
            if (name != null && !name.isBlank() && candidate.player() != null
                    && pendingStorage.addToWhitelist(candidate.player().getUniqueId(), name)) {
                pendingStorage.removePendingOnly(name);
                changed++;
            }
        }
        if (changed > 0) {
            SoundUtil.success(player);
            TextUtil.send(player, "&aAdded &f" + changed + " &7player(s) to the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, "&eNo players were added.");
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
            TextUtil.send(player, "&aRemoved &f" + name + " &7from the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, "&c" + name + " &7is no longer whitelisted.");
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
            TextUtil.send(player, "&aRemoved &f" + changed + " &7player(s) from the whitelist.");
        } else {
            SoundUtil.failure(player);
            TextUtil.send(player, "&eNo whitelisted players were changed.");
        }
    }

    private void handleConfig(Player player, WlGui gui, int slot, boolean rightClick) {
        switch (slot) {
            case 11 -> togglePurge(player);
            case 13 -> updatePurgeDays(player, rightClick ? -5 : 5);
            case 15 -> updatePageSize(player, rightClick ? -1 : 1);
            case 22 -> {
                SoundUtil.click(player);
                gui.openMain(player);
                return;
            }
            default -> {
            }
        }
        gui.openConfig(player);
    }

    private void togglePurge(Player player) {
        boolean value = !plugin.isPurgeEnabled();
        plugin.getConfig().set("purge.enabled", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, "&aSaved &fpurge.enabled: &f" + value);
    }

    private void updatePurgeDays(Player player, int delta) {
        int value = Math.max(1, Math.min(3650, plugin.getPurgeDays() + delta));
        plugin.getConfig().set("purge.days", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, "&aSaved &fpurge.days: &f" + value);
    }

    private void updatePageSize(Player player, int delta) {
        int value = Math.max(1, Math.min(45, plugin.getConfiguredPageSize() + delta));
        plugin.getConfig().set("page-size", value);
        plugin.saveConfig();
        SoundUtil.success(player);
        TextUtil.send(player, "&aSaved &fpage-size: &f" + value);
    }


}
