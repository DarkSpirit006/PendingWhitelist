package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides whitelist operations independently from pending-entry persistence.
 */
final class WhitelistService {

    private static final long[] WHITELIST_REPAIR_DELAYS_TICKS = { 2L, 20L, 60L, 120L, 200L, 300L, 400L, 600L };
    private static final long WHITELIST_REPAIR_SUCCESS_COOLDOWN_MS = 5000L;

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository pendingRepository;
    private final PendingPersistence persistence;
    private final java.util.concurrent.ExecutorService whitelistRepairExecutor;
    private final Map<UUID, String> pendingWhitelistRepairs = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> scheduledWhitelistRepairs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RepairRecord> recentWhitelistRepairs = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownWhitelistNames = new ConcurrentHashMap<>();
    private final AtomicLong knownWhitelistNamesRevision = new AtomicLong();
    private final AtomicLong savedWhitelistNamesRevision = new AtomicLong();
    private final AtomicBoolean knownWhitelistNamesSaveQueued = new AtomicBoolean();

    WhitelistService(
            PendingWhitelistPlugin plugin, PendingRepository pendingRepository, PendingPersistence persistence) {
        this.plugin = plugin;
        this.pendingRepository = pendingRepository;
        this.persistence = persistence;
        this.whitelistRepairExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PendingWhitelist-WhitelistRepair");
            thread.setDaemon(true);
            return thread;
        });
        loadKnownWhitelistNames();
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
            // Bukkit's whitelist remains the source of truth if the JSON file cannot be
            // read.
        }
        return names;
    }

    public boolean removeFromWhitelist(String identifier) {
        DebugLog.debug("Removing whitelist identifier: " + identifier);
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

        PendingEntry pendingEntry = pendingRepository.findPendingEntry(normalizedIdentifier);
        UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());
        return removeWhitelistMatches(normalizedIdentifier, pendingUuid);
    }

    private List<PendingEntry> findAllMatches(String identifier) {
        List<PendingEntry> matches = new ArrayList<>();
        for (PendingEntry entry : pendingRepository.getPendingEntriesSortedByRecencyDesc()) {
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
        DebugLog.debug("Removing pending-only identifier: " + identifier);
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        List<PendingEntry> matches = findAllMatches(normalizedIdentifier);
        if (matches.isEmpty()) {
            return false;
        }
        pendingRepository.removePendingEntries(matches);
        return true;
    }

    public boolean isWhitelisted(String identifier) {
        DebugLog.debug("Checking whitelist identifier: " + identifier);
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
        DebugLog.debug("Adding whitelist identifier: " + identifier);
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
        PendingEntry entry = pendingRepository.findPendingEntry(identifier);
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
        PendingEntry entry = pendingRepository.findPendingEntry(identifier);
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
        String previousName = knownWhitelistNames.put(uuid, normalizedName);
        if (!normalizedName.equals(previousName)) {
            knownWhitelistNamesRevision.incrementAndGet();
            scheduleKnownWhitelistNamesSave();
        }
    }

    private void loadKnownWhitelistNames() {
        DebugLog.debug("Loading remembered whitelist names");
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
            DebugLog.warn("Could not read whitelist-names.json.");
        }
    }

    private void scheduleKnownWhitelistNamesSave() {
        DebugLog.debug("Scheduling remembered whitelist names save");
        if (knownWhitelistNamesRevision.get() == savedWhitelistNamesRevision.get()
                || !knownWhitelistNamesSaveQueued.compareAndSet(false, true)) {
            return;
        }
        if (!persistence.execute(this::saveKnownWhitelistNames)) {
            knownWhitelistNamesSaveQueued.set(false);
        }
    }

    private void saveKnownWhitelistNames() {
        DebugLog.debug("Saving remembered whitelist names");
        try {
            for (;;) {
                long revision = knownWhitelistNamesRevision.get();
                if (revision == savedWhitelistNamesRevision.get()) {
                    return;
                }
                if (!writeKnownWhitelistNames()) {
                    return;
                }
                savedWhitelistNamesRevision.set(revision);
            }
        } finally {
            knownWhitelistNamesSaveQueued.set(false);
            if (knownWhitelistNamesRevision.get() != savedWhitelistNamesRevision.get()) {
                scheduleKnownWhitelistNamesSave();
            }
        }
    }

    private boolean writeKnownWhitelistNames() {
        DebugLog.debug("Writing remembered whitelist names to disk");
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
            return true;
        } catch (IOException ex) {
            DebugLog.error("Could not save whitelist-names.json: " + ex.getMessage(), ex);
            deleteTemporaryFile(temporary);
            return false;
        }
    }

    public void repairWhitelistJsonName(UUID uuid, String name) {
        String normalizedName = normalizeIdentifier(name);
        if (uuid == null || !isStoredWhitelistName(normalizedName) || whitelistRepairExecutor.isShutdown()) {
            return;
        }

        pendingWhitelistRepairs.put(uuid, normalizedName);
        long now = System.currentTimeMillis();
        RepairRecord recentRepair = recentWhitelistRepairs.get(uuid);
        if (recentRepair != null) {
            if (normalizedName.equals(recentRepair.name())
                    && now - recentRepair.completedAtMs() < WHITELIST_REPAIR_SUCCESS_COOLDOWN_MS) {
                pendingWhitelistRepairs.remove(uuid, normalizedName);
                return;
            }
            recentWhitelistRepairs.remove(uuid, recentRepair);
        }
        if (scheduledWhitelistRepairs.add(uuid)) {
            scheduleWhitelistRepair(uuid, 0);
        }
    }

    private void scheduleWhitelistRepair(UUID uuid, int attempt) {
        DebugLog.debug("Whitelist JSON repair attempt scheduled: attempt=" + attempt);
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

            try {
                whitelistRepairExecutor.submit(() -> {
                    boolean repaired = repairWhitelistJsonNameNow(uuid, name);
                    String latestName = pendingWhitelistRepairs.get(uuid);
                    if (repaired) {
                        pendingWhitelistRepairs.remove(uuid, name);
                        scheduledWhitelistRepairs.remove(uuid);
                        recentWhitelistRepairs.put(uuid, new RepairRecord(name, System.currentTimeMillis()));
                        DebugLog.debug("Whitelist JSON name repair completed");
                        return;
                    }
                    if (latestName == null) {
                        scheduledWhitelistRepairs.remove(uuid);
                        return;
                    }
                    if (attempt + 1 >= WHITELIST_REPAIR_DELAYS_TICKS.length) {
                        scheduledWhitelistRepairs.remove(uuid);
                        pendingWhitelistRepairs.remove(uuid);
                        if (!repaired) {
                            DebugLog.debug("Could not confirm whitelist name repair for " + uuid);
                        }
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> scheduleWhitelistRepair(uuid, attempt + 1));
                });
            } catch (RejectedExecutionException ex) {
                scheduledWhitelistRepairs.remove(uuid);
                pendingWhitelistRepairs.remove(uuid);
            }
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

    private record RepairRecord(String name, long completedAtMs) {
    }

    private boolean repairWhitelistJsonNameNow(UUID uuid, String name) {
        DebugLog.debug("Running whitelist JSON name repair");
        Path file = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
        Path temporary = file.resolveSibling("whitelist.json.pendingwhitelist.tmp");
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (repairWhitelistJsonNameAttempt(file, temporary, uuid, name)) {
                    return true;
                }
            }
            DebugLog.debug("Skipped whitelist name repair because whitelist.json kept changing.");
            return false;
        } catch (IOException | RuntimeException ex) {
            deleteTemporaryFile(temporary);
            DebugLog.error("Could not repair whitelist name for " + uuid + ": " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean repairWhitelistJsonNameAttempt(Path file, Path temporary, UUID uuid,
            String name) throws IOException {
        DebugLog.debug("Attempting whitelist JSON file repair");
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
                    ? object.get("name").getAsString()
                    : "";
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
        PendingEntry entry = pendingRepository.findPendingEntry(identifier);
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

    void shutdown() {
        whitelistRepairExecutor.shutdown();
        try {
            if (!whitelistRepairExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                DebugLog.warn("Timed out while waiting for whitelist repairs to finish.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DebugLog.warn("Interrupted while waiting for whitelist repairs to finish.");
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
