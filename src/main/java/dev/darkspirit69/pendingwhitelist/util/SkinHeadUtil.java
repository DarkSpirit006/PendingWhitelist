package dev.darkspirit69.pendingwhitelist.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves player textures in the background and updates open GUI heads when
 * ready.
 */
public final class SkinHeadUtil {

    private static final String GEYSER_SKIN_API = "https://api.geysermc.org/v2/skin/";
    private static final HttpClient GEYSER_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String PROVIDER_CLASS = "net.skinsrestorer.api.SkinsRestorerProvider";
    private static final long SUCCESS_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final long FAILURE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long GENERIC_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long MOJANG_REQUEST_INTERVAL_MILLIS = 1_100L;
    private static final long MOJANG_REQUEST_TIMEOUT_MILLIS = 8_000L;
    private static final long MOJANG_BACKOFF_INITIAL_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long MOJANG_BACKOFF_MAX_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_PERSISTENT_ENTRIES = 2048;
    private static final Object MOJANG_REQUEST_LOCK = new Object();
    private static long nextMojangRequestAt;
    private static long mojangBackoffUntil;
    private static long mojangBackoffMillis = MOJANG_BACKOFF_INITIAL_MILLIS;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentMap<SkinCacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean CACHE_SAVE_QUEUED = new AtomicBoolean();
    private static final Object CACHE_PERSISTENCE_LOCK = new Object();
    private static volatile Path cacheFile;
    private static final Object SKIN_EXECUTOR_LOCK = new Object();
    private static volatile ExecutorService skinExecutor;
    private static volatile boolean running;

    private SkinHeadUtil() {
    }

    /** Starts background skin workers for a newly enabled plugin instance. */
    public static void initialize(PendingWhitelistPlugin plugin) {
        DebugLog.debug("Initializing skin service");
        synchronized (SKIN_EXECUTOR_LOCK) {
            if (skinExecutor == null || skinExecutor.isShutdown() || skinExecutor.isTerminated()) {
                skinExecutor = newSkinExecutor();
            }
            nextMojangRequestAt = 0L;
            mojangBackoffUntil = 0L;
            mojangBackoffMillis = MOJANG_BACKOFF_INITIAL_MILLIS;
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
        return Executors.newSingleThreadExecutor(new SkinThreadFactory());
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
        for (;;) {
            long now = System.currentTimeMillis();
            CacheEntry existing = CACHE.get(key);
            if (existing != null && now < existing.expiresAt()) {
                DebugLog.debug("Skin cache hit: " + normalized);
                return existing.future();
            }

            CacheEntry replacement = new CacheEntry(new CompletableFuture<>(),
                    now + FAILURE_TTL_MILLIS);
            boolean installed;
            synchronized (SKIN_EXECUTOR_LOCK) {
                if (!running) {
                    return CompletableFuture.completedFuture(null);
                }
                if (existing == null) {
                    installed = CACHE.putIfAbsent(key, replacement) == null;
                } else {
                    installed = CACHE.replace(key, existing, replacement);
                }
                if (installed) {
                    ExecutorService executor = ensureSkinExecutor();
                    try {
                        executor.execute(() -> resolveAndComplete(key, uuid, normalized, replacement));
                        attachExpiryRefresh(key, replacement);
                        DebugLog.debug("Queued skin load: " + normalized);
                    } catch (RuntimeException exception) {
                        CACHE.remove(key, replacement);
                        replacement.future().completeExceptionally(exception);
                        if (executor.isShutdown()) {
                            skinExecutor = null;
                        }
                    }
                }
            }

            if (installed) {
                return replacement.future();
            }

            CacheEntry current = CACHE.get(key);
            if (current != null && now < current.expiresAt()) {
                DebugLog.debug("Skin request joined an existing load: " + normalized);
                return current.future();
            }
        }
    }

    private static ExecutorService ensureSkinExecutor() {
        ExecutorService executor = skinExecutor;
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = newSkinExecutor();
            skinExecutor = executor;
        }
        return executor;
    }

