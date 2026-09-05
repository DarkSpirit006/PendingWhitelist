package dev.darkspirit69.pendingwhitelist.storage;

import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import com.google.gson.JsonParseException;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SkinHeadUtil;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.scheduler.PurgeTask;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns pending-entry data and coordinates persistence for commands and GUIs.
 */
public final class PendingStorage implements PendingRepository {

    private static final String ADMIN_PERMISSION = "pendingwhitelist.admin";
    private static final String PLAYER_NAME_PATTERN = "[A-Za-z0-9_]{3,16}";

    private final PendingWhitelistPlugin plugin;
    private final PendingFileStore fileStore;
    private final List<PendingEntry> pending = new ArrayList<>();
    private final Map<UUID, PendingEntry> pendingByUuid = new HashMap<>();
    private final Map<String, PendingEntry> pendingByName = new HashMap<>();
    private final PendingPersistence persistence;
    private List<PendingEntry> recencyCache;
    private final WhitelistService whitelistService;
    private final Map<UUID, Long> notificationCooldowns = new ConcurrentHashMap<>();
    private BukkitTask purgeTask;

    public PendingStorage(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.fileStore = new PendingFileStore(plugin.getDataFolder().toPath().resolve("pending.json"));
        this.persistence = new PendingPersistence(fileStore);
        this.whitelistService = new WhitelistService(plugin, this, persistence);
    }

    public void loadFromDisk() {
        DebugLog.debug("PendingStorage.loadFromDisk() started");
        try {
            List<PendingEntry> loaded = persistence.load();
            List<PendingEntry> enriched = enrichEntriesWithResolvedNames(loaded);
            pending.clear();
            pending.addAll(enriched);
            rebuildIndexes();
            DebugLog.debug("Loaded " + enriched.size() + " pending entr" + (enriched.size() == 1 ? "y" : "ies"));
            if (!enriched.equals(loaded)) {
                scheduleSave();
            }
        } catch (IOException | JsonParseException ex) {
            DebugLog.error("Failed to load pending.json", ex);
            DebugLog.error("Failed to read pending.json: " + ex.getMessage(), ex);
            pending.clear();
            clearIndexes();
        }
    }

    public void recordAttempt(String username, UUID uuid) {
        DebugLog.debug("recordAttempt(username=" + username + ", uuid=" + uuid + ")");
        long now = Instant.now().toEpochMilli();
        String normalizedUsername = normalizeIdentifier(username);
        PendingEntry existing = findMatchingEntry(normalizedUsername, uuid);
        String persistedName = resolveDisplayName(normalizedUsername, uuid, existing);
        int attempts = existing == null ? 1 : existing.attempts() + 1;

        if (existing != null) {
            removeFromIndexes(existing);
            pending.remove(existing);
        }
        PendingEntry updated = new PendingEntry(
                uuid != null ? uuid.toString() : existingUuid(existing),
                persistedName,
                attempts,
                existing == null ? now : existing.firstAttempt(),
                now);
        pending.add(updated);
        addToIndexes(updated);
        invalidateRecencyCache();

        DebugLog.debug("Pending attempt count for " + persistedName + ": " + attempts);
        if (plugin.isJoinAttemptNotificationsEnabled() && shouldNotify(uuid)) {
            notifyAdmins(username, uuid, persistedName, attempts);
        }
        scheduleSave();
        DebugLog.debug("Pending attempt recorded and save requested for " + persistedName);
    }

    private boolean shouldNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        long cooldownMillis = plugin.getJoinAttemptNotificationCooldownSeconds() * 1000L;
        if (uuid == null || cooldownMillis <= 0) {
            return true;
        }

        Long lastNotification = notificationCooldowns.get(uuid);
        if (lastNotification != null && now - lastNotification < cooldownMillis) {
            DebugLog.debug("Notification cooldown active for uuid=" + uuid);
            return false;
        }

