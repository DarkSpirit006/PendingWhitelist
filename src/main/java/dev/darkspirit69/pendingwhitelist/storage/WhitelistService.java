package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides whitelist operations independently from pending-entry persistence.
 */
final class WhitelistService {

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository pendingRepository;
    private final PendingPersistence persistence;
    private final Map<UUID, String> knownWhitelistNames = new ConcurrentHashMap<>();
    private final AtomicLong knownWhitelistNamesRevision = new AtomicLong();
    private volatile long storedWhitelistNamesLastModified = Long.MIN_VALUE;
    private volatile Map<UUID, String> storedWhitelistNamesCache = Map.of();
    private final AtomicLong savedWhitelistNamesRevision = new AtomicLong();
    private final AtomicBoolean knownWhitelistNamesSaveQueued = new AtomicBoolean();

    WhitelistService(
            PendingWhitelistPlugin plugin, PendingRepository pendingRepository, PendingPersistence persistence) {
        this.plugin = plugin;
        this.pendingRepository = pendingRepository;
        this.persistence = persistence;
        loadKnownWhitelistNames();
    }

    private void invalidateStoredWhitelistNamesCache() {
        storedWhitelistNamesLastModified = Long.MIN_VALUE;
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
        Path file = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
        if (!Files.isRegularFile(file)) {
            storedWhitelistNamesLastModified = Long.MIN_VALUE;
            storedWhitelistNamesCache = Map.of();
            return Map.of();
        }

        long lastModified;
        try {
            lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            return storedWhitelistNamesCache;
        }
        Map<UUID, String> cached = storedWhitelistNamesCache;
        if (lastModified == storedWhitelistNamesLastModified) {
            return cached;
        }

        synchronized (this) {
            if (lastModified == storedWhitelistNamesLastModified) {
                return storedWhitelistNamesCache;
            }
            Map<UUID, String> names = new java.util.LinkedHashMap<>();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    storedWhitelistNamesLastModified = lastModified;
                    storedWhitelistNamesCache = Map.of();
                    return Map.of();
                }
                for (var element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    JsonElement uuidElement = object.get("uuid");
                    JsonElement nameElement = object.get("name");
                    if (uuidElement == null || !uuidElement.isJsonPrimitive()
                            || nameElement == null || !nameElement.isJsonPrimitive()) {
                        continue;
                    }
                    UUID uuid = parseUuid(uuidElement.getAsString());
                    String name = normalizeIdentifier(nameElement.getAsString());
                    if (uuid != null && isStoredWhitelistName(name)) {
                        names.put(uuid, name);
                    }
                }
            } catch (IOException | JsonParseException | UnsupportedOperationException ignored) {
                return storedWhitelistNamesCache;
            }
            Map<UUID, String> result = Map.copyOf(names);
            storedWhitelistNamesLastModified = lastModified;
            storedWhitelistNamesCache = result;
            return result;
        }
    }

    public boolean removeFromWhitelist(String identifier) {
        DebugLog.debug("Removing whitelist identifier: " + identifier);
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        UUID rememberedUuid = findRememberedWhitelistUuid(normalizedIdentifier);
        PendingEntry pendingEntry = pendingRepository.findPendingEntry(normalizedIdentifier);
        UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());
        UUID uuid = parseUuid(normalizedIdentifier);
        if (uuid == null) {
            uuid = rememberedUuid != null ? rememberedUuid : pendingUuid;
        }
        if (uuid == null) {
            uuid = findActualWhitelistedUuid(normalizedIdentifier);
        }

        boolean wasWhitelisted = isWhitelisted(normalizedIdentifier);
        if (!wasWhitelisted) {
            return false;
        }

        UUID actualWhitelistedUuid = findActualWhitelistedUuid(normalizedIdentifier);
        OfflinePlayer offlinePlayer = actualWhitelistedUuid == null
                ? (uuid == null ? Bukkit.getOfflinePlayer(normalizedIdentifier) : Bukkit.getOfflinePlayer(uuid))
                : Bukkit.getOfflinePlayer(actualWhitelistedUuid);
        offlinePlayer.setWhitelisted(false);
        refreshWhitelistState();
        return true;
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

        UUID identifierUuid = parseUuid(normalizedIdentifier);
        if (identifierUuid != null) {
            return isExactUuidWhitelisted(identifierUuid);
        }

        Map<UUID, String> storedNames = readStoredWhitelistNames();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (matchesActualWhitelistName(player, normalizedIdentifier, storedNames)) {
                return true;
            }
        }
        return false;
    }

    public boolean addToWhitelist(UUID uuid, String username) {
        String normalizedUsername = normalizeWhitelistName(uuid, username);
        if (uuid == null || normalizedUsername == null) {
            return false;
        }
        if (FloodgateUtil.isFloodgateId(uuid)) {
            return addFloodgatePlayerToWhitelist(uuid, username);
        }

        if (isExactUuidWhitelisted(uuid) || isActualWhitelistNamePresent(normalizedUsername)) {
            rememberWhitelistName(uuid, normalizedUsername);
            return false;
        }

        Bukkit.getOfflinePlayer(uuid).setWhitelisted(true);
        refreshWhitelistState();

        UUID actualUuid = findActualWhitelistedUuid(normalizedUsername);
        if (actualUuid == null) {
            return false;
        }

        rememberWhitelistName(actualUuid, normalizedUsername);
        invalidateStoredWhitelistNamesCache();
        return true;
    }

    public CompletableFuture<Boolean> addToWhitelistAsync(String identifier) {
        DebugLog.debug("Asynchronously adding whitelist identifier: " + identifier);
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                result.complete(false);
                return;
            }
            try {
                PendingEntry pendingEntry = pendingRepository.findPendingEntry(normalizedIdentifier);
                UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());
                UUID knownFloodgateUuid = pendingUuid != null && FloodgateUtil.isFloodgateId(pendingUuid)
                        ? pendingUuid
                        : findKnownOfflineFloodgateUuid(normalizedIdentifier);
                if (knownFloodgateUuid != null) {
                    addFloodgatePlayerToWhitelistAsync(normalizedIdentifier)
                            .whenComplete((added, error) -> {
                                if (error != null) {
                                    result.completeExceptionally(error);
                                } else {
                                    result.complete(Boolean.TRUE.equals(added));
                                }
                            });
                } else {
                    result.complete(addToWhitelist(normalizedIdentifier));
                }
            } catch (RuntimeException ex) {
                DebugLog.error("Could not add " + normalizedIdentifier + " to the whitelist.", ex);
                result.complete(false);
            }
        });
        return result;
    }

    public CompletableFuture<Boolean> addFloodgatePlayerToWhitelistAsync(String username) {
        String normalizedUsername = normalizeIdentifier(username);
        if (normalizedUsername == null) {
            return CompletableFuture.completedFuture(false);
        }

        UUID directUuid = parseUuid(normalizedUsername);
        if (directUuid != null) {
            if (!FloodgateUtil.isFloodgateId(directUuid)) {
                return CompletableFuture.completedFuture(false);
            }
            String knownName = resolveDisplayName(directUuid);
            return addFloodgatePlayerUsingKnownIdentity(directUuid, knownName);
        }

        PendingEntry pendingEntry = pendingRepository.findPendingEntry(normalizedUsername);
        UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());
        if (pendingEntry != null && pendingUuid != null && FloodgateUtil.isFloodgateId(pendingUuid)) {
            UUID floodgateUuid = pendingUuid;
            return addFloodgatePlayerUsingKnownIdentity(floodgateUuid, pendingEntry.name());
        }

        UUID rememberedUuid = findRememberedWhitelistUuid(normalizedUsername);
        if (rememberedUuid != null && FloodgateUtil.isFloodgateId(rememberedUuid)) {
            return addFloodgatePlayerUsingKnownIdentity(rememberedUuid, normalizedUsername);
        }

        UUID knownOfflineUuid = findKnownOfflineFloodgateUuid(normalizedUsername);
        if (knownOfflineUuid != null) {
            String knownName = resolveDisplayName(knownOfflineUuid);
            return addFloodgatePlayerUsingKnownIdentity(knownOfflineUuid,
                    knownName == null ? normalizedUsername : knownName);
        }

        DebugLog.debug("Resolving Floodgate player for whitelist: " + normalizedUsername);
        return FloodgateUtil.resolveBedrockIdentityAsync(normalizedUsername).thenCompose(identity -> {
            if (identity == null || identity.floodgateUuid() == null) {
                DebugLog.debug("Floodgate could not resolve " + normalizedUsername
                        + "; the player may need to have joined a Geyser server or a Floodgate UUID must be used");
                return CompletableFuture.completedFuture(false);
            }
            String name = FloodgateUtil.stripPrefix(identity.username());
            boolean useName = !identity.useUuidForWhitelist();
            return runFloodgateWhitelistAdd(identity.floodgateUuid(), name, useName);
        });
    }

    public boolean addToWhitelist(String identifier) {
        DebugLog.debug("Adding whitelist identifier: " + identifier);
        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (normalizedIdentifier == null) {
            return false;
        }

        UUID uuid = parseUuid(normalizedIdentifier);
        if (uuid != null) {
            String name = resolveDisplayName(uuid);
            return addToWhitelist(uuid, name);
        }

        PendingEntry pendingEntry = pendingRepository.findPendingEntry(normalizedIdentifier);
        UUID pendingUuid = pendingEntry == null ? null : parseUuid(pendingEntry.uuid());

        if (isActualWhitelistNamePresent(normalizedIdentifier)
                || (pendingUuid != null && isExactUuidWhitelisted(pendingUuid))) {
            UUID existingUuid = findActualWhitelistedUuid(normalizedIdentifier);
            if (existingUuid == null) {
                existingUuid = pendingUuid;
            }
            if (existingUuid != null) {
                rememberWhitelistName(existingUuid, normalizedIdentifier);
            }
            return false;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(normalizedIdentifier);
        offlinePlayer.setWhitelisted(true);
        refreshWhitelistState();

        UUID actualUuid = pendingUuid != null ? pendingUuid : findActualWhitelistedUuid(normalizedIdentifier);
        if (actualUuid != null) {
            rememberWhitelistName(actualUuid, normalizedIdentifier);
        }
        invalidateStoredWhitelistNamesCache();
        return true;
    }

    private boolean isActualWhitelistNamePresent(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return false;
        }
        Map<UUID, String> storedNames = readStoredWhitelistNames();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (matchesActualWhitelistName(player, normalized, storedNames)) {
                return true;
            }
        }
        return false;
    }

    private UUID findActualWhitelistedUuid(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        Map<UUID, String> storedNames = readStoredWhitelistNames();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            if (matchesActualWhitelistName(player, normalized, storedNames)) {
                return player.getUniqueId();
            }
        }
        return null;
    }

    private boolean matchesActualWhitelistName(OfflinePlayer player, String identifier, Map<UUID, String> storedNames) {
        if (player == null || identifier == null) {
            return false;
        }
        String playerName = normalizeIdentifier(player.getName());
        if (namesEqual(identifier, playerName)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        String knownName = normalizeIdentifier(knownWhitelistNames.get(uuid));
        if (namesEqual(identifier, knownName)) {
            return true;
        }

        String storedName = normalizeIdentifier(storedNames.get(uuid));
        return namesEqual(identifier, storedName);
    }

    private boolean namesEqual(String first, String second) {
        return first != null && second != null
                && (first.equalsIgnoreCase(second)
                        || first.equalsIgnoreCase(FloodgateUtil.stripPrefix(second)));
    }

    private CompletableFuture<Boolean> addFloodgatePlayerUsingKnownIdentity(UUID uuid, String username) {
        if (uuid == null || !FloodgateUtil.isFloodgateId(uuid)) {
            return CompletableFuture.completedFuture(false);
        }

        String normalizedName = FloodgateUtil.stripPrefix(normalizeIdentifier(username));
        if (normalizedName == null) {
            return runFloodgateWhitelistAdd(uuid, null, false);
        }

        return runFloodgateWhitelistAdd(uuid, normalizedName, false);
    }

    private static final int WHITELIST_POLL_MAX_ATTEMPTS = 10;
    private static final long WHITELIST_POLL_INTERVAL_TICKS = 4L;

    private CompletableFuture<Boolean> runFloodgateWhitelistAdd(UUID uuid, String username, boolean useUsername) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                result.complete(false);
                return;
            }
            if (isExactUuidWhitelisted(uuid)) {
                rememberFloodgateWhitelistName(uuid, username);
                result.complete(false);
                return;
            }
            try {
                if (addFloodgatePlayerToWhitelistInternal(uuid, username, useUsername)) {
                    result.complete(true);
                } else {
                    pollForWhitelistAddition(uuid, username, useUsername, result, 0);
                }
            } catch (RuntimeException ex) {
                DebugLog.error("Could not add Floodgate player " + uuid + " to the whitelist.", ex);
                result.complete(false);
            }
        });
        return result;
    }

    private void pollForWhitelistAddition(UUID uuid, String username, boolean useUsername,
            CompletableFuture<Boolean> result, int attempt) {
        if (attempt >= WHITELIST_POLL_MAX_ATTEMPTS) {
            if (useUsername) {
                result.complete(false);
            } else {
                fallBackToUuidOnlyAdd(uuid, username, result);
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) {
                result.complete(false);
                return;
            }
            refreshWhitelistState();
            if (isExactUuidWhitelisted(uuid)) {
                DebugLog.debug("Floodgate whitelist add for " + uuid + " confirmed after " + attempt
                        + " poll attempt(s)");
                rememberFloodgateWhitelistName(uuid, username);
                result.complete(true);
                return;
            }
            pollForWhitelistAddition(uuid, username, useUsername, result, attempt + 1);
        }, WHITELIST_POLL_INTERVAL_TICKS);
    }

    private void fallBackToUuidOnlyAdd(UUID uuid, String username, CompletableFuture<Boolean> result) {
        try {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fwhitelist add " + uuid)) {
                result.complete(false);
                return;
            }
            refreshWhitelistState();
            if (!isExactUuidWhitelisted(uuid)) {
                result.complete(false);
                return;
            }
            rememberFloodgateWhitelistName(uuid, username);
            DebugLog.debug("Fell back to UUID-only whitelist add for " + uuid
                    + " after the name-based add did not complete in time.");
            result.complete(true);
        } catch (RuntimeException ex) {
            DebugLog.error("Could not add Floodgate player " + uuid + " to the whitelist (fallback).", ex);
            result.complete(false);
        }
    }

    private UUID findKnownOfflineFloodgateUuid(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }

        String candidate = FloodgateUtil.stripPrefix(normalized);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (FloodgateUtil.isFloodgateId(uuid) && namesEqual(normalized, player.getName())) {
                return uuid;
            }
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player == null) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            if (!FloodgateUtil.isFloodgateId(uuid)) {
                continue;
            }
            String playerName = normalizeIdentifier(player.getName());
            String strippedPlayerName = FloodgateUtil.stripPrefix(playerName);
            if (playerName != null && (playerName.equalsIgnoreCase(normalized)
                    || playerName.equalsIgnoreCase(candidate)
                    || (strippedPlayerName != null && strippedPlayerName.equalsIgnoreCase(candidate)))) {
                return uuid;
            }

            String knownName = normalizeIdentifier(knownWhitelistNames.get(uuid));
            if (knownName != null && (knownName.equalsIgnoreCase(normalized)
                    || knownName.equalsIgnoreCase(candidate))) {
                return uuid;
            }
        }
        return null;
    }

    public boolean addFloodgatePlayerToWhitelist(UUID uuid, String username) {
        return addFloodgatePlayerToWhitelistInternal(uuid, username, false);
    }

    private boolean addFloodgatePlayerToWhitelistInternal(UUID uuid, String username, boolean useUsername) {
        if (uuid == null || !FloodgateUtil.isFloodgateId(uuid)) {
            return false;
        }

        String normalizedUsername = normalizeWhitelistName(uuid, username);
        if (isExactUuidWhitelisted(uuid)) {
            rememberFloodgateWhitelistName(uuid, normalizedUsername);
            return false;
        }

        if (useUsername && normalizedUsername != null) {
            String commandIdentifier = FloodgateUtil.stripPrefix(normalizedUsername);
            if (commandIdentifier != null) {
                // Wait for Floodgate's asynchronous name lookup before using the UUID fallback.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fwhitelist add " + commandIdentifier);
                rememberWhitelistName(uuid, normalizedUsername);
                return false;
            }
        }

        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "fwhitelist add " + uuid)) {
            return false;
        }

        refreshWhitelistState();
        if (!isExactUuidWhitelisted(uuid)) {
            return false;
        }
        rememberFloodgateWhitelistName(uuid, normalizedUsername);
        return true;
    }

    private void rememberFloodgateWhitelistName(UUID uuid, String username) {
        String normalizedUsername = normalizeWhitelistName(uuid, username);
        if (normalizedUsername == null) {
            return;
        }
        rememberWhitelistName(uuid, normalizedUsername);
        try {
            Path whitelistFile = plugin.getServer().getWorldContainer().toPath().resolve("whitelist.json");
            if (new WhitelistFileStore(whitelistFile).updateName(uuid, normalizedUsername)) {
                plugin.getServer().reloadWhitelist();
                refreshWhitelistState();
            }
        } catch (IOException | RuntimeException ex) {
            DebugLog.warn("Could not update the server whitelist name for " + uuid + ": " + ex.getMessage());
        }
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

    private void refreshWhitelistState() {
        invalidateStoredWhitelistNamesCache();
    }

    public boolean setWhitelistEnabled(boolean enabled) {
        boolean currentlyEnabled = plugin.getServer().hasWhitelist();
        if (currentlyEnabled == enabled) {
            return false;
        }
        plugin.getServer().setWhitelist(enabled);
        return true;
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

    private UUID findRememberedWhitelistUuid(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        String candidate = FloodgateUtil.stripPrefix(normalized);
        for (Map.Entry<UUID, String> entry : knownWhitelistNames.entrySet()) {
            String knownName = entry.getValue();
            if (knownName != null && (knownName.equalsIgnoreCase(normalized)
                    || knownName.equalsIgnoreCase(candidate))) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getKnownWhitelistName(UUID uuid) {
        return uuid == null ? null : knownWhitelistNames.get(uuid);
    }

    public String resolveDisplayName(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        boolean floodgate = FloodgateUtil.isAvailable() && FloodgateUtil.isFloodgateId(uuid);
        if (floodgate) {
            FloodgateUtil.Identity identity = FloodgateUtil.resolveOnlineIdentity(uuid);
            if (identity != null) {
                rememberWhitelistName(uuid, identity.username());
                return identity.username();
            }
        }

        Player livePlayer = Bukkit.getPlayer(uuid);
        String liveName = livePlayer == null ? null : normalizeIdentifier(livePlayer.getName());
        if (liveName != null) {
            rememberWhitelistName(uuid, liveName);
            return floodgate ? FloodgateUtil.stripPrefix(liveName) : liveName;
        }

        PendingEntry pendingEntry = pendingRepository.findPendingEntry(uuid.toString());
        if (pendingEntry != null) {
            String pendingName = normalizeIdentifier(pendingEntry.name());
            if (pendingName != null) {
                rememberWhitelistName(uuid, pendingName);
                return floodgate ? FloodgateUtil.stripPrefix(pendingName) : pendingName;
            }
        }

        String knownName = knownWhitelistNames.get(uuid);
        if (knownName != null) {
            return floodgate ? FloodgateUtil.stripPrefix(knownName) : knownName;
        }

        String storedName = readStoredWhitelistNames().get(uuid);
        if (storedName != null) {
            String normalizedStoredName = normalizeIdentifier(storedName);
            if (normalizedStoredName != null) {
                rememberWhitelistName(uuid, normalizedStoredName);
                return floodgate ? FloodgateUtil.stripPrefix(normalizedStoredName) : normalizedStoredName;
            }
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return normalizeIdentifier(player.getName());
    }

    public void rememberWhitelistName(UUID uuid, String name) {
        String normalizedName = normalizeIdentifier(name);
        if (uuid == null || FloodgateUtil.isFloodgateId(uuid)) {
            normalizedName = FloodgateUtil.stripPrefix(normalizedName);
        }
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
                if (FloodgateUtil.isFloodgateId(uuid)) {
                    name = FloodgateUtil.stripPrefix(name);
                }
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
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                DebugLog.debug("Could not delete temporary whitelist names file: "
                        + cleanupException.getMessage());
            }
            return false;
        }
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
            if (name != null && FloodgateUtil.isAvailable()
                    && FloodgateUtil.isFloodgateId(player.getUniqueId())) {
                name = FloodgateUtil.stripPrefix(name);
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