    private static void resolveAndComplete(SkinCacheKey key, UUID uuid, String name, CacheEntry entry) {
        try {
            SkinData data = loadSkin(uuid, name);
            entry.future().complete(data);
        } catch (RuntimeException exception) {
            entry.future().completeExceptionally(exception);
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
        if (data == null || !data.hasTexture()) {
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
        if (data == null || !data.hasTexture()) {
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
        String normalizedName = normalizeName(name);
        PlayerProfile profile = normalizedName != null && normalizedName.length() <= 16
                ? Bukkit.createProfile(uuid, normalizedName)
                : Bukkit.createProfile(uuid);
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
     * Refreshes a player's cached skin after a successful join. SkinsRestorer may
     * apply or persist the player's skin slightly after the join event, so the
     * lookup is intentionally delayed by the caller.
     */
    public static void refreshPlayerSkin(Player player) {
        if (player == null) {
            return;
        }
        String name = normalizeName(player.getName());
        UUID uuid = player.getUniqueId();
        if (name == null || uuid == null || !running) {
            return;
        }

        SkinData liveSkin = skinDataFromProfile(player.getPlayerProfile());
        if (liveSkin != null) {
            SkinCacheKey key = new SkinCacheKey(uuid, name.toLowerCase(Locale.ROOT));
            CacheEntry replacement = new CacheEntry(
                    CompletableFuture.completedFuture(liveSkin),
                    System.currentTimeMillis() + SUCCESS_TTL_MILLIS);
            CACHE.put(key, replacement);
            scheduleCacheSave();
            DebugLog.debug("Cached live player skin after join: " + name);
            return;
        }

        CacheEntry existing = CACHE.get(new SkinCacheKey(uuid, name.toLowerCase(Locale.ROOT)));
        if (existing != null && System.currentTimeMillis() < existing.expiresAt()) {
            return;
        }
        getSkinFuture(player, name);
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

    private static SkinData loadSkin(UUID uuid, String name) {
        DebugLog.debug("Loading skin data: name=" + name + ", uuid=" + uuid);

        if (FloodgateUtil.isFloodgateId(uuid)) {
            SkinData bedrockSkin = loadFromGeyser(uuid);
            if (bedrockSkin != null) {
                DebugLog.debug("Skin resolved through Geyser Global API: " + name);
                return bedrockSkin;
            }
            DebugLog.debug("No converted Bedrock skin found for " + name + "; using offline default profile");
            return SkinData.generic();
        }

        SkinData mojangSkin = loadFromMojang(name);
        if (mojangSkin != null) {
            DebugLog.debug("Skin resolved through Mojang: " + name);
            return mojangSkin;
        }

        SkinData skinsRestorerSkin = loadFromSkinsRestorer(uuid, name);
        if (skinsRestorerSkin != null) {
            DebugLog.debug("Skin resolved through SkinsRestorer: " + name);
            return skinsRestorerSkin;
        }

        DebugLog.debug("No Mojang or SkinsRestorer skin found for " + name + "; using offline default profile");
        return SkinData.generic();
    }

    /**
     * Fetches the official Mojang profile while enforcing a global request interval.
     * Cache de-duplication happens before this method, so one player cannot create
     * multiple simultaneous lookups.
     */
    private static SkinData loadFromGeyser(UUID uuid) {
        String xuid = FloodgateUtil.getXuid(uuid);
        if (xuid == null || xuid.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(GEYSER_SKIN_API + xuid);
        } catch (IllegalArgumentException ex) {
            DebugLog.debug("Invalid Bedrock XUID for skin lookup: " + xuid);
            return null;
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = GEYSER_HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                DebugLog.debug("Geyser skin lookup for XUID " + xuid
                        + " returned HTTP " + response.statusCode());
                return null;
            }
            var root = JsonParser.parseString(response.body());
            if (!root.isJsonObject()) {
                return null;
            }
            var object = root.getAsJsonObject();
            String value = object.has("value") && !object.get("value").isJsonNull()
                    ? object.get("value").getAsString() : null;
            String signature = object.has("signature") && !object.get("signature").isJsonNull()
                    ? object.get("signature").getAsString() : null;
            if (value == null || value.isBlank()) {
                return null;
            }
            return new SkinData(value, signature);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            DebugLog.debug("Geyser skin lookup failed for XUID " + xuid + ": "
                    + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            return null;
        }
    }

    private static SkinData skinDataFromProfile(PlayerProfile profile) {
        if (profile == null) {
            return null;
        }
        for (ProfileProperty property : profile.getProperties()) {
            if (property != null && "textures".equals(property.getName())
                    && property.getValue() != null && !property.getValue().isBlank()) {
                return new SkinData(property.getValue(), property.getSignature());
            }
        }
        return null;
    }

    private static SkinData loadFromMojang(String name) {
        synchronized (MOJANG_REQUEST_LOCK) {
            try {
                long now = System.currentTimeMillis();
                long backoffWait = mojangBackoffUntil - now;
                if (backoffWait > 0) {
                    DebugLog.debug("Mojang skin lookup delayed by backoff: " + name);
                    Thread.sleep(backoffWait);
                }

                long waitMillis = nextMojangRequestAt - System.currentTimeMillis();
                if (waitMillis > 0) {
                    Thread.sleep(waitMillis);
                }
                nextMojangRequestAt = System.currentTimeMillis() + MOJANG_REQUEST_INTERVAL_MILLIS;

                PlayerProfile profile = Bukkit.createProfile(name);
                PlayerProfile updated = profile.update().get(MOJANG_REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                SkinData skin = skinDataFromProfile(updated);
                if (skin != null) {
                    mojangBackoffUntil = 0L;
                    mojangBackoffMillis = MOJANG_BACKOFF_INITIAL_MILLIS;
                    return skin;
                }
                mojangBackoffUntil = 0L;
                mojangBackoffMillis = MOJANG_BACKOFF_INITIAL_MILLIS;
                return null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                DebugLog.debug("Mojang skin lookup interrupted for " + name);
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                if (looksLikeMojangRateLimit(exception)) {
                    mojangBackoffUntil = System.currentTimeMillis() + mojangBackoffMillis;
                    mojangBackoffMillis = Math.min(MOJANG_BACKOFF_MAX_MILLIS, mojangBackoffMillis * 2L);
                    DebugLog.debug("Mojang skin lookup rate-limited for " + name
                            + "; backing off for " + (mojangBackoffUntil - System.currentTimeMillis()) + "ms");
                } else {
                    DebugLog.debug("Mojang skin lookup failed for " + name + ": "
                            + exception.getClass().getSimpleName()
                            + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
                }
            }
            return null;
        }
    }

    private static boolean looksLikeMojangRateLimit(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("429") || normalized.contains("too many requests")
                        || normalized.contains("rate limit") || normalized.contains("rate-limit")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static SkinData loadFromSkinsRestorer(UUID uuid, String name) {
        try {
            Plugin skinsRestorer = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
            if (skinsRestorer == null || !skinsRestorer.isEnabled()) {
                return null;
            }

            ClassLoader classLoader = skinsRestorer.getClass().getClassLoader();
            Class<?> provider = Class.forName(PROVIDER_CLASS, true, classLoader);
            Object api = provider.getMethod("get").invoke(null);
            Object storage = api.getClass().getMethod("getPlayerStorage").invoke(api);

            Optional<?> result = invokeOptional(storage, "getSkinForPlayer",
                    new Class<?>[] { UUID.class, String.class }, new Object[] { uuid, name });
            SkinData skin = toSkinData(result);
            if (skin != null) {
                DebugLog.debug("Found SkinsRestorer player skin: " + name);
            }
            return skin;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
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
