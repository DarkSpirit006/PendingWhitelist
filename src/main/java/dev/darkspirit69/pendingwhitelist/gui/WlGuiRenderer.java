package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Renders whitelist administration inventories from prepared GUI data. */
final class WlGuiRenderer {

    private static final int GUI_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 46;
    private static final int NEXT_SLOT = 47;
    private static final int BACK_SLOT = 49;

    private final PendingWhitelistPlugin plugin;

    WlGuiRenderer(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    Inventory createMainInventory(WlGui gui) {
        DebugLog.debug("Rendering MAIN GUI");
        Inventory result = Bukkit.createInventory(gui, 27, text("&bPendingWhitelist &7• Dashboard"));
        result.setItem(11, item(Material.CHEST, "&bAdd Players",
                "&7Review pending requests and add",
                "&7known players to the whitelist."));
        result.setItem(13, item(Material.EMERALD, "&bWhitelisted Players",
                "&7View and remove whitelisted players."));
        result.setItem(15, item(Material.COMPARATOR, "&bConfigure",
                "&7Manage plugin settings."));
        result.setItem(22, item(Material.BARRIER, "&cClose",
                "&7Close this menu."));
        return result;
    }

    Inventory createAddInventory(WlGui gui) {
        DebugLog.debug("Rendering ADD GUI: page=" + gui.getPage());
        List<WlGui.AddCandidate> candidates = gui.getAddCandidates();
        List<WlGui.AddCandidate> layout = gui.getAddLayout();
        Inventory result = Bukkit.createInventory(gui, GUI_SIZE,
                text("&bPendingWhitelist &7• Add Players &f(" + candidates.size() + ")"));
        int start = (gui.getPage() - 1) * WlGui.PLAYER_SLOTS;
        int end = Math.min(start + WlGui.PLAYER_SLOTS, layout.size());
        List<WlGui.AddCandidate> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WlGui.AddCandidate candidate = layout.get(index);
            if (candidate == null) {
                continue;
            }
            int slot = index - start;
            result.setItem(slot, addPlayerItem(candidate));
            visible.add(candidate);
        }
        SkinHeadUtil.prefetch(plugin, gui, visible);
        if (candidates.isEmpty()) {
            result.setItem(22, item(Material.LIME_WOOL, "&aEveryone is whitelisted",
                    "&7There are no known non-whitelisted players."));
        }
        addNavigation(result, gui.getPage(), gui.getAddPageCount());
        return result;
    }

