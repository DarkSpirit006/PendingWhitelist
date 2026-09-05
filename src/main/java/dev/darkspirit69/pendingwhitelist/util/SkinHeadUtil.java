package dev.darkspirit69.pendingwhitelist.util;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves player textures in the background and updates open GUI heads when
 * ready.
 */
public final class SkinHeadUtil {

    private static final String PROVIDER_CLASS = "net.skinsrestorer.api.SkinsRestorerProvider";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final long SUCCESS_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final long FAILURE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long GENERIC_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long RATE_LIMIT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_PERSISTENT_ENTRIES = 2048;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentMap<SkinCacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean CACHE_SAVE_QUEUED = new AtomicBoolean();
    private static final Object CACHE_PERSISTENCE_LOCK = new Object();
    private static volatile Path cacheFile;
    private static final Object SKIN_EXECUTOR_LOCK = new Object();
    private static volatile ExecutorService skinExecutor;
    private static volatile boolean running;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    private static final Object MOJANG_REQUEST_LOCK = new Object();
    private static long lastMojangRequestAt;
    private static volatile long mojangCooldownUntil;

    private SkinHeadUtil() {
    }

    /** Starts background skin workers for a newly enabled plugin instance. */
    public static void initialize(PendingWhitelistPlugin plugin) {
        DebugLog.debug("Initializing skin service");
        synchronized (SKIN_EXECUTOR_LOCK) {
            if (skinExecutor == null || skinExecutor.isShutdown() || skinExecutor.isTerminated()) {
                skinExecutor = newSkinExecutor();
            }
            cacheFile = plugin.getDataFolder().toPath().resolve("skin-cache.json");
            running = true;
        }
        loadPersistentCache();
    }

    /** Stops background skin workers when the plugin is fully disabled. */
    public static void shutdown() {
        DebugLog.debug("Shutting down skin service");
        persistCacheSynchronously();
        synchronized (SKIN_EXECUTOR_LOCK) {
            running = false;
            if (skinExecutor != null) {
                skinExecutor.shutdownNow();
            }
        }
        CACHE.clear();
        cacheFile = null;
    }

