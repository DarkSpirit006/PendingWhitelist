package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.update.UpdateResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates GUI state while delegating data preparation and inventory
 * rendering.
 */
public final class WlGui implements InventoryHolder {

    public enum View {
        MAIN, ADD, WHITELISTED, CONFIG
    }

    public record AddCandidate(OfflinePlayer player, String name, boolean pending, boolean bedrock, boolean online) {
    }

    public record WhitelistEntry(OfflinePlayer player, String name) {
    }

    public static final int PLAYER_SLOTS = 36;

    private final PendingWhitelistPlugin plugin;
    private final WlGuiData data;
    private final WlGuiRenderer renderer;
    private final Set<UUID> viewers = new HashSet<>();
    private View view = View.MAIN;
    private int page = 1;
    private Inventory inventory;
    private boolean versionCheckInProgress;
    private UpdateResult versionResult;

    public WlGui(PendingWhitelistPlugin plugin, PendingRepository repository) {
        this.plugin = plugin;
        this.data = new WlGuiData(plugin, repository);
        this.renderer = new WlGuiRenderer(plugin);
    }

    public void openMain(Player player) {
        DebugLog.debug("GUI open: MAIN, player=" + player.getName());
        view = View.MAIN;
        page = 1;
        open(player, renderer.createMainInventory(this));
    }

    public void openAdd(Player player) {
        DebugLog.debug("GUI open: ADD, player=" + player.getName());
        invalidateAddCandidates();
        view = View.ADD;
        page = 1;
        open(player, renderer.createAddInventory(this));
    }

    public void openAddPage(Player player, int requestedPage) {
        DebugLog.debug("GUI open: ADD page=" + requestedPage + ", player=" + player.getName());
        view = View.ADD;
        page = clampPage(requestedPage, getAddPageCount());
        open(player, renderer.createAddInventory(this));
    }

    public void openWhitelisted(Player player, boolean removeMode) {
        DebugLog.debug("GUI open: WHITELISTED, removeMode=" + removeMode + ", player=" + player.getName());
        data.invalidateWhitelistCache();
        view = View.WHITELISTED;
        page = 1;
        open(player, renderer.createWhitelistedInventory(this));
    }

    public void openWhitelistedPage(Player player, int requestedPage) {
        DebugLog.debug("GUI open: WHITELISTED page=" + requestedPage + ", player=" + player.getName());
        data.invalidateWhitelistCache();
        view = View.WHITELISTED;
        page = clampPage(requestedPage, getWhitelistedPageCount());
        open(player, renderer.createWhitelistedInventory(this));
    }

    public void openConfig(Player player) {
        DebugLog.debug("GUI open: CONFIG, player=" + player.getName());
        view = View.CONFIG;
        page = 1;
        open(player, renderer.createConfigInventory(this));
    }

    public View getView() {
        return view;
    }

    public void startVersionCheck() {
        versionCheckInProgress = true;
        versionResult = null;
    }

    public void finishVersionCheck(UpdateResult result) {
        versionCheckInProgress = false;
        versionResult = result;
    }

    public List<String> getVersionTooltipLines() {
        if (versionCheckInProgress) {
            return List.of("&7Checking Modrinth for the latest release...");
        }
        String installed = plugin.getInstalledVersion();
        if (versionResult == null) {
            return List.of("&7Installed: &f" + installed, "&7Click to check Modrinth.");
        }
        if (!versionResult.hasRelease()) {
            return List.of("&7Installed: &f" + installed, "&cLatest version could not be determined.",
                    "&7Left-click: &fCheck again");
        }
        String latest = versionResult.latestVersion();
        boolean update = plugin.getUpdateNotifier().isNewerVersion(latest, installed);
        if (update) {
            return List.of("&7Installed: &f" + installed, "&7Latest: &f" + latest,
                    "&eUpdate available.", "&7Left-click: &fCheck again");
        }
        return List.of("&7Installed: &f" + installed, "&7Latest: &f" + latest,
                "&aUp to date.", "&7Left-click: &fCheck again");
    }

    public int getPage() {
        return page;
    }

    public List<PendingEntry> getPendingEntries() {
        return data.getPendingEntries();
    }

    public int getPendingPageCount() {
        return data.getPendingPageCount();
    }

    public List<WhitelistEntry> getWhitelistEntries() {
        return data.getWhitelistEntries();
    }

    public List<OfflinePlayer> getWhitelistedPlayers() {
        return data.getWhitelistedPlayers();
    }

    public List<String> getWhitelistedNames() {
        return data.getWhitelistedNames();
    }

    public WhitelistEntry getWhitelistedEntryAtSlot(int slot) {
        return data.getWhitelistedEntryAtSlot(page, slot);
    }

    public int getWhitelistedPageCount() {
        return data.getWhitelistedPageCount();
    }

    public int findWhitelistedEntrySlot(WhitelistEntry entry) {
        return data.findWhitelistedEntrySlot(page, entry);
    }

    public List<WhitelistEntry> getVisibleWhitelistedEntries() {
        return data.getVisibleWhitelistedEntries(page);
    }

    public List<AddCandidate> getAddCandidates() {
        return data.getAddCandidates();
    }

    public int findCandidateSlot(AddCandidate candidate) {
        return data.findCandidateSlot(page, candidate);
    }

    public AddCandidate getAddCandidateAtSlot(int slot) {
        return data.getAddCandidateAtSlot(page, slot);
    }

    public int getAddPageCount() {
        return data.getAddPageCount();
    }

    boolean hasPendingAddCandidates() {
        return data.hasPendingCandidates();
    }

    boolean hasOnlineAddCandidates() {
        return data.hasOnlineCandidates();
    }

    boolean hasOfflineAddCandidates() {
        return data.hasOfflineCandidates();
    }

    public void invalidateAddCandidates() {
        DebugLog.debug("Invalidating Add GUI candidate cache");
        data.invalidateAddCandidates();
    }

    public List<OfflinePlayer> getVisibleWhitelistedPlayers() {
        return data.getVisibleWhitelistedPlayers(page);
    }

    List<AddCandidate> getAddLayout() {
        return data.getAddLayout();
    }

    List<WhitelistEntry> getWhitelistLayout() {
        return data.getWhitelistLayout();
    }

    private int clampPage(int requestedPage, int totalPages) {
        return Math.max(1, Math.min(requestedPage, totalPages));
    }

    private void open(Player player, Inventory newInventory) {
        inventory = newInventory;
        viewers.add(player.getUniqueId());
        plugin.trackGuiViewer(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    boolean onClose(UUID playerId, Inventory closedInventory) {
        if (inventory != closedInventory) {
            return false;
        }
        viewers.remove(playerId);
        return plugin.untrackGuiViewer(playerId, this);
    }

    boolean hasViewer(UUID playerId) {
        return viewers.contains(playerId);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

}
