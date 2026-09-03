package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.scheduler.PurgeTask;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Owns pending data and the whitelist identity handling used by commands and GUIs. */
public final class PendingStorage {

    private static final String ADMIN_PERMISSION = "pendingwhitelist.admin";
    private static final String PLAYER_NAME_PATTERN = "[A-Za-z0-9_]{3,16}";
    private static final long[] WHITELIST_REPAIR_DELAYS_TICKS = {2L, 20L, 60L, 120L, 200L, 300L, 400L, 600L};

    private final PendingWhitelistPlugin plugin;
    private final PendingFileStore fileStore;
    private final List<PendingEntry> pending = new ArrayList<>();
    private final ExecutorService saveExecutor;
    private final ExecutorService whitelistRepairExecutor;
    private final Map<UUID, String> pendingWhitelistRepairs = new ConcurrentHashMap<>();
    private final Set<UUID> scheduledWhitelistRepairs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> knownWhitelistNames = new ConcurrentHashMap<>();

    public PendingStorage(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.fileStore = new PendingFileStore(plugin.getDataFolder().toPath().resolve("pending.json"));
        this.saveExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PendingWhitelist-Storage");
            thread.setDaemon(true);
            return thread;
        });
        this.whitelistRepairExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PendingWhitelist-WhitelistRepair");
            thread.setDaemon(true);
            return thread;
        });
        loadKnownWhitelistNames();
    }

    public void loadFromDisk() {
        try {
            List<PendingEntry> loaded = fileStore.load();
            List<PendingEntry> enriched = enrichEntriesWithResolvedNames(loaded);
            pending.clear();
            pending.addAll(enriched);
            if (!enriched.equals(loaded)) {
                scheduleSave();
            }
        } catch (IOException | JsonParseException ex) {
            plugin.getLogger().warning("Failed to read pending.json: " + ex.getMessage());
            pending.clear();
        }
    }

    public void recordAttempt(String username, UUID uuid) {
        long now = Instant.now().toEpochMilli();
        String normalizedUsername = normalizeIdentifier(username);
        PendingEntry existing = findMatchingEntry(normalizedUsername, uuid);
        String persistedName = resolveDisplayName(normalizedUsername, uuid, existing);
        int attempts = existing == null ? 1 : existing.attempts() + 1;

        if (existing != null) {
            pending.remove(existing);
        }
        pending.add(new PendingEntry(
                uuid != null ? uuid.toString() : existingUuid(existing),
                persistedName,
                attempts,
                existing == null ? now : existing.firstAttempt(),
                now));

        notifyAdmins(username, uuid, persistedName, attempts);
        scheduleSave();
    }

    private String existingUuid(PendingEntry entry) {
        return entry == null ? null : entry.uuid();
    }

    private PendingEntry findMatchingEntry(String username, UUID uuid) {
        if (uuid != null) {
            String uuidString = uuid.toString();
            for (PendingEntry entry : pending) {
                if (uuidString.equalsIgnoreCase(entry.uuid())) {
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
                .append(Component.text("UUID: ", NamedTextColor.GRAY))
                .append(Component.text(uuid == null ? "unknown" : uuid.toString(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Attempts: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(attempts), NamedTextColor.WHITE))
                .build();

        Component whitelist = action("Whitelist", NamedTextColor.GREEN,
                "/wl add " + commandIdentifier, "Whitelist this player");
        Component reject = action("Reject", NamedTextColor.RED,
                "/wl rpl " + commandIdentifier, "Remove this player from pending");
        Component open = action("Open GUI", NamedTextColor.AQUA,
                "/wl add", "Open the pending-player GUI");
        Component message = Component.text("PendingWhitelist", NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(displayName, NamedTextColor.WHITE))
                .append(Component.text(" is waiting for whitelist review", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Actions: ", NamedTextColor.GRAY))
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

    private boolean isStoredWhitelistName(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
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

    public boolean removeFromWhitelist(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        UUID resolvedUuid = resolveWhitelistUuid(normalizedIdentifier);
        if (FloodgateUtil.isFloodgateId(resolvedUuid)) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(resolvedUuid);
            if (!player.isWhitelisted()) {
                return false;
            }
            player.setWhitelisted(false);
            return true;
        }

        PendingEntry pendingEntry = findPendingEntry(normalizedIdentifier);
        UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());
        return removeWhitelistMatches(normalizedIdentifier, pendingUuid);
    }

    private List<PendingEntry> findAllMatches(String identifier) {
        List<PendingEntry> matches = new ArrayList<>();
        for (PendingEntry entry : pending) {
            if (entry.matchesIdentifier(identifier)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private boolean removeWhitelistMatches(String identifier, UUID pendingUuid) {
        UUID identifierUuid = parseUuid(identifier);
        List<OfflinePlayer> matches = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (matchesWhitelistPlayer(player, identifier, identifierUuid, pendingUuid)) {
                matches.add(player);
            }
        }

        OfflinePlayer resolved = resolveOfflinePlayer(identifier);
        if (resolved != null && resolved.isWhitelisted() && !matches.contains(resolved)) {
            matches.add(resolved);
        }

        boolean changed = false;
        for (OfflinePlayer player : matches) {
            if (player.isWhitelisted()) {
                player.setWhitelisted(false);
                changed = true;
            }
        }
        return changed;
    }

    private boolean matchesWhitelistPlayer(OfflinePlayer player, String identifier,
            UUID identifierUuid, UUID pendingUuid) {
        String name = normalizeIdentifier(player.getName());
        return (identifierUuid != null && identifierUuid.equals(player.getUniqueId()))
                || (pendingUuid != null && pendingUuid.equals(player.getUniqueId()))
                || (name != null && name.equalsIgnoreCase(identifier));
    }

    public boolean removePendingOnly(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        List<PendingEntry> matches = findAllMatches(normalizedIdentifier);
        if (matches.isEmpty()) {
            return false;
        }
        pending.removeAll(matches);
        scheduleSave();
        return true;
    }

    public boolean isWhitelisted(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }
        UUID resolvedUuid = resolveWhitelistUuid(normalizedIdentifier);
        if (FloodgateUtil.isFloodgateId(resolvedUuid)) {
            return isExactUuidWhitelisted(resolvedUuid);
        }
        OfflinePlayer player = resolvedUuid == null
                ? resolveOfflinePlayer(normalizedIdentifier)
                : Bukkit.getOfflinePlayer(resolvedUuid);
        return player != null && player.isWhitelisted();
    }

    public boolean addToWhitelist(UUID uuid, String username) {
        String normalizedUsername = normalizeWhitelistName(uuid, username);
        if (uuid == null || normalizedUsername == null) {
            return false;
        }
        if (FloodgateUtil.isFloodgateId(uuid)) {
            if (isExactUuidWhitelisted(uuid)) {
                rememberWhitelistName(uuid, normalizedUsername);
                repairWhitelistJsonName(uuid, normalizedUsername);
                return false;
            }
            return addFloodgatePlayerToWhitelist(uuid, normalizedUsername);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.isWhitelisted()) {
            rememberWhitelistName(uuid, normalizedUsername);
            repairWhitelistJsonName(uuid, normalizedUsername);
            return false;
        }
        player.setWhitelisted(true);
        rememberWhitelistName(uuid, normalizedUsername);
        repairWhitelistJsonName(uuid, normalizedUsername);
        return true;
    }

    public boolean addToWhitelist(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        UUID resolvedUuid = resolveWhitelistUuid(normalizedIdentifier);
        String resolvedName = resolveWhitelistName(normalizedIdentifier);
        if (FloodgateUtil.isFloodgateId(resolvedUuid)) {
            if (isExactUuidWhitelisted(resolvedUuid)) {
                rememberWhitelistName(resolvedUuid, resolvedName);
                repairWhitelistJsonName(resolvedUuid, resolvedName);
                return false;
            }
            return addFloodgatePlayerToWhitelist(resolvedUuid, resolvedName);
        }

        OfflinePlayer offlinePlayer = resolvedUuid == null
                ? resolveOfflinePlayer(normalizedIdentifier)
                : Bukkit.getOfflinePlayer(resolvedUuid);
        if (offlinePlayer == null) {
            return false;
        }

        if (offlinePlayer.isWhitelisted()) {
            if (resolvedUuid != null) {
                rememberWhitelistName(resolvedUuid, resolvedName);
                repairWhitelistJsonName(resolvedUuid, resolvedName);
            }
            return false;
        }

        offlinePlayer.setWhitelisted(true);
        rememberWhitelistName(offlinePlayer.getUniqueId(), resolvedName);
        repairWhitelistJsonName(offlinePlayer.getUniqueId(), resolvedName);
        return true;
    }

    public boolean addFloodgatePlayerToWhitelist(UUID uuid, String username) {
        String normalizedUsername = normalizeWhitelistName(uuid, username);
        if (uuid == null || normalizedUsername == null || !FloodgateUtil.isFloodgateId(uuid)) {
            return false;
        }
        if (isExactUuidWhitelisted(uuid)) {
            rememberWhitelistName(uuid, normalizedUsername);
            repairWhitelistJsonName(uuid, normalizedUsername);
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.isWhitelisted()) {
            rememberWhitelistName(uuid, normalizedUsername);
            repairWhitelistJsonName(uuid, normalizedUsername);
            return false;
        }
        player.setWhitelisted(true);
        rememberWhitelistName(uuid, normalizedUsername);
        repairWhitelistJsonName(uuid, normalizedUsername);
        return true;
    }

    private String normalizeWhitelistName(UUID uuid, String username) {
        String normalized = normalizeIdentifier(username);
        if (normalized == null) {
            return null;
        }
        if (uuid != null && FloodgateUtil.isFloodgateId(uuid)) {
            normalized = FloodgateUtil.addPrefix(normalized);
        }
        return isStoredWhitelistName(normalized) ? normalized : null;
    }

    public boolean isFloodgateUuid(String identifier) {
        return FloodgateUtil.isFloodgateId(parseUuid(identifier));
    }

    private boolean isExactUuidWhitelisted(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (uuid.equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private UUID resolveWhitelistUuid(String identifier) {
        PendingEntry entry = findPendingEntry(identifier);
        UUID entryUuid = entry == null ? null : parseUuid(entry.uuid());
        if (entryUuid != null) {
            return entryUuid;
        }

        UUID identifierUuid = parseUuid(identifier);
        if (identifierUuid != null) {
            return identifierUuid;
        }

        FloodgateUtil.Identity identity = FloodgateUtil.resolveIdentifierIdentity(identifier);
        if (identity != null) {
            return identity.floodgateUuid();
        }

        Player onlinePlayer = Bukkit.getPlayerExact(identifier);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(identifier);
        return offlinePlayer.getUniqueId();
    }

    private String resolveWhitelistName(String identifier) {
        PendingEntry entry = findPendingEntry(identifier);
        if (entry != null) {
            UUID entryUuid = parseUuid(entry.uuid());
            String entryName = normalizeIdentifier(entry.name());
            if (entryName != null) {
                return normalizeWhitelistName(entryUuid, entryName);
            }
        }

        Player onlinePlayer = Bukkit.getPlayerExact(identifier);
        if (onlinePlayer != null) {
            FloodgateUtil.Identity identity = FloodgateUtil.resolveOnlineIdentity(onlinePlayer);
            String onlineName = identity != null ? identity.username() : onlinePlayer.getName();
            return normalizeWhitelistName(onlinePlayer.getUniqueId(), onlineName);
        }

        UUID uuid = parseUuid(identifier);
        if (uuid != null) {
            String known = getKnownWhitelistName(uuid);
            if (known != null) {
                return normalizeWhitelistName(uuid, known);
            }
            return normalizeWhitelistName(uuid, Bukkit.getOfflinePlayer(uuid).getName());
        }

        FloodgateUtil.Identity identity = FloodgateUtil.resolveIdentifierIdentity(identifier);
        if (identity != null) {
            return normalizeWhitelistName(identity.floodgateUuid(), identity.username());
        }

        return normalizeWhitelistName(null, identifier);
    }

    public String getKnownWhitelistName(UUID uuid) {
        return uuid == null ? null : knownWhitelistNames.get(uuid);
    }

    public void rememberWhitelistName(UUID uuid, String name) {
        String normalizedName = normalizeIdentifier(name);
        if (uuid == null || !isStoredWhitelistName(normalizedName)) {
            return;
        }
        knownWhitelistNames.put(uuid, normalizedName);
        scheduleKnownWhitelistNamesSave();
    }

    private void loadKnownWhitelistNames() {
        Path file = plugin.getDataFolder().toPath().resolve("whitelist-names.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            for (var entry : root.getAsJsonObject().entrySet()) {
                UUID uuid = parseUuid(entry.getKey());
                if (uuid == null || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                String name = normalizeIdentifier(entry.getValue().getAsString());
                if (isStoredWhitelistName(name)) {
                    knownWhitelistNames.put(uuid, name);
                }
            }
        } catch (IOException | JsonParseException | UnsupportedOperationException ignored) {
            plugin.getLogger().warning("Could not read whitelist-names.json.");
        }
    }

    private void scheduleKnownWhitelistNamesSave() {
        if (saveExecutor.isShutdown()) {
            return;
        }
        saveExecutor.execute(() -> {
            Path file = plugin.getDataFolder().toPath().resolve("whitelist-names.json");
            Path temporary = file.resolveSibling("whitelist-names.json.tmp");
            try {
                Files.createDirectories(file.getParent());
                JsonObject object = new JsonObject();
                for (Map.Entry<UUID, String> entry : knownWhitelistNames.entrySet()) {
                    object.addProperty(entry.getKey().toString(), entry.getValue());
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(temporary, gson.toJson(object) + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().fine("Could not save whitelist-names.json: " + ex.getMessage());
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
        });
    }

    public void repairWhitelistJsonName(UUID uuid, String name) {
        String normalizedName = normalizeIdentifier(name);
        if (uuid == null || !isStoredWhitelistName(normalizedName) || whitelistRepairExecutor.isShutdown()) {
            return;
        }

        pendingWhitelistRepairs.put(uuid, normalizedName);
        if (scheduledWhitelistRepairs.add(uuid)) {
            scheduleWhitelistRepair(uuid, 0);
        }
    }

    private void scheduleWhitelistRepair(UUID uuid, int attempt) {
        if (attempt >= WHITELIST_REPAIR_DELAYS_TICKS.length) {
            scheduledWhitelistRepairs.remove(uuid);
            pendingWhitelistRepairs.remove(uuid);
            return;
        }

        long delay = WHITELIST_REPAIR_DELAYS_TICKS[attempt];
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (whitelistRepairExecutor.isShutdown()) {
                scheduledWhitelistRepairs.remove(uuid);
                pendingWhitelistRepairs.remove(uuid);
                return;
            }

            String name = pendingWhitelistRepairs.get(uuid);
            if (name == null) {
                scheduledWhitelistRepairs.remove(uuid);
                return;
            }

            whitelistRepairExecutor.submit(() -> {
                boolean repaired = repairWhitelistJsonNameNow(uuid, name);
                String latestName = pendingWhitelistRepairs.get(uuid);
                if (latestName == null) {
                    scheduledWhitelistRepairs.remove(uuid);
                    return;
                }
                if (attempt + 1 >= WHITELIST_REPAIR_DELAYS_TICKS.length) {
                    scheduledWhitelistRepairs.remove(uuid);
                    pendingWhitelistRepairs.remove(uuid);
                    if (!repaired) {
                        plugin.getLogger().fine("Could not confirm whitelist name repair for " + uuid);
                    }
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> scheduleWhitelistRepair(uuid, attempt + 1));
            });
        }, delay);
    }

    private String findWhitelistJsonName(JsonArray entries, UUID uuid) {
        for (var element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.has("uuid") || !object.get("uuid").isJsonPrimitive()) {
                continue;
            }
            if (!uuid.toString().equalsIgnoreCase(object.get("uuid").getAsString())) {
                continue;
            }
            if (object.has("name") && object.get("name").isJsonPrimitive()) {
                return object.get("name").getAsString();
            }
            return null;
        }
        return null;
    }

    private boolean repairWhitelistJsonNameNow(UUID uuid, String name) {
        Path file = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
        Path temporary = file.resolveSibling("whitelist.json.pendingwhitelist.tmp");
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (repairWhitelistJsonNameAttempt(file, temporary, uuid, name)) {
                    return true;
                }
            }
            plugin.getLogger().fine("Skipped whitelist name repair because whitelist.json kept changing.");
            return false;
        } catch (IOException | RuntimeException ex) {
            deleteTemporaryFile(temporary);
            plugin.getLogger().warning("Could not repair whitelist name for " + uuid + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean repairWhitelistJsonNameAttempt(Path file, Path temporary, UUID uuid,
            String name) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        String originalContent = Files.readString(file, StandardCharsets.UTF_8);
        JsonArray entries = parseWhitelistEntries(originalContent);
        if (entries == null) {
            return false;
        }
        if (!updateWhitelistEntry(entries, uuid, name)) {
            return false;
        }
        if (name.equals(findWhitelistJsonName(entries, uuid))) {
            return true;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String repairedJson = gson.toJson(entries) + System.lineSeparator();
        Files.writeString(temporary, repairedJson, StandardCharsets.UTF_8);
        if (whitelistFileChanged(file, originalContent)) {
            Files.deleteIfExists(temporary);
            return false;
        }

        replaceWhitelistFile(file, temporary, originalContent, repairedJson, gson, entries);
        return verifyWhitelistJsonName(file, uuid, name);
    }

    private JsonArray parseWhitelistEntries(String content) {
        JsonElement parsed = JsonParser.parseString(content);
        return parsed.isJsonArray() ? parsed.getAsJsonArray() : null;
    }

    private boolean updateWhitelistEntry(JsonArray entries, UUID uuid, String name) {
        for (var element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!hasUuid(object, uuid)) {
                continue;
            }
            String current = object.has("name") && object.get("name").isJsonPrimitive()
                    ? object.get("name").getAsString() : "";
            if (!name.equals(current)) {
                object.addProperty("name", name);
            }
            return true;
        }
        return false;
    }

    private boolean hasUuid(JsonObject object, UUID uuid) {
        return object.has("uuid") && object.get("uuid").isJsonPrimitive()
                && uuid.toString().equalsIgnoreCase(object.get("uuid").getAsString());
    }

    private boolean whitelistFileChanged(Path file, String originalContent) throws IOException {
        return !originalContent.equals(Files.readString(file, StandardCharsets.UTF_8));
    }

    private void replaceWhitelistFile(Path file, Path temporary, String originalContent,
            String repairedJson, Gson gson, JsonArray entries) throws IOException {
        try {
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            if (whitelistFileChanged(file, originalContent)) {
                Files.deleteIfExists(temporary);
                throw ex;
            }
            Files.deleteIfExists(temporary);
            Files.writeString(file, repairedJson, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);
        }
    }

    private boolean verifyWhitelistJsonName(Path file, UUID uuid, String name) throws IOException {
        String repairedContent = Files.readString(file, StandardCharsets.UTF_8);
        JsonArray repairedEntries = parseWhitelistEntries(repairedContent);
        return repairedEntries != null && name.equals(findWhitelistJsonName(repairedEntries, uuid));
    }

    private void deleteTemporaryFile(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // Best-effort cleanup of a temporary repair file.
        }
    }

    private OfflinePlayer resolveOfflinePlayer(String identifier) {
        PendingEntry entry = findMatchingEntry(identifier, null);
        if (entry != null) {
            OfflinePlayer fromEntry = resolvePendingPlayer(entry);
            if (fromEntry != null) {
                return fromEntry;
            }
        }

        UUID uuid = parseUuid(identifier);
        return uuid == null ? Bukkit.getOfflinePlayer(identifier) : Bukkit.getOfflinePlayer(uuid);
    }

    private OfflinePlayer resolvePendingPlayer(PendingEntry entry) {
        UUID uuid = parseUuid(entry.uuid());
        if (uuid != null) {
            OfflinePlayer byUuid = Bukkit.getOfflinePlayer(uuid);
            if (FloodgateUtil.isFloodgateId(uuid) || byUuid.getName() != null) {
                return byUuid;
            }
        }
        if (isStoredWhitelistName(entry.name())) {
            return Bukkit.getOfflinePlayer(entry.name());
        }
        return null;
    }

    public boolean isPending(String identifier) {
        return findPendingEntry(identifier) != null;
    }

    public PendingEntry findPendingEntry(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        return findMatchingEntry(normalized, null);
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

    private Map<UUID, String> readStoredWhitelistNames() {
        Map<UUID, String> names = new java.util.LinkedHashMap<>();
        Path file = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
        if (!Files.isRegularFile(file)) {
            return names;
        }

        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                return names;
            }
            for (var element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("uuid") || !object.has("name")) {
                    continue;
                }

                UUID uuid = parseUuid(object.get("uuid").getAsString());
                String name = normalizeIdentifier(object.get("name").getAsString());
                if (uuid != null && name != null) {
                    names.put(uuid, name);
                }
            }
        } catch (IOException | JsonParseException | UnsupportedOperationException ignored) {
            // Bukkit's whitelist remains the source of truth if the JSON file cannot be read.
        }
        return names;
    }

    public String resolveWhitelistedUuid(String name) {
        String normalized = normalizeIdentifier(name);
        if (normalized == null) {
            return null;
        }

        UUID directUuid = parseUuid(normalized);
        if (directUuid != null) {
            return directUuid.toString();
        }

        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            String playerName = normalizeIdentifier(player.getName());
            String knownName = knownWhitelistNames.get(player.getUniqueId());
            if ((playerName != null && playerName.equalsIgnoreCase(normalized))
                    || (knownName != null && knownName.equalsIgnoreCase(normalized))) {
                return player.getUniqueId().toString();
            }
        }

        Map<UUID, String> storedNames = readStoredWhitelistNames();
        for (Map.Entry<UUID, String> entry : storedNames.entrySet()) {
            String storedName = normalizeIdentifier(entry.getValue());
            if (storedName != null && storedName.equalsIgnoreCase(normalized)) {
                return entry.getKey().toString();
            }
        }

        FloodgateUtil.Identity identity = FloodgateUtil.resolveIdentifierIdentity(normalized);
        if (identity != null) {
            return identity.floodgateUuid().toString();
        }

        return null;
    }

    public List<String> getWhitelistedUsernames() {
        Map<UUID, String> storedNames = readStoredWhitelistNames();
        List<String> names = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            String name = knownWhitelistNames.get(player.getUniqueId());
            if (name == null) {
                name = storedNames.get(player.getUniqueId());
            }
            if (name == null) {
                name = normalizeIdentifier(player.getName());
            }
            if (name == null) {
                name = player.getUniqueId().toString();
            }
            if (!containsIgnoreCase(names, name)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    public List<PendingEntry> getPendingEntriesSortedByRecencyDesc() {
        List<PendingEntry> entries = new ArrayList<>(pending);
        entries.sort((left, right) -> Long.compare(right.lastAttempt(), left.lastAttempt()));
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
        int removed = 0;
        for (int index = pending.size() - 1; index >= 0; index--) {
            if (pending.get(index).lastAttempt() < cutoffMillis) {
                pending.remove(index);
                removed++;
            }
        }
        if (removed > 0) {
            scheduleSave();
        }
        return removed;
    }

    public int size() {
        return pending.size();
    }

    public void schedulePurgeCheck() {
        new PurgeTask(plugin, this).runTaskTimer(plugin, 20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void scheduleSave() {
        if (!saveExecutor.isShutdown()) {
            submitSave(List.copyOf(pending));
        }
    }

    public void flushSynchronously() {
        if (!saveExecutor.isShutdown()) {
            waitFor(submitSave(List.copyOf(pending)), "save pending entries during shutdown");
            saveExecutor.shutdown();
        }

        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out while waiting for pending storage writes to finish.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while waiting for pending storage writes to finish.");
        }

        whitelistRepairExecutor.shutdown();
        try {
            if (!whitelistRepairExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out while waiting for whitelist repairs to finish.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while waiting for whitelist repairs to finish.");
        }
    }

    public boolean reloadFromDisk() {
        if (!saveExecutor.isShutdown() && !waitFor(saveExecutor.submit(() -> {
        }), "finish pending storage writes before reloading")) {
            return false;
        }
        loadFromDisk();
        return true;
    }

    private Future<?> submitSave(List<PendingEntry> snapshot) {
        return saveExecutor.submit(() -> saveToDisk(snapshot));
    }

    private boolean waitFor(Future<?> future, String operation) {
        try {
            future.get();
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while trying to " + operation + ".");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            plugin.getLogger().warning("Failed to " + operation + ": "
                    + (cause == null ? "unknown error" : cause.getMessage()));
        }
        return false;
    }

    private void saveToDisk(List<PendingEntry> snapshot) {
        try {
            fileStore.save(snapshot);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pending.json: " + ex.getMessage());
        }
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