    private static void loadPersistentCache() {
        Path file = cacheFile;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            SkinCacheFile stored = GSON.fromJson(json, SkinCacheFile.class);
            if (stored == null || stored.entries == null) {
                return;
            }
            long now = System.currentTimeMillis();
            int loaded = 0;
            for (StoredSkin entry : stored.entries) {
                if (entry == null || entry.uuid == null || entry.name == null || entry.expiresAt <= now) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(entry.uuid);
                    String normalized = normalizeName(entry.name);
                    if (normalized == null) {
                        continue;
                    }
                    if (entry.value != null && !entry.value.isBlank()) {
                        Base64.getDecoder().decode(entry.value);
                    }
                    SkinData data = new SkinData(entry.value, entry.signature);
                    CACHE.put(new SkinCacheKey(uuid, normalized.toLowerCase(Locale.ROOT)),
                            new CacheEntry(CompletableFuture.completedFuture(data), entry.expiresAt));
                    loaded++;
                    if (loaded >= MAX_PERSISTENT_ENTRIES) {
                        break;
                    }
                } catch (IllegalArgumentException ignored) {
                    DebugLog.debug("Skipping invalid persistent skin-cache entry");
                }
            }
            DebugLog.debug("Loaded " + loaded + " persistent skin cache entries");
            if (loaded == 0) {
                deleteEmptyCacheFile(file);
            }
        } catch (IOException | RuntimeException exception) {
            DebugLog.warn("Could not load persistent skin cache: " + exception.getMessage());
        }
    }

    private static void deleteEmptyCacheFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            DebugLog.debug("Could not remove empty skin cache: " + exception.getMessage());
        }
    }

    private static void scheduleCacheSave() {
        if (!running || cacheFile == null || CACHE_SAVE_QUEUED.getAndSet(true)) {
            return;
        }
        ExecutorService executor = skinExecutor;
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            CACHE_SAVE_QUEUED.set(false);
            return;
        }
        try {
            CompletableFuture.delayedExecutor(250L, TimeUnit.MILLISECONDS, executor).execute(() -> {
                try {
                    persistCache();
                } finally {
                    CACHE_SAVE_QUEUED.set(false);
                }
            });
        } catch (RuntimeException exception) {
            CACHE_SAVE_QUEUED.set(false);
        }
    }

    private static void persistCacheSynchronously() {
        CACHE_SAVE_QUEUED.set(false);
        persistCache();
    }

    private static void persistCache() {
        synchronized (CACHE_PERSISTENCE_LOCK) {
            Path file = cacheFile;
            if (file == null) {
                return;
            }
            long now = System.currentTimeMillis();
            List<StoredSkin> entries = new ArrayList<>();
            for (var entry : CACHE.entrySet()) {
                CacheEntry cacheEntry = entry.getValue();
                if (cacheEntry.expiresAt() <= now || !cacheEntry.future().isDone()
                        || cacheEntry.future().isCompletedExceptionally()) {
                    continue;
                }
                SkinData data = cacheEntry.future().getNow(null);
                if (data == null) {
                    continue;
                }
                entries.add(new StoredSkin(entry.getKey().uuid().toString(), entry.getKey().name(),
                        data.value(), data.signature(), cacheEntry.expiresAt()));
            }
            entries.sort((left, right) -> Long.compare(right.expiresAt, left.expiresAt));
            if (entries.size() > MAX_PERSISTENT_ENTRIES) {
                entries = new ArrayList<>(entries.subList(0, MAX_PERSISTENT_ENTRIES));
            }
            if (entries.isEmpty()) {
                deleteEmptyCacheFile(file);
                return;
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(temp, GSON.toJson(new SkinCacheFile(1, entries)),
                        StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                DebugLog.debug("Persisted " + entries.size() + " skin cache entries");
            } catch (IOException exception) {
                DebugLog.warn("Could not persist skin cache: " + exception.getMessage());
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Ignore cleanup failure.
                }
            }
        }
    }

    private static ExecutorService newSkinExecutor() {
        return Executors.newFixedThreadPool(2, new SkinThreadFactory());
    }

    public static void applyProfile(SkullMeta meta, OfflinePlayer player, String name) {
        DebugLog.debug("Applying skin profile: name=" + name + ", uuid="
                + (player == null ? null : player.getUniqueId()));
        if (player == null) {
            return;
        }
        Player online = player.getPlayer();
        if (online != null) {
            meta.setPlayerProfile(online.getPlayerProfile());
            return;
        }
        SkinData data = completedCachedSkin(player.getUniqueId(), name);
        PlayerProfile profile = createBaseProfile(player, name);
        if (data != null && data.hasTexture()) {
            applySkin(profile, data);
        }
        meta.setPlayerProfile(profile);
    }

    private static PlayerProfile createBaseProfile(OfflinePlayer player, String name) {
        UUID uuid = player.getUniqueId();
        if (FloodgateUtil.isFloodgateId(uuid)) {
            return Bukkit.createProfile(uuid);
        }
        String normalized = normalizeName(name);
        if (normalized != null && normalized.length() <= 16) {
            return Bukkit.createProfile(uuid, normalized);
        }
        return Bukkit.createProfile(uuid);
    }

    public static void prefetchWhitelisted(PendingWhitelistPlugin plugin, WlGui gui,
            List<WlGui.WhitelistEntry> entries) {
        DebugLog.debug("Prefetching whitelisted skins: entries=" + entries.size());
        for (WlGui.WhitelistEntry entry : entries) {
            OfflinePlayer player = entry.player();
            String name = normalizeName(entry.name());
            if (player == null || name == null || player.getPlayer() != null) {
                continue;
            }
            CompletableFuture<SkinData> future = getSkinFuture(player, name);
            future.thenAccept(data -> applyToWhitelistedInventory(
                    plugin, gui, entry, data));
        }
    }

    public static void prefetch(PendingWhitelistPlugin plugin, WlGui gui,
            List<WlGui.AddCandidate> candidates) {
        DebugLog.debug("Prefetching Add GUI skins: candidates=" + candidates.size());
        for (WlGui.AddCandidate candidate : candidates) {
            OfflinePlayer player = candidate.player();
            String name = normalizeName(candidate.name());
            if (player == null || name == null || player.getPlayer() != null) {
                continue;
            }
            if (gui.findCandidateSlot(candidate) < 0) {
                continue;
            }
            CompletableFuture<SkinData> future = getSkinFuture(player, name);
            future.thenAccept(data -> applyToInventory(plugin, gui, player, name, data, candidate));
        }
    }

    private static CompletableFuture<SkinData> getSkinFuture(OfflinePlayer player, String name) {
        String normalized = normalizeName(name);
        if (player == null || normalized == null || !running) {
            return CompletableFuture.completedFuture(null);
        }
        UUID uuid = player.getUniqueId();
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        SkinCacheKey key = new SkinCacheKey(uuid, normalized.toLowerCase(Locale.ROOT));
        long now = System.currentTimeMillis();
        for (;;) {
            CacheEntry existing = CACHE.get(key);
            if (existing != null && now < existing.expiresAt()) {
                DebugLog.debug("Skin cache hit: " + normalized);
                return existing.future();
            }
            DebugLog.debug("Skin cache miss/expired: " + normalized);
            SkinData serverProfileSkin = null;
            boolean onlineMode = Bukkit.getOnlineMode();
            CompletableFuture<SkinData> future;
            synchronized (SKIN_EXECUTOR_LOCK) {
                if (!running) {
                    return CompletableFuture.completedFuture(null);
                }
                ExecutorService executor = skinExecutor;
                if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                    skinExecutor = newSkinExecutor();
                    executor = skinExecutor;
                }
                future = CompletableFuture.supplyAsync(
                        () -> loadSkin(uuid, normalized, serverProfileSkin, onlineMode), executor);
            }
            long expiry = now + FAILURE_TTL_MILLIS;
            CacheEntry replacement = new CacheEntry(future, expiry);
            if (existing == null) {
                if (CACHE.putIfAbsent(key, replacement) == null) {
                    DebugLog.debug("Started skin load: " + normalized);
                    attachExpiryRefresh(key, replacement);
                    return future;
                }
            } else if (CACHE.replace(key, existing, replacement)) {
                attachExpiryRefresh(key, replacement);
                return future;
            }
            existing = CACHE.get(key);
            if (existing != null && now < existing.expiresAt()) {
                DebugLog.debug("Skin request joined an existing load: " + normalized);
                return existing.future();
            }
        }
    }

    private static void attachExpiryRefresh(SkinCacheKey key, CacheEntry entry) {
        entry.future().whenComplete((data, error) -> {
            long ttl = error != null || data == null
                    ? FAILURE_TTL_MILLIS
                    : (data.hasTexture() ? SUCCESS_TTL_MILLIS : GENERIC_TTL_MILLIS);
            DebugLog.debug("Skin load completed: success=" + (data != null) + ", cacheTtlMs=" + ttl);
            CACHE.computeIfPresent(key, (ignored, current) -> {
                if (current != entry) {
                    return current;
                }
                return new CacheEntry(entry.future(), System.currentTimeMillis() + ttl);
            });
            if (data != null && error == null) {
                scheduleCacheSave();
            }
        });
    }

    private static void applyToWhitelistedInventory(PendingWhitelistPlugin plugin, WlGui gui,
            WlGui.WhitelistEntry entry, SkinData data) {
        if (data == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inventory = gui.getInventory();
            if (inventory == null || gui.getView() != WlGui.View.WHITELISTED) {
                return;
            }
            int slot = gui.findWhitelistedEntrySlot(entry);
            if (slot >= 0) {
                applyTextureToSlot(inventory, slot, entry.player().getUniqueId(),
                        entry.name(), data);
            }
        });
    }

    private static void applyToInventory(PendingWhitelistPlugin plugin, WlGui gui,
            OfflinePlayer player, String name, SkinData data, WlGui.AddCandidate candidate) {
        if (data == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inventory = gui.getInventory();
            if (inventory == null || gui.getView() != WlGui.View.ADD) {
                return;
            }
            int slot = gui.findCandidateSlot(candidate);
            if (slot >= 0) {
                applyTextureToSlot(inventory, slot, player.getUniqueId(), name, data);
            }
        });
    }

    private static void applyTextureToSlot(Inventory inventory, int slot, UUID uuid,
            String name, SkinData data) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || !(stack.getItemMeta() instanceof SkullMeta meta)) {
            return;
        }
        PlayerProfile profile = FloodgateUtil.isFloodgateId(uuid)
                || name == null || name.length() > 16
                        ? Bukkit.createProfile(uuid)
                        : Bukkit.createProfile(uuid, name);
        if (data.hasTexture()) {
            applySkin(profile, data);
        }
        meta.setPlayerProfile(profile);
        stack.setItemMeta(meta);
        inventory.setItem(slot, stack);
    }

    private static final class SkinThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "PendingWhitelist-Skin-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Removes every cached skin for a player, including entries created under an
     * older name.
     */
    public static void invalidate(UUID uuid) {
        if (uuid == null) {
            return;
        }
        CACHE.keySet().removeIf(key -> uuid.equals(key.uuid()));
        scheduleCacheSave();
        DebugLog.debug("Invalidated skin cache for uuid=" + uuid);
    }

    private static SkinData completedCachedSkin(UUID uuid, String name) {
        String normalized = normalizeName(name);
        if (uuid == null || normalized == null) {
            return null;
        }
        CacheEntry entry = CACHE.get(new SkinCacheKey(uuid, normalized.toLowerCase(Locale.ROOT)));
        if (entry == null || !entry.future().isDone() || entry.future().isCompletedExceptionally()) {
            return null;
        }
        return entry.future().getNow(null);
    }

    private static SkinData loadSkin(UUID uuid, String name, SkinData serverProfileSkin, boolean onlineMode) {
        DebugLog.debug("Loading skin data: name=" + name + ", uuid=" + uuid);

        if (serverProfileSkin != null) {
            DebugLog.debug("Skin resolved from the server's stored player profile: " + name);
            return serverProfileSkin;
        }

        if (FloodgateUtil.isFloodgateId(uuid)) {
            SkinData skinsRestorerSkin = loadFromSkinsRestorer(uuid, name, onlineMode);
            if (skinsRestorerSkin != null) {
                DebugLog.debug("Skin resolved through SkinsRestorer: " + name);
                return skinsRestorerSkin;
            }
            return SkinData.generic();
        }

        SkinData skinsRestorerSkin = loadFromSkinsRestorer(uuid, name, onlineMode);
        if (skinsRestorerSkin != null) {
            DebugLog.debug("Skin resolved through SkinsRestorer: " + name);
            return skinsRestorerSkin;
        }

        if (!onlineMode) {
            DebugLog.debug("Offline-mode server: using generic profile for " + name);
            return SkinData.generic();
        }
        if (System.currentTimeMillis() < mojangCooldownUntil) {
            return null;
        }
        DebugLog.debug("Falling back to Mojang UUID lookup: " + uuid);
        return loadFromMojangByUuid(uuid);
    }

    private static SkinData loadFromSkinsRestorer(UUID uuid, String name, boolean onlineMode) {
        try {
            Plugin skinsRestorer = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
            if (skinsRestorer == null || !skinsRestorer.isEnabled()) {
                return null;
            }
            ClassLoader classLoader = skinsRestorer.getClass().getClassLoader();
            Class<?> provider = Class.forName(PROVIDER_CLASS, true, classLoader);
            Object api = provider.getMethod("get").invoke(null);
            Object storage = api.getClass().getMethod("getPlayerStorage").invoke(api);

            Optional<?> linkedResult = invokeOptional(storage, "getSkinOfPlayer",
                    new Class<?>[] { UUID.class }, new Object[] { uuid });
            SkinData linkedSkin = toSkinData(linkedResult);
            if (linkedSkin != null) {
                DebugLog.debug("Found directly linked SkinsRestorer skin: " + name);
                return linkedSkin;
            }

            if (!onlineMode) {
                DebugLog.debug("No linked SkinsRestorer skin found for offline player: " + name);
                return null;
            }

            Optional<?> joinResult = invokeOptional(storage, "getSkinForPlayer",
                    new Class<?>[] { UUID.class, String.class, boolean.class },
                    new Object[] { uuid, name, true });
            SkinData joinSkin = toSkinData(joinResult);
            if (joinSkin != null) {
                DebugLog.debug("Found SkinsRestorer join skin: " + name);
                return joinSkin;
            }

            Optional<?> legacyResult = invokeOptional(storage, "getSkinForPlayer",
                    new Class<?>[] { UUID.class, String.class }, new Object[] { uuid, name });
            SkinData legacySkin = toSkinData(legacyResult);
            if (legacySkin != null) {
                DebugLog.debug("Found SkinsRestorer legacy skin: " + name);
                return legacySkin;
            }

            Object skinStorage = api.getClass().getMethod("getSkinStorage").invoke(api);
            Optional<?> playerSkinResult = invokeOptional(skinStorage, "getPlayerSkin",
                    new Class<?>[] { String.class, boolean.class }, new Object[] { name, false });
            SkinData playerSkin = toMojangSkinData(playerSkinResult);
            if (playerSkin != null) {
                DebugLog.debug("Found SkinsRestorer cached player skin: " + name);
            }
            return playerSkin;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DebugLog.debug("SkinsRestorer lookup failed for " + name + ": "
                    + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
            return null;
        }
    }

    private static Optional<?> invokeOptional(Object target, String methodName, Class<?>[] parameterTypes,
            Object[] arguments) throws ReflectiveOperationException {
        Object result = target.getClass().getMethod(methodName, parameterTypes).invoke(target, arguments);
        return result instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static SkinData toSkinData(Optional<?> result) throws ReflectiveOperationException {
        if (result.isEmpty()) {
            return null;
        }
        Object property = result.get();
        if (property == null) {
            return null;
        }
        String value = (String) property.getClass().getMethod("getValue").invoke(property);
        String signature = (String) property.getClass().getMethod("getSignature").invoke(property);
        return value == null || value.isBlank() ? null : new SkinData(value, signature);
    }

    private static SkinData toMojangSkinData(Optional<?> result) throws ReflectiveOperationException {
        if (result.isEmpty()) {
            return null;
        }
        Object skinResult = result.get();
        if (skinResult == null) {
            return null;
        }
        Object property = skinResult.getClass().getMethod("getSkinProperty").invoke(skinResult);
        if (property == null) {
            return null;
        }
        String value = (String) property.getClass().getMethod("getValue").invoke(property);
        String signature = (String) property.getClass().getMethod("getSignature").invoke(property);
        return value == null || value.isBlank() ? null : new SkinData(value, signature);
    }

    private static SkinData loadFromMojangByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        HttpResponse<String> response = sendMojangRequest(
                MOJANG_SESSION_URL + uuid.toString().replace("-", "") + "?unsigned=false");
        if (response == null || response.statusCode() != 200) {
            return null;
        }
        try {
            return extractTextures(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static HttpResponse<String> sendMojangRequest(String url) {
        synchronized (MOJANG_REQUEST_LOCK) {
            long now = System.currentTimeMillis();
            if (now < mojangCooldownUntil) {
                return null;
            }
            long wait = 1000L - (now - lastMojangRequestAt);
            if (wait > 0L) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            lastMojangRequestAt = System.currentTimeMillis();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                mojangCooldownUntil = System.currentTimeMillis() +
                        (response.statusCode() == 429 ? RATE_LIMIT_TTL_MILLIS : FAILURE_TTL_MILLIS);
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException exception) {
            mojangCooldownUntil = System.currentTimeMillis() + FAILURE_TTL_MILLIS;
            return null;
        }
    }

    private static SkinData extractTextures(JsonObject profile) {
        JsonArray properties = profile.getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String value = property.get("value").getAsString();
            String signature = property.has("signature") ? property.get("signature").getAsString() : null;
            if (value.isBlank()) {
                return null;
            }
            try {
                Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException exception) {
                return null;
            }
            return new SkinData(value, signature);
        }
        return null;
    }

    private static void applySkin(PlayerProfile profile, SkinData data) {
        String value = Objects.requireNonNull(data.value());
        if (data.signature() == null) {
            profile.setProperty(new ProfileProperty("textures", value));
            return;
        }
        String signature = Objects.requireNonNull(data.signature());
        profile.setProperty(new ProfileProperty("textures", value, signature));
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? null : name;
    }

    private record SkinCacheKey(UUID uuid, String name) {
    }

    private record CacheEntry(CompletableFuture<SkinData> future, long expiresAt) {
    }

    private record SkinData(String value, String signature) {

        static SkinData generic() {
            return new SkinData(null, null);
        }

        boolean hasTexture() {
            return value != null && !value.isBlank();
        }
    }

    private record SkinCacheFile(int version, List<StoredSkin> entries) {
    }

    private record StoredSkin(String uuid, String name, String value, String signature, long expiresAt) {
    }
}
