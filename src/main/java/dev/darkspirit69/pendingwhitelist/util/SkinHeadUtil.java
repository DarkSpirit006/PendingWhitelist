package dev.darkspirit69.pendingwhitelist.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Resolves player textures in the background and updates open GUI heads when ready. */
public final class SkinHeadUtil {

    private static final String PROVIDER_CLASS = "net.skinsrestorer.api.SkinsRestorerProvider";
    private static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final long SUCCESS_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long FAILURE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long RATE_LIMIT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final ConcurrentMap<SkinCacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
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
    public static void initialize() {
        synchronized (SKIN_EXECUTOR_LOCK) {
            if (skinExecutor == null || skinExecutor.isShutdown() || skinExecutor.isTerminated()) {
                skinExecutor = newSkinExecutor();
            }
            running = true;
        }
    }

    /** Stops background skin workers when the plugin is disabled or reloaded. */
    public static void shutdown() {
        CACHE.clear();
        synchronized (SKIN_EXECUTOR_LOCK) {
            running = false;
            if (skinExecutor != null) {
                skinExecutor.shutdownNow();
            }
        }
    }

    private static ExecutorService newSkinExecutor() {
        return Executors.newFixedThreadPool(2, new SkinThreadFactory());
    }

    public static void applyProfile(SkullMeta meta, OfflinePlayer player, String name) {
        if (player == null) {
            return;
        }
        Player online = player.getPlayer();
        if (online != null) {
            meta.setPlayerProfile(online.getPlayerProfile());
            return;
        }
        SkinData data = completedCachedSkin(player.getUniqueId(), name);
        if (data == null) {
            return;
        }
        PlayerProfile profile = createBaseProfile(player, name);
        applySkin(profile, data);
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
        for (WlGui.WhitelistEntry entry : entries) {
            OfflinePlayer player = entry.player();
            String name = normalizeName(entry.name());
            if (player == null || name == null || player.getPlayer() != null) {
                continue;
            }
            CompletableFuture<SkinData> future = getSkinFuture(player.getUniqueId(), name);
            future.thenAccept(data -> applyToWhitelistedInventory(
                    plugin, gui, entry, data));
        }
    }

    public static void prefetch(PendingWhitelistPlugin plugin, WlGui gui,
            List<WlGui.AddCandidate> candidates) {
        for (WlGui.AddCandidate candidate : candidates) {
            OfflinePlayer player = candidate.player();
            String name = normalizeName(candidate.name());
            if (player == null || name == null || player.getPlayer() != null) {
                continue;
            }
            if (gui.findCandidateSlot(candidate) < 0) {
                continue;
            }
            CompletableFuture<SkinData> future = getSkinFuture(player.getUniqueId(), name);
            future.thenAccept(data -> applyToInventory(plugin, gui, player, name, data, candidate));
        }
    }

    private static CompletableFuture<SkinData> getSkinFuture(UUID uuid, String name) {
        String normalized = normalizeName(name);
        if (uuid == null || normalized == null || !running) {
            return CompletableFuture.completedFuture(null);
        }
        SkinCacheKey key = new SkinCacheKey(uuid, normalized.toLowerCase(Locale.ROOT));
        long now = System.currentTimeMillis();
        for (;;) {
            CacheEntry existing = CACHE.get(key);
            if (existing != null && now < existing.expiresAt()) {
                return existing.future();
            }
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
                        () -> loadSkin(uuid, normalized), executor);
            }
            long expiry = now + FAILURE_TTL_MILLIS;
            CacheEntry replacement = new CacheEntry(future, expiry);
            if (existing == null) {
                if (CACHE.putIfAbsent(key, replacement) == null) {
                    attachExpiryRefresh(key, replacement);
                    return future;
                }
            } else if (CACHE.replace(key, existing, replacement)) {
                attachExpiryRefresh(key, replacement);
                return future;
            }
            existing = CACHE.get(key);
            if (existing != null && now < existing.expiresAt()) {
                return existing.future();
            }
        }
    }

    private static void attachExpiryRefresh(SkinCacheKey key, CacheEntry entry) {
        entry.future().whenComplete((data, error) -> {
            long ttl = error != null || data == null ? FAILURE_TTL_MILLIS : SUCCESS_TTL_MILLIS;
            CACHE.computeIfPresent(key, (ignored, current) -> {
                if (current != entry) {
                    return current;
                }
                return new CacheEntry(entry.future(), System.currentTimeMillis() + ttl);
            });
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
                ? Bukkit.createProfile(uuid) : Bukkit.createProfile(uuid, name);
        applySkin(profile, data);
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
        if (FloodgateUtil.isFloodgateId(uuid)) {
            return loadFromSkinsRestorer(uuid, name);
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return extractTextures(online.getPlayerProfile());
        }
        SkinData skinsRestorerSkin = loadFromSkinsRestorer(uuid, name);
        if (skinsRestorerSkin != null) {
            return skinsRestorerSkin;
        }
        if (System.currentTimeMillis() < mojangCooldownUntil) {
            return null;
        }
        if (Bukkit.getOnlineMode()) {
            return loadFromMojangByUuid(uuid);
        }
        return loadFromMojangByName(name);
    }

    private static SkinData loadFromSkinsRestorer(UUID uuid, String name) {
        try {
            Class<?> provider = Class.forName(PROVIDER_CLASS);
            Object api = provider.getMethod("get").invoke(null);
            Object storage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object result;
            try {
                result = storage.getClass()
                        .getMethod("getSkinForPlayer", UUID.class, String.class, boolean.class)
                        .invoke(storage, uuid, name, Bukkit.getOnlineMode());
            } catch (NoSuchMethodException exception) {
                result = storage.getClass()
                        .getMethod("getSkinForPlayer", UUID.class, String.class)
                        .invoke(storage, uuid, name);
            }
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return null;
            }
            Object property = optional.get();
            if (property == null) {
                return null;
            }
            String value = (String) property.getClass().getMethod("getValue").invoke(property);
            String signature = (String) property.getClass().getMethod("getSignature").invoke(property);
            return value == null || value.isBlank() ? null : new SkinData(value, signature);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
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

    private static SkinData loadFromMojangByName(String name) {
        if (!isMojangName(name)) {
            return null;
        }
        HttpResponse<String> uuidResponse = sendMojangRequest(
                MOJANG_PROFILE_URL + encodeName(name));
        if (uuidResponse == null || uuidResponse.statusCode() != 200) {
            return null;
        }
        try {
            JsonObject account = JsonParser.parseString(uuidResponse.body()).getAsJsonObject();
            String onlineUuid = account.get("id").getAsString();
            HttpResponse<String> profileResponse = sendMojangRequest(
                    MOJANG_SESSION_URL + onlineUuid + "?unsigned=false");
            if (profileResponse == null || profileResponse.statusCode() != 200) {
                return null;
            }
            return extractTextures(JsonParser.parseString(profileResponse.body()).getAsJsonObject());
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

    private static boolean isMojangName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
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

    private static SkinData extractTextures(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            String value = Objects.requireNonNull(property.getValue());
            if (!"textures".equals(property.getName()) || value.isBlank()) {
                continue;
            }
            return new SkinData(value, property.getSignature());
        }
        return null;
    }

    private static String encodeName(String name) {
        return name.replace("%", "%25").replace(" ", "%20");
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
    }
}
