package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds the admin screens and keeps their player-slot layout consistent across views. */
public final class WlGui implements InventoryHolder {

    public enum View {
        MAIN,
        ADD,
        WHITELISTED,
        CONFIG
    }

    public record AddCandidate(OfflinePlayer player, String name, boolean pending, boolean bedrock) {
    }

    public record WhitelistEntry(OfflinePlayer player, String name) {
    }

    public static final int PLAYER_SLOTS = 36;
    private static final int GUI_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 46;
    private static final int NEXT_SLOT = 47;
    private static final int BACK_SLOT = 49;

    private final PendingWhitelistPlugin plugin;
    private final PendingStorage pendingStorage;
    private final UpdateNotifier updateNotifier;
    private View view = View.MAIN;
    private int page = 1;
    private Inventory inventory;
    private List<AddCandidate> addCandidatesCache;
    private List<AddCandidate> addLayoutCache;

    public WlGui(PendingWhitelistPlugin plugin, PendingStorage pendingStorage, UpdateNotifier updateNotifier) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
        this.updateNotifier = updateNotifier;
    }

    public void openMain(Player player) {
        view = View.MAIN;
        page = 1;
        open(player, createMainInventory());
    }

    public void openAdd(Player player) {
        invalidateAddCandidates();
        view = View.ADD;
        page = 1;
        open(player, createAddInventory());
    }

    public void openAddPage(Player player, int requestedPage) {
        view = View.ADD;
        page = clampPage(requestedPage, getAddPageCount());
        open(player, createAddInventory());
    }

    public void openWhitelisted(Player player, boolean removeMode) {
        view = View.WHITELISTED;
        page = 1;
        open(player, createWhitelistedInventory());
    }

    public void openWhitelistedPage(Player player, int requestedPage) {
        view = View.WHITELISTED;
        page = clampPage(requestedPage, getWhitelistedPageCount());
        open(player, createWhitelistedInventory());
    }

    public void openConfig(Player player) {
        view = View.CONFIG;
        page = 1;
        open(player, createConfigInventory());
    }

    public View getView() {
        return view;
    }

    public int getPage() {
        return page;
    }

    public List<PendingEntry> getPendingEntries() {
        return pendingStorage.getPendingEntriesSortedByRecencyDesc();
    }

    public int getPendingPageCount() {
        return pageCount(getPendingEntries().size());
    }

    public List<WhitelistEntry> getWhitelistEntries() {
        Map<UUID, String> storedNames = readStoredWhitelistNames();
        List<WhitelistEntry> entries = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            String name = normalizeDisplayName(player.getName());
            if (name == null) {
                name = pendingStorage.getKnownWhitelistName(player.getUniqueId());
            }
            if (name == null) {
                name = storedNames.get(player.getUniqueId());
            }
            if (name == null) {
                name = player.getUniqueId().toString();
            }
            if (name.length() <= 64 && !name.equals(player.getUniqueId().toString())) {
                pendingStorage.rememberWhitelistName(player.getUniqueId(), name);
                pendingStorage.repairWhitelistJsonName(player.getUniqueId(), name);
            }
            entries.add(new WhitelistEntry(player, name));
        }
        entries.sort(Comparator.comparing(entry -> nonNullSortKey(entry.name()), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private List<WhitelistEntry> getWhitelistLayout() {
        List<WhitelistEntry> bedrock = new ArrayList<>();
        List<WhitelistEntry> java = new ArrayList<>();
        for (WhitelistEntry entry : getWhitelistEntries()) {
            if (FloodgateUtil.isFloodgateId(entry.player().getUniqueId())) {
                bedrock.add(entry);
            } else {
                java.add(entry);
            }
        }
        Comparator<WhitelistEntry> comparator =
                Comparator.comparing(entry -> nonNullSortKey(entry.name()), String.CASE_INSENSITIVE_ORDER);
        bedrock.sort(comparator);
        java.sort(comparator);
        List<WhitelistEntry> layout = new ArrayList<>();
        addWhitelistGroupToLayout(layout, bedrock);
        addWhitelistGroupToLayout(layout, java);
        return layout;
    }

    private void addWhitelistGroupToLayout(List<WhitelistEntry> layout,
            List<WhitelistEntry> group) {
        if (group.isEmpty()) {
            return;
        }
        while (!layout.isEmpty() && layout.size() % 9 != 0) {
            layout.add(null);
        }
        layout.addAll(group);
    }

    public List<OfflinePlayer> getWhitelistedPlayers() {
        List<OfflinePlayer> players = new ArrayList<>();
        for (WhitelistEntry entry : getWhitelistEntries()) {
            players.add(entry.player());
        }
        return players;
    }

    public List<String> getWhitelistedNames() {
        List<String> names = new ArrayList<>();
        for (WhitelistEntry entry : getWhitelistEntries()) {
            names.add(entry.name());
        }
        return names;
    }

    public WhitelistEntry getWhitelistedEntryAtSlot(int slot) {
        if (slot < 0 || slot >= PLAYER_SLOTS) {
            return null;
        }
        List<WhitelistEntry> layout = getWhitelistLayout();
        int index = (page - 1) * PLAYER_SLOTS + slot;
        return index >= 0 && index < layout.size() ? layout.get(index) : null;
    }

    public int getWhitelistedPageCount() {
        return pageCount(getWhitelistLayout().size());
    }

    public int findWhitelistedEntrySlot(WhitelistEntry entry) {
        if (entry == null) {
            return -1;
        }
        List<WhitelistEntry> layout = getWhitelistLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        for (int index = start; index < end; index++) {
            WhitelistEntry current = layout.get(index);
            if (entry.equals(current)) {
                return index - start;
            }
        }
        return -1;
    }

    public List<WhitelistEntry> getVisibleWhitelistedEntries() {
        List<WhitelistEntry> layout = getWhitelistLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        List<WhitelistEntry> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WhitelistEntry entry = layout.get(index);
            if (entry != null) {
                visible.add(entry);
            }
        }
        return visible;
    }

    public List<AddCandidate> getAddCandidates() {
        if (addCandidatesCache != null) {
            return addCandidatesCache;
        }

        List<AddCandidate> pendingBedrock = new ArrayList<>();
        List<AddCandidate> pendingJava = new ArrayList<>();
        List<AddCandidate> otherBedrock = new ArrayList<>();
        List<AddCandidate> otherJava = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Set<UUID> whitelistedUuids = new HashSet<>();
        Set<String> whitelistedNames = new HashSet<>();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            whitelistedUuids.add(player.getUniqueId());
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                whitelistedNames.add(name.toLowerCase(java.util.Locale.ROOT));
            }
        }

        for (PendingEntry entry : getPendingEntries()) {
            UUID uuid = parseUuid(entry.uuid());
            String name = entry.name();
            if (name == null || name.isBlank()) {
                name = entry.displayName();
            }
            if (name == null || name.isBlank() || "unknown".equalsIgnoreCase(name)
                    || isWhitelisted(uuid, name, whitelistedUuids, whitelistedNames)) {
                continue;
            }
            OfflinePlayer player = uuid == null ? Bukkit.getOfflinePlayer(name) : Bukkit.getOfflinePlayer(uuid);
            boolean bedrock = uuid != null && dev.darkspirit69.pendingwhitelist.util.FloodgateUtil.isFloodgateId(uuid);
            AddCandidate candidate = new AddCandidate(player, name, true, bedrock);
            if (uuid != null) {
                seen.add(uuid);
            }
            if (bedrock) {
                pendingBedrock.add(candidate);
            } else {
                pendingJava.add(candidate);
            }
        }

        List<OfflinePlayer> others = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            if (name == null || name.isBlank() || whitelistedUuids.contains(uuid)
                    || whitelistedNames.contains(name.toLowerCase(java.util.Locale.ROOT)) || !seen.add(uuid)) {
                continue;
            }
            others.add(player);
        }
        others.sort(Comparator.comparing(player -> nonNullSortKey(player.getName()),
                String.CASE_INSENSITIVE_ORDER));
        for (OfflinePlayer player : others) {
            AddCandidate candidate = new AddCandidate(
                    player, player.getName(), false,
                    dev.darkspirit69.pendingwhitelist.util.FloodgateUtil.isFloodgateId(player.getUniqueId()));
            if (candidate.bedrock()) {
                otherBedrock.add(candidate);
            } else {
                otherJava.add(candidate);
            }
        }

        Comparator<AddCandidate> candidateComparator =
                Comparator.comparing(candidate -> nonNullSortKey(candidate.name()),
                        String.CASE_INSENSITIVE_ORDER);
        pendingBedrock.sort(candidateComparator);
        pendingJava.sort(candidateComparator);
        otherBedrock.sort(candidateComparator);
        otherJava.sort(candidateComparator);

        addCandidatesCache = new ArrayList<>();
        addCandidatesCache.addAll(pendingBedrock);
        addCandidatesCache.addAll(pendingJava);
        addCandidatesCache.addAll(otherBedrock);
        addCandidatesCache.addAll(otherJava);
        return addCandidatesCache;
    }

    private List<AddCandidate> getAddLayout() {
        if (addLayoutCache != null) {
            return addLayoutCache;
        }
        List<AddCandidate> candidates = getAddCandidates();
        List<AddCandidate> layout = new ArrayList<>();
        addGroupToLayout(layout, candidates, true, true);
        addGroupToLayout(layout, candidates, true, false);
        addGroupToLayout(layout, candidates, false, true);
        addGroupToLayout(layout, candidates, false, false);
        addLayoutCache = layout;
        return layout;
    }

    private void addGroupToLayout(List<AddCandidate> layout, List<AddCandidate> candidates,
            boolean pending, boolean bedrock) {
        List<AddCandidate> group = new ArrayList<>();
        for (AddCandidate candidate : candidates) {
            if (candidate.pending() == pending && candidate.bedrock() == bedrock) {
                group.add(candidate);
            }
        }
        if (group.isEmpty()) {
            return;
        }
        while (!layout.isEmpty() && layout.size() % 9 != 0) {
            layout.add(null);
        }
        layout.addAll(group);
    }

    public int findCandidateSlot(AddCandidate candidate) {
        if (candidate == null) {
            return -1;
        }
        List<AddCandidate> layout = getAddLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        for (int index = start; index < end; index++) {
            if (candidate.equals(layout.get(index))) {
                return index - start;
            }
        }
        return -1;
    }

    public AddCandidate getAddCandidateAtSlot(int slot) {
        int index = (page - 1) * PLAYER_SLOTS + slot;
        List<AddCandidate> layout = getAddLayout();
        if (slot < 0 || slot >= PLAYER_SLOTS || index >= layout.size()) {
            return null;
        }
        return layout.get(index);
    }

    public int getAddPageCount() {
        return pageCount(getAddLayout().size(), PLAYER_SLOTS);
    }

    public void invalidateAddCandidates() {
        addCandidatesCache = null;
        addLayoutCache = null;
    }

    private Inventory createMainInventory() {
        Inventory result = Bukkit.createInventory(this, 27, text("&8PendingWhitelist &6Dashboard"));
        result.setItem(11, item(Material.CHEST, "&eAdd Players",
                "&7Review pending requests and add",
                "&7known players to the whitelist."));
        result.setItem(13, item(Material.EMERALD, "&aWhitelisted Players",
                "&7View and remove whitelisted players."));
        result.setItem(15, item(Material.COMPARATOR, "&bConfigure",
                "&7Manage plugin settings."));
        result.setItem(22, item(Material.BARRIER, "&cClose",
                "&7Close this menu."));
        return result;
    }

    private Inventory createAddInventory() {
        List<AddCandidate> candidates = getAddCandidates();
        // Keep group boundaries intact so Java and Bedrock entries never share a row.
        List<AddCandidate> layout = getAddLayout();
        Inventory result = Bukkit.createInventory(this, GUI_SIZE,
                text("&8PendingWhitelist &6Add Players &7(" + candidates.size() + ")"));
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        List<AddCandidate> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            AddCandidate candidate = layout.get(index);
            if (candidate != null) {
                int slot = index - start;
                result.setItem(slot, addPlayerItem(candidate));
                visible.add(candidate);
            }
        }
        SkinHeadUtil.prefetch(plugin, this, visible);
        if (candidates.isEmpty()) {
            result.setItem(22, item(Material.LIME_WOOL, "&aEveryone is whitelisted",
                    "&7There are no known non-whitelisted players."));
        }
        // Navigation is only rendered when another page actually exists.
        addNavigation(result, page, getAddPageCount());
        return result;
    }

    private Inventory createWhitelistedInventory() {
        List<WhitelistEntry> entries = getWhitelistEntries();
        List<WhitelistEntry> layout = getWhitelistLayout();
        Inventory result = Bukkit.createInventory(this, GUI_SIZE,
                text("&8PendingWhitelist &6Whitelisted Players &7(" + entries.size() + ")"));
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        List<WhitelistEntry> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WhitelistEntry entry = layout.get(index);
            if (entry == null) {
                continue;
            }
            String type = FloodgateUtil.isFloodgateId(entry.player().getUniqueId())
                    ? "&bBedrock" : "&fJava";
            result.setItem(index - start, playerHeadNamed(entry.player(), entry.name(),
                    "&7Status: &aWhitelisted", "&7Type: " + type,
                    "&7UUID: &f" + entry.player().getUniqueId(),
                    "&aLeft-click: &7Remove from whitelist",
                    "&eShift-left-click: &7Remove this page"));
            visible.add(entry);
        }
        SkinHeadUtil.prefetchWhitelisted(plugin, this, visible);
        if (entries.isEmpty()) {
            result.setItem(22, item(Material.LIME_WOOL, "&aWhitelist is empty",
                    "&7No players are currently whitelisted."));
        }
        addNavigation(result, page, getWhitelistedPageCount());
        return result;
    }

    private Inventory createConfigInventory() {
        Inventory result = Bukkit.createInventory(this, 27, text("&8PendingWhitelist &6Configuration"));
        boolean purgeEnabled = plugin.isPurgeEnabled();
        int purgeDays = plugin.getPurgeDays();
        int pageSize = plugin.getConfiguredPageSize();

        result.setItem(11, item(purgeEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                purgeEnabled ? "&aAutomatic Purge: ON" : "&cAutomatic Purge: OFF",
                "&7Left-click: &fToggle automatic purge"));
        result.setItem(13, item(Material.CLOCK, "&ePurge Age: &f" + purgeDays + " days",
                "&7Left-click: &fIncrease by 5 days",
                "&7Right-click: &fDecrease by 5 days"));
        result.setItem(15, item(Material.BOOK, "&bPage Size: &f" + pageSize,
                "&7Left-click: &fIncrease by 1",
                "&7Right-click: &fDecrease by 1"));
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
                    "&ePage &f" + currentPage + "&7/&f" + totalPages));
            if (currentPage < totalPages) {
                result.setItem(NEXT_SLOT, item(Material.ARROW, "&bNext Page",
                        "&7Go to page " + (currentPage + 1) + "."));
            }
        }
        result.setItem(BACK_SLOT, item(Material.BARRIER, "&cBack", "&7Return to dashboard."));
    }

    private ItemStack addPlayerItem(AddCandidate candidate) {
        String name = candidate.name() == null || candidate.name().isBlank() ? "unknown" : candidate.name();
        String status = candidate.pending() ? "&6Pending request" : "&fNot whitelisted";
        String type = candidate.bedrock() ? "&bBedrock" : "&fJava";
        if (candidate.pending()) {
            return playerHeadNamed(candidate.player(), name, "&7Status: " + status,
                    "&7Type: " + type, "&7UUID: &f" + candidate.player().getUniqueId(),
                    "&aLeft-click: &7Add to whitelist",
                    "&eShift-left-click: &7Add this page",
                    "&cRight-click: &7Remove from pending");
        }
        return playerHeadNamed(candidate.player(), name, "&7Status: " + status,
                "&7Type: " + type, "&7UUID: &f" + candidate.player().getUniqueId(),
                "&aLeft-click: &7Add to whitelist",
                "&eShift-left-click: &7Add this page");
    }

    private ItemStack playerHeadNamed(OfflinePlayer player, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        SkinHeadUtil.applyProfile(meta, player, name);
        meta.displayName(text("&a" + name));
        setLore(meta, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isWhitelisted(UUID uuid, String name, Set<UUID> uuids, Set<String> names) {
        if (uuid != null && uuids.contains(uuid)) {
            return true;
        }
        return name != null && names.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private void setLore(ItemMeta meta, String... lore) {
        List<net.kyori.adventure.text.Component> components = new ArrayList<>();
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

    private int pageCount(int size) {
        return pageCount(size, PLAYER_SLOTS);
    }

    private int pageCount(int size, int slotsPerPage) {
        return Math.max(1, (size + slotsPerPage - 1) / slotsPerPage);
    }

    private int clampPage(int requestedPage, int totalPages) {
        return Math.max(1, Math.min(requestedPage, totalPages));
    }

    private String nonNullSortKey(String value) {
        return value == null ? "" : value;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<UUID, String> readStoredWhitelistNames() {
        Map<UUID, String> names = new LinkedHashMap<>();
        java.nio.file.Path file = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
        if (!java.nio.file.Files.isRegularFile(file)) {
            return names;
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(
                file, java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                return names;
            }
            for (com.google.gson.JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject object = element.getAsJsonObject();
                if (!object.has("uuid") || !object.has("name")) {
                    continue;
                }
                UUID uuid = parseUuid(object.get("uuid").getAsString());
                String name = normalizeDisplayName(object.get("name").getAsString());
                if (uuid != null && name != null) {
                    names.put(uuid, name);
                }
            }
        } catch (java.io.IOException | com.google.gson.JsonParseException |
                UnsupportedOperationException ignored) {
        }
        return names;
    }

    private net.kyori.adventure.text.Component text(String input) {
        return dev.darkspirit69.pendingwhitelist.util.TextUtil.component(input);
    }

    private void open(Player player, Inventory newInventory) {
        inventory = java.util.Objects.requireNonNull(newInventory);
        player.openInventory(inventory);
    }

    public List<OfflinePlayer> getVisibleWhitelistedPlayers() {
        List<OfflinePlayer> players = new ArrayList<>();
        for (WhitelistEntry entry : getVisibleWhitelistedEntries()) {
            players.add(entry.player());
        }
        return players;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public PendingWhitelistPlugin getPlugin() {
        return plugin;
    }

    public PendingStorage getPendingStorage() {
        return pendingStorage;
    }

    public UpdateNotifier getUpdateNotifier() {
        return updateNotifier;
    }
}