        notificationCooldowns.put(uuid, now);
        return true;
    }

    private String existingUuid(PendingEntry entry) {
        return entry == null ? null : entry.uuid();
    }

    private PendingEntry findMatchingEntry(String username, UUID uuid) {
        if (uuid != null) {
            PendingEntry entry = pendingByUuid.get(uuid);
            if (entry != null) {
                return entry;
            }
        }
        if (username == null || username.isBlank()) {
            return null;
        }
        return pendingByName.get(username.toLowerCase(Locale.ROOT));
    }

    private String resolveDisplayName(String username, UUID uuid, PendingEntry existing) {
        String resolvedName = normalizeIdentifier(username);
        if (resolvedName == null) {
            FloodgateUtil.Identity identity = FloodgateUtil.resolveOnlineIdentity(uuid);
            if (identity != null) {
                resolvedName = identity.username();
            }
        }
        if (resolvedName == null) {
            resolvedName = resolvePlayerName(uuid);
        }
        if (resolvedName == null && existing != null) {
            resolvedName = normalizeIdentifier(existing.name());
        }
        return firstNonBlank(resolvedName, uuid == null ? null : uuid.toString(), "unknown");
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        Player livePlayer = Bukkit.getPlayer(uuid);
        if (livePlayer != null) {
            return normalizeIdentifier(livePlayer.getName());
        }

        return normalizeIdentifier(Bukkit.getOfflinePlayer(uuid).getName());
    }

    private void notifyAdmins(String username, UUID uuid, String resolvedName, int attempts) {
        String displayName = firstNonBlank(resolvedName, username, "unknown");
        String identifier = uuid == null ? displayName : uuid.toString();
        String commandIdentifier = isPlayerName(resolvedName) ? resolvedName
                : (isPlayerName(username) ? username : identifier);

        Component hover = Component.text()
                .append(Component.text("UUID: ", MessageStyle.SECONDARY))
                .append(Component.text(uuid == null ? "unknown" : uuid.toString(), MessageStyle.VALUE))
                .append(Component.newline())
                .append(Component.text("Attempts: ", MessageStyle.SECONDARY))
                .append(Component.text(String.valueOf(attempts), MessageStyle.VALUE))
                .build();

        Component whitelist = action("Whitelist", MessageStyle.SUCCESS,
                "/wl add " + commandIdentifier, "Whitelist this player");
        Component reject = action("Reject", MessageStyle.ERROR,
                "/wl rpl " + commandIdentifier, "Remove this player from pending");
        Component open = action("Open GUI", MessageStyle.PRIMARY,
                "/wl add", "Open the pending-player GUI");
        Component message = Component.text("PendingWhitelist", MessageStyle.PRIMARY)
                .append(Component.newline())
                .append(Component.text(displayName, MessageStyle.VALUE))
                .append(Component.text(" is waiting for whitelist review", MessageStyle.SECONDARY))
                .append(Component.newline())
                .append(Component.text("Actions: ", MessageStyle.SECONDARY))
                .append(whitelist)
                .append(Component.space())
                .append(reject)
                .append(Component.space())
                .append(open)
                .hoverEvent(HoverEvent.showText(hover));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(message);
                SoundUtil.notification(player);
            }
        }
    }

    private Component action(String label, NamedTextColor color, String command, String hoverText) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText, color)));
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isPlayerName(String value) {
        return value != null && value.matches(PLAYER_NAME_PATTERN);
    }

    private List<PendingEntry> enrichEntriesWithResolvedNames(List<PendingEntry> entries) {
        List<PendingEntry> enriched = new ArrayList<>(entries.size());
        for (PendingEntry entry : entries) {
            String resolvedName = entry.name();
            UUID uuid = parseUuid(entry.uuid());
            if ((resolvedName == null || resolvedName.isBlank()) && uuid != null) {
                resolvedName = resolvePlayerName(uuid);
            }
            if (resolvedName == null) {
                resolvedName = entry.uuid();
            }
            if (resolvedName.equals(entry.name())) {
                enriched.add(entry);
            } else {
                enriched.add(new PendingEntry(entry.uuid(), resolvedName, entry.attempts(),
                        entry.firstAttempt(), entry.lastAttempt()));
            }
        }
        return enriched;
    }

    public boolean isPending(String identifier) {
        return findPendingEntry(identifier) != null;
    }

    public PendingEntry findPendingEntry(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        return findMatchingEntry(normalized, parseUuid(normalized));
    }

    public String resolveDisplayNameForIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }

        PendingEntry entry = findPendingEntry(normalized);
        if (entry != null) {
            if (entry.name() != null && !entry.name().isBlank()) {
                return entry.name();
            }
            String resolved = resolvePlayerName(parseUuid(entry.uuid()));
            return firstNonBlank(resolved, entry.uuid(), normalized);
        }

        String resolved = resolvePlayerName(parseUuid(normalized));
        return firstNonBlank(resolved, normalized, normalized);
    }

    public List<String> getPendingUsernames() {
        List<String> names = new ArrayList<>(pending.size());
        for (PendingEntry entry : pending) {
            names.add(entry.displayName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<PendingEntry> getPendingEntriesSortedByRecencyDesc() {
        if (recencyCache == null) {
            List<PendingEntry> entries = new ArrayList<>(pending);
            entries.sort((left, right) -> Long.compare(right.lastAttempt(), left.lastAttempt()));
            recencyCache = List.copyOf(entries);
        }
        return recencyCache;
    }

    public List<String> getPendingUsernamesSortedByRecencyDesc() {
        List<String> names = new ArrayList<>();
        for (PendingEntry entry : getPendingEntriesSortedByRecencyDesc()) {
            names.add(entry.displayName());
        }
        return names;
    }

    public int purgeExpiredEntries(long cutoffMillis) {
        int removed = 0;
        for (int index = pending.size() - 1; index >= 0; index--) {
            PendingEntry entry = pending.get(index);
            if (entry.lastAttempt() < cutoffMillis) {
                removeFromIndexes(entry);
                pending.remove(index);
                invalidateSkinCache(entry);
                removed++;
            }
        }
        if (removed > 0) {
            invalidateRecencyCache();
        }
        if (removed > 0) {
            scheduleSave();
        }
        return removed;
    }

    private void invalidateSkinCache(PendingEntry entry) {
        UUID uuid = parseUuid(entry.uuid());
        if (uuid != null) {
            SkinHeadUtil.invalidate(uuid);
        }
    }

    public int size() {
        return pending.size();
    }

    @Override
    public boolean removeFromWhitelist(String identifier) {
        return whitelistService.removeFromWhitelist(identifier);
    }

    @Override
    public boolean removePendingOnly(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        List<PendingEntry> matches = new ArrayList<>();
        PendingEntry match = findMatchingEntry(normalizedIdentifier, parseUuid(normalizedIdentifier));
        if (match != null) {
            matches.add(match);
        }
        if (matches.isEmpty()) {
            return false;
        }
        for (PendingEntry entry : matches) {
            removeFromIndexes(entry);
            pending.remove(entry);
            invalidateSkinCache(entry);
        }
        invalidateRecencyCache();
        scheduleSave();
        return true;
    }

    @Override
    public void removePendingEntries(List<PendingEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        for (PendingEntry entry : entries) {
            if (pending.remove(entry)) {
                removeFromIndexes(entry);
                invalidateSkinCache(entry);
            }
        }
        invalidateRecencyCache();
        scheduleSave();
    }

    @Override
    public boolean isWhitelisted(String identifier) {
        return whitelistService.isWhitelisted(identifier);
    }

    @Override
    public boolean addToWhitelist(UUID uuid, String username) {
        return whitelistService.addToWhitelist(uuid, username);
    }

    @Override
    public boolean addToWhitelist(String identifier) {
        return whitelistService.addToWhitelist(identifier);
    }

    @Override
    public boolean addFloodgatePlayerToWhitelist(UUID uuid, String username) {
        return whitelistService.addFloodgatePlayerToWhitelist(uuid, username);
    }

    @Override
    public boolean isFloodgateUuid(String identifier) {
        return whitelistService.isFloodgateUuid(identifier);
    }

    @Override
    public String getKnownWhitelistName(UUID uuid) {
        return whitelistService.getKnownWhitelistName(uuid);
    }

    @Override
    public void rememberWhitelistName(UUID uuid, String name) {
        whitelistService.rememberWhitelistName(uuid, name);
    }

    @Override
    public void repairWhitelistJsonName(UUID uuid, String name) {
        whitelistService.repairWhitelistJsonName(uuid, name);
    }

    @Override
    public String resolveWhitelistedUuid(String name) {
        return whitelistService.resolveWhitelistedUuid(name);
    }

    @Override
    public List<String> getWhitelistedUsernames() {
        return whitelistService.getWhitelistedUsernames();
    }

    public void schedulePurgeCheck() {
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        purgeTask = new PurgeTask(plugin, this).runTaskTimer(plugin, 20L * 60L * 60L, 20L * 60L * 60L);
        DebugLog.debug("Scheduled pending-entry purge task");
    }

    public void scheduleSave() {
        persistence.scheduleSave(List.copyOf(pending));
    }

    public void flushSynchronously() {
        persistence.flush(List.copyOf(pending), "save pending entries");
    }

    public void shutdown() {
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
        notificationCooldowns.clear();
        persistence.shutdown(List.copyOf(pending), "save pending entries during shutdown");
        whitelistService.shutdown();
    }

    private void rebuildIndexes() {
        clearIndexes();
        for (PendingEntry entry : pending) {
            addToIndexes(entry);
        }
        invalidateRecencyCache();
    }

    private void clearIndexes() {
        pendingByUuid.clear();
        pendingByName.clear();
        invalidateRecencyCache();
    }

    private void addToIndexes(PendingEntry entry) {
        UUID uuid = parseUuid(entry.uuid());
        if (uuid != null) {
            pendingByUuid.put(uuid, entry);
        }
        String name = normalizeIdentifier(entry.name());
        if (name != null) {
            pendingByName.put(name.toLowerCase(Locale.ROOT), entry);
        }
    }

    private void removeFromIndexes(PendingEntry entry) {
        UUID uuid = parseUuid(entry.uuid());
        if (uuid != null) {
            pendingByUuid.remove(uuid, entry);
        }
        String name = normalizeIdentifier(entry.name());
        if (name != null) {
            pendingByName.remove(name.toLowerCase(Locale.ROOT), entry);
        }
    }

    private void invalidateRecencyCache() {
        recencyCache = null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