    Inventory createWhitelistedInventory(WlGui gui) {
        DebugLog.debug("Rendering WHITELISTED GUI: page=" + gui.getPage());
        List<WlGui.WhitelistEntry> layout = gui.getWhitelistLayout();
        int entryCount = gui.getWhitelistEntries().size();
        Inventory result = Bukkit.createInventory(gui, GUI_SIZE,
                text("&bPendingWhitelist &7• Whitelisted Players &f(" + entryCount + ")"));
        int start = (gui.getPage() - 1) * WlGui.PLAYER_SLOTS;
        int end = Math.min(start + WlGui.PLAYER_SLOTS, layout.size());
        List<WlGui.WhitelistEntry> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WlGui.WhitelistEntry entry = layout.get(index);
            if (entry == null) {
                continue;
            }
            String type = FloodgateUtil.isFloodgateId(entry.player().getUniqueId())
                    ? "&bBedrock"
                    : "&fJava";
            result.setItem(index - start, playerHeadNamed(entry.player(), entry.name(),
                    "&7Status: &aWhitelisted", "&7Type: " + type,
                    "&7UUID: &f" + entry.player().getUniqueId(),
                    "&7Left-click: &7Remove from whitelist",
                    "&7Shift-left-click: &7Remove this page"));
            visible.add(entry);
        }
        SkinHeadUtil.prefetchWhitelisted(plugin, gui, visible);
        if (entryCount == 0) {
            result.setItem(22, item(Material.LIME_WOOL, "&aWhitelist is empty",
                    "&7No players are currently whitelisted."));
        }
        addNavigation(result, gui.getPage(), gui.getWhitelistedPageCount());
        return result;
    }

    Inventory createConfigInventory(WlGui gui) {
        DebugLog.debug("Rendering CONFIG GUI");
        Inventory result = Bukkit.createInventory(gui, 27, text("&bPendingWhitelist &7• Configuration"));
        boolean purgeEnabled = plugin.isPurgeEnabled();
        int purgeDays = plugin.getPurgeDays();
        int pageSize = plugin.getConfiguredPageSize();
        boolean joinNotifications = plugin.isJoinAttemptNotificationsEnabled();
        boolean debugEnabled = plugin.isDebugLoggingEnabled();
        boolean whitelistEnabled = plugin.getServer().hasWhitelist();

        // Four centered controls on each of the two content rows.
        result.setItem(10, item(purgeEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                purgeEnabled ? "&aAutomatic Purge: ON" : "&cAutomatic Purge: OFF",
                "&7Automatically remove old pending entries.",
                "&7Left-click: &fToggle automatic purge"));
        result.setItem(12, item(Material.CLOCK, "&bPurge Age: &f" + purgeDays + " days",
                "&7Pending entries older than this can be purged.",
                "&7Left-click: &fIncrease by 5 days",
                "&7Right-click: &fDecrease by 5 days"));
        result.setItem(14, item(Material.BOOK, "&bPage Size: &f" + pageSize,
                "&7Players shown per GUI page.",
                "&7Left-click: &fIncrease by 1",
                "&7Right-click: &fDecrease by 1"));
        result.setItem(16, item(joinNotifications ? Material.LIME_DYE : Material.GRAY_DYE,
                joinNotifications ? "&aJoin Notifications: ON" : "&7Join Notifications: OFF",
                "&7Notify staff when an unwhitelisted player attempts to join.",
                "&7Left-click: &fToggle join notifications"));

        result.setItem(19, item(debugEnabled ? Material.REDSTONE_TORCH : Material.LEVER,
                debugEnabled ? "&aDebug Logging: ON" : "&7Debug Logging: OFF",
                "&7Enable detailed diagnostic logs in the server console.",
                "&7Left-click: &fToggle debug logging"));
        result.setItem(21, item(whitelistEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                whitelistEnabled ? "&aWhitelist: ON" : "&cWhitelist: OFF",
                whitelistEnabled ? "&7The server whitelist is enabled." : "&7The server whitelist is disabled.",
                "&7Left-click: &fToggle server whitelist"));
        result.setItem(23, item(Material.COMPARATOR, "&bReload",
                "&7Reload PendingWhitelist configuration safely.",
                "&7The plugin instance stays loaded.",
                "&7Left-click: &fReload configuration"));

        List<String> versionLore = gui.getVersionTooltipLines();
        result.setItem(25, item(Material.PAPER, "&bVersion Check", versionLore.toArray(new String[0])));
        result.setItem(22, item(Material.BARRIER, "&cBack", "&7Return to dashboard."));
        return result;
    }

    private void addNavigation(Inventory result, int currentPage, int totalPages) {
        result.setItem(PREVIOUS_SLOT, null);
        result.setItem(PAGE_SLOT, null);
        result.setItem(NEXT_SLOT, null);
        if (totalPages > 1) {
            if (currentPage > 1) {
                result.setItem(PREVIOUS_SLOT, item(Material.ARROW, "&bPrevious Page",
                        "&7Go to page " + (currentPage - 1) + "."));
            }
            result.setItem(PAGE_SLOT, item(Material.PAPER,
                    "&7Page &f" + currentPage + "&7/&f" + totalPages));
            if (currentPage < totalPages) {
                result.setItem(NEXT_SLOT, item(Material.ARROW, "&bNext Page",
                        "&7Go to page " + (currentPage + 1) + "."));
            }
        }
        result.setItem(BACK_SLOT, item(Material.BARRIER, "&cBack", "&7Return to dashboard."));
    }

    private ItemStack addPlayerItem(WlGui.AddCandidate candidate) {
        String name = candidate.name() == null || candidate.name().isBlank() ? "unknown" : candidate.name();
        String status;
        if (candidate.pending()) {
            status = "&ePending request";
        } else if (candidate.online()) {
            status = "&aOnline";
        } else {
            status = "&7Offline";
        }
        String type = candidate.bedrock() ? "&bBedrock" : "&fJava";
        if (candidate.pending()) {
            return playerHeadNamed(candidate.player(), name, "&7Status: " + status,
                    "&7Type: " + type, "&7UUID: &f" + candidate.player().getUniqueId(),
                    "&7Left-click: &7Add to whitelist",
                    "&7Shift-left-click: &7Add this page",
                    "&7Right-click: &7Remove from pending");
        }
        return playerHeadNamed(candidate.player(), name, "&7Status: " + status,
                "&7Type: " + type, "&7UUID: &f" + candidate.player().getUniqueId(),
                "&7Left-click: &7Add to whitelist",
                "&7Shift-left-click: &7Add this page");
    }

    private ItemStack playerHeadNamed(OfflinePlayer player, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        SkinHeadUtil.applyProfile(meta, player, name);
        meta.displayName(text("&f" + name));
        setLore(meta, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void setLore(ItemMeta meta, String... lore) {
        List<Component> components = new ArrayList<>(lore.length);
        for (String line : lore) {
            components.add(text(line));
        }
        meta.lore(components);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name));
        setLore(meta, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String input) {
        return TextUtil.component(input);
    }
}
