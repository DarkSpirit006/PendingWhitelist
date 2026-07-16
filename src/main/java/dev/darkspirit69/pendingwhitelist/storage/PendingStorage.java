package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.scheduler.PurgeTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PendingWhitelistPlugin plugin;
    private final Path storageFile;
    private final List<PendingEntry> pending = new ArrayList<>();
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);

    public PendingStorage(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin.getDataFolder().toPath().resolve("pending.json");
    }

    public void loadFromDisk() {
        try {
            if (!Files.exists(storageFile)) {
                Files.createDirectories(storageFile.getParent());
                Files.writeString(storageFile, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }

            String contents = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (contents == null || contents.isBlank()) {
                pending.clear();
                return;
            }

            List<PendingEntry> loaded = parseEntries(contents);
            List<PendingEntry> enriched = enrichEntriesWithResolvedNames(loaded);
            pending.clear();
            pending.addAll(enriched);
            if (!enriched.equals(loaded)) {
                scheduleSave();
            }
        } catch (IOException | JsonParseException ex) {
            plugin.getLogger().warning("Failed to read pending.json: " + ex.getMessage());
            pending.clear();
            if (!Files.exists(storageFile)) {
                try {
                    Files.createDirectories(storageFile.getParent());
                    Files.writeString(storageFile, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException ioException) {
                    plugin.getLogger().warning("Failed to recreate pending.json: " + ioException.getMessage());
                }
            }
        }
    }

    private List<PendingEntry> parseEntries(String contents) {
        JsonElement element = JsonParser.parseString(contents);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        if (element.isJsonArray()) {
            List<PendingEntry> entries = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (!child.isJsonObject()) {
                    continue;
                }
                JsonObject object = child.getAsJsonObject();
                String uuid = normalizeIdentifier(getString(object, "uuid"));
                String name = normalizeIdentifier(getString(object, "name"));
                int attempts = object.has("attempts") ? object.get("attempts").getAsInt() : 0;
                long firstAttempt = object.has("firstAttempt") ? object.get("firstAttempt").getAsLong() : 0L;
                long lastAttempt = object.has("lastAttempt") ? object.get("lastAttempt").getAsLong() : 0L;
                entries.add(new PendingEntry(uuid, name, attempts, firstAttempt, lastAttempt));
            }
            return entries;
        }

        if (element.isJsonObject()) {
            Map<String, PendingEntry> legacy = GSON.fromJson(contents,
                    new TypeToken<Map<String, PendingEntry>>() {
                    }.getType());
            if (legacy == null) {
                return List.of();
            }

            List<PendingEntry> entries = new ArrayList<>();
            for (Map.Entry<String, PendingEntry> legacyEntry : legacy.entrySet()) {
                PendingEntry value = legacyEntry.getValue();
                if (value == null) {
                    continue;
                }
                entries.add(new PendingEntry(
                        value.uuid(),
                        legacyEntry.getKey(),
                        value.attempts(),
                        value.firstAttempt(),
                        value.lastAttempt()));
            }
            return entries;
        }

        return List.of();
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    public void recordAttempt(String username, UUID uuid) {
        long now = Instant.now().toEpochMilli();
        String normalizedUsername = normalizeIdentifier(username);
        PendingEntry existing = findMatchingEntry(normalizedUsername, uuid);
        String resolvedName = resolveDisplayName(normalizedUsername, uuid, existing);
        String persistedName = resolvedName != null && !resolvedName.isBlank() ? resolvedName
                : (normalizedUsername != null ? normalizedUsername : null);

        if (existing != null) {
            pending.remove(existing);
            pending.add(new PendingEntry(
                    uuid != null ? uuid.toString() : existing.uuid(),
                    persistedName,
                    existing.attempts() + 1,
                    existing.firstAttempt(),
                    now));
        } else {
            pending.add(new PendingEntry(
                    uuid != null ? uuid.toString() : null,
                    persistedName,
                    1,
                    now,
                    now));
        }

        notifyAdmins(username, uuid, persistedName, existing != null ? existing.attempts() + 1 : 1);
        scheduleSave();
    }

    private PendingEntry findMatchingEntry(String username, UUID uuid) {
        if (uuid != null) {
            for (PendingEntry entry : pending) {
                if (uuid.toString().equalsIgnoreCase(entry.uuid())) {
                    return entry;
                }
            }
        }

        if (username != null && !username.isBlank()) {
            for (PendingEntry entry : pending) {
                if (entry.matchesIdentifier(username)) {
                    return entry;
                }
            }
        }

        return null;
    }

    private String resolveDisplayName(String username, UUID uuid, PendingEntry existing) {
        String resolvedName = normalizeIdentifier(username);
        if (resolvedName == null && uuid != null) {
            try {
                org.bukkit.entity.Player livePlayer = Bukkit.getPlayer(uuid);
                if (livePlayer != null) {
                    resolvedName = normalizeIdentifier(livePlayer.getName());
                }
            } catch (Exception ignored) {
            }
        }
        if (resolvedName == null && uuid != null) {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                resolvedName = normalizeIdentifier(player.getName());
            } catch (Exception ignored) {
            }
        }
        if (resolvedName == null && existing != null) {
            resolvedName = normalizeIdentifier(existing.name());
        }
        if (resolvedName == null && uuid != null) {
            return uuid.toString();
        }
        return resolvedName;
    }

    private void notifyAdmins(String username, UUID uuid, String resolvedName, int attempts) {
        String displayName = resolvedName != null && !resolvedName.isBlank() ? resolvedName
                : (username != null && !username.isBlank() ? username : "unknown");
        String identifier = uuid != null ? uuid.toString() : displayName;

        Component hover = Component.text()
                .append(Component.text("UUID: ", NamedTextColor.GRAY))
                .append(Component.text(uuid != null ? uuid.toString() : "unknown", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Attempts: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(attempts), NamedTextColor.WHITE))
                .build();

        Component approve = Component.text("[APPROVE]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/wl approve " + identifier))
                .hoverEvent(
                        HoverEvent.showText(Component.text("Approve and whitelist this player", NamedTextColor.GREEN)));
        Component deny = Component.text("[DENY]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/wl deny " + identifier))
                .hoverEvent(HoverEvent
                        .showText(Component.text("Deny and remove this player from pending", NamedTextColor.RED)));

        Component message = Component.text()
                .append(Component.text("[PendingWhitelist] ", NamedTextColor.RED))
                .append(Component.text(displayName, NamedTextColor.YELLOW))
                .append(Component.text(" attempted to join", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Approve: ", NamedTextColor.GRAY))
                .append(approve)
                .append(Component.text("  "))
                .append(Component.text("Deny: ", NamedTextColor.GRAY))
                .append(deny)
                .build();

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("pendingwhitelist.admin")) {
                player.sendMessage(message.hoverEvent(HoverEvent.showText(hover)));
            }
        }
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<PendingEntry> enrichEntriesWithResolvedNames(List<PendingEntry> entries) {
        List<PendingEntry> enriched = new ArrayList<>();
        for (PendingEntry entry : entries) {
            if (entry.name() != null && !entry.name().isBlank()) {
                enriched.add(entry);
                continue;
            }

            String resolvedName = null;
            if (entry.uuid() != null && !entry.uuid().isBlank()) {
                try {
                    UUID uuid = UUID.fromString(entry.uuid());
                    resolvedName = resolveStoredName(uuid);
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (resolvedName == null) {
                resolvedName = entry.uuid();
            }

            enriched.add(new PendingEntry(entry.uuid(), resolvedName, entry.attempts(), entry.firstAttempt(),
                    entry.lastAttempt()));
        }
        return enriched;
    }

    private String resolveStoredName(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        try {
            org.bukkit.entity.Player livePlayer = Bukkit.getPlayer(uuid);
            if (livePlayer != null) {
                return normalizeIdentifier(livePlayer.getName());
            }
        } catch (Exception ignored) {
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            return normalizeIdentifier(offlinePlayer.getName());
        } catch (Exception ignored) {
        }

        return null;
    }

    public boolean remove(String identifier) {
        boolean changed = false;
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        List<PendingEntry> matches = new ArrayList<>();
        for (PendingEntry entry : pending) {
            if (entry.matchesIdentifier(normalizedIdentifier)) {
                matches.add(entry);
            }
        }
        if (!matches.isEmpty()) {
            pending.removeAll(matches);
            changed = true;
        }

        OfflinePlayer offlinePlayer = resolveOfflinePlayer(normalizedIdentifier);
        if (offlinePlayer != null && offlinePlayer.isWhitelisted()) {
            offlinePlayer.setWhitelisted(false);
            changed = true;
        }

        if (changed) {
            scheduleSave();
        }
        return changed;
    }

    public boolean removePendingOnly(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        List<PendingEntry> matches = new ArrayList<>();
        for (PendingEntry entry : pending) {
            if (entry.matchesIdentifier(normalizedIdentifier)) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            return false;
        }

        pending.removeAll(matches);
        scheduleSave();
        return true;
    }

    public boolean addToWhitelist(String identifier) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(identifier);
        if (offlinePlayer == null) {
            return false;
        }
        if (offlinePlayer.isWhitelisted()) {
            return false;
        }
        offlinePlayer.setWhitelisted(true);
        return true;
    }

    private OfflinePlayer resolveOfflinePlayer(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        String trimmed = identifier.trim();

        PendingEntry entry = findMatchingEntry(trimmed, null);
        if (entry != null && entry.uuid() != null && !entry.uuid().isBlank()) {
            try {
                return Bukkit.getOfflinePlayer(UUID.fromString(entry.uuid()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(trimmed));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayer(trimmed);
        }
    }

    public boolean isPending(String identifier) {
        return pending.stream().anyMatch(entry -> entry.matchesIdentifier(identifier));
    }

    public PendingEntry findPendingEntry(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }

        for (PendingEntry entry : pending) {
            if (entry.matchesIdentifier(normalized)) {
                return entry;
            }
        }
        return null;
    }

    public String resolveDisplayNameForIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }

        PendingEntry entry = findMatchingEntry(normalized, null);
        if (entry != null) {
            if (entry.name() != null && !entry.name().isBlank()) {
                return entry.name();
            }
            try {
                UUID uuid = UUID.fromString(entry.uuid());
                String resolvedName = resolveStoredName(uuid);
                if (resolvedName != null) {
                    return resolvedName;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return entry.uuid() != null && !entry.uuid().isBlank() ? entry.uuid() : normalized;
        }

        try {
            UUID uuid = UUID.fromString(normalized);
            String resolvedName = resolveStoredName(uuid);
            if (resolvedName != null) {
                return resolvedName;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return normalized;
    }

    public List<String> getPendingUsernames() {
        List<String> names = new ArrayList<>();
        for (PendingEntry entry : pending) {
            names.add(entry.displayName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<String> getWhitelistedUsernames() {
        List<String> names = new ArrayList<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getWhitelistedPlayers()) {
            String name = normalizeIdentifier(offlinePlayer.getName());
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<PendingEntry> getPendingEntriesSortedByRecencyDesc() {
        List<PendingEntry> entries = new ArrayList<>(pending);
        entries.sort(Comparator.comparingLong(PendingEntry::lastAttempt).reversed());
        return entries;
    }

    public List<String> getPendingUsernamesSortedByRecencyDesc() {
        List<String> names = new ArrayList<>();
        for (PendingEntry entry : getPendingEntriesSortedByRecencyDesc()) {
            names.add(entry.displayName());
        }
        return names;
    }

    public int purgeExpiredEntries(long cutoffMillis) {
        List<PendingEntry> toRemove = new ArrayList<>();
        for (PendingEntry entry : pending) {
            if (entry.lastAttempt() < cutoffMillis) {
                toRemove.add(entry);
            }
        }

        if (toRemove.isEmpty()) {
            return 0;
        }

        pending.removeAll(toRemove);
        scheduleSave();
        return toRemove.size();
    }

    public int size() {
        return pending.size();
    }

    public void schedulePurgeCheck() {
        new PurgeTask(plugin, this).runTaskTimer(plugin, 20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void scheduleSave() {
        if (saveInProgress.get()) {
            return;
        }
        CompletableFuture.runAsync(this::saveToDisk);
    }

    public void flushSynchronously() {
        saveToDisk();
    }

    private void saveToDisk() {
        if (saveInProgress.getAndSet(true)) {
            return;
        }
        try {
            Files.createDirectories(storageFile.getParent());
            String serialized = GSON.toJson(pending);
            Files.writeString(storageFile, serialized, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pending.json: " + ex.getMessage());
        } finally {
            saveInProgress.set(false);
        }
    }
}
