package dev.darkspirit69.pendingwhitelist.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Optional Floodgate integration kept isolated so the plugin also works without
 * Floodgate.
 */
public final class FloodgateUtil {

    private static final String API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String GLOBAL_XUID_API = "https://api.geysermc.org/v2/xbox/xuid/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static volatile Method getInstanceMethod;
    private static volatile Method isFloodgatePlayerMethod;
    private static volatile Method isFloodgateIdMethod;
    private static volatile Method getPlayerMethod;
    private static volatile Method createJavaPlayerIdMethod;
    private static volatile Method getPlayerPrefixMethod;
    private static volatile Method getUuidForMethod;
    private static volatile Method getXuidMethod;
    private static volatile Method getUsernameMethod;
    private static volatile String playerPrefix;
    private static volatile boolean initialized;

    private FloodgateUtil() {
    }

    public record Identity(UUID floodgateUuid, String username, boolean useUuidForWhitelist) {
        public Identity(UUID floodgateUuid, String username) {
            this(floodgateUuid, username, false);
        }
    }

    public static boolean isAvailable() {
        initialize();
        return getInstanceMethod != null && getUuidForMethod != null
                && isFloodgatePlayerMethod != null && isFloodgateIdMethod != null
                && getPlayerMethod != null && createJavaPlayerIdMethod != null;
    }

    public static boolean isFloodgatePlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        initialize();
        return invokeBoolean(isFloodgatePlayerMethod, uuid);
    }

    public static boolean isFloodgateId(UUID uuid) {
        if (uuid == null || !isAvailable()) {
            return false;
        }
        return invokeBoolean(isFloodgateIdMethod, uuid) || looksLikeFloodgateId(uuid);
    }

    private static boolean looksLikeFloodgateId(UUID uuid) {
        return uuid.toString().startsWith("00000000-0000-0000-0009-");
    }

    private static boolean invokeBoolean(Method method, UUID uuid) {
        if (method == null || getInstanceMethod == null) {
            return false;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            return Boolean.TRUE.equals(method.invoke(api, uuid));
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException ex) {
            return false;
        }
    }

    public static Identity resolveOnlineIdentity(Player player) {
        return player == null ? null : resolveOnlineIdentity(player.getUniqueId());
    }

    public static Identity resolveOnlineIdentity(UUID serverUuid) {
        if (serverUuid == null || !isFloodgatePlayer(serverUuid)) {
            return null;
        }
        initialize();
        if (getPlayerMethod == null || getInstanceMethod == null) {
            return null;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            Object floodgatePlayer = getPlayerMethod.invoke(api, serverUuid);
            return buildIdentity(floodgatePlayer);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    public static CompletableFuture<Identity> resolveBedrockIdentityAsync(String identifier) {
        String normalized = normalize(identifier);
        if (normalized == null) {
            return CompletableFuture.completedFuture(null);
        }

        Player online = Bukkit.getPlayerExact(normalized);
        if (online == null) {
            online = Bukkit.getPlayerExact(addPrefix(normalized));
        }
        if (online == null) {
            online = Bukkit.getPlayerExact(stripPrefix(normalized));
        }
        Identity onlineIdentity = resolveOnlineIdentity(online);
        if (onlineIdentity != null) {
            return CompletableFuture.completedFuture(onlineIdentity);
        }

        initialize();
        if (getInstanceMethod == null || getUuidForMethod == null) {
            return CompletableFuture.completedFuture(null);
        }

        String gamertag = stripPrefix(normalized);
        try {
            Object api = getInstanceMethod.invoke(null);
            Object future = getUuidForMethod.invoke(api, gamertag);
            return adaptUuidFuture(future).handle((value, error) -> {
                if (error == null && value instanceof UUID uuid) {
                    return new Identity(uuid, gamertag);
                }
                Throwable cause = error == null ? null : (error.getCause() == null ? error : error.getCause());
                DebugLog.debug("Floodgate cache could not resolve " + gamertag
                        + (cause == null ? "" : ": " + cause.getMessage()));
                return null;
            }).thenCompose(identity -> identity != null
                    ? CompletableFuture.completedFuture(identity)
                    : resolveIdentityFromGlobalApiAsync(gamertag));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            DebugLog.debug("Could not query Floodgate for " + gamertag + ": " + ex.getMessage());
            return resolveIdentityFromGlobalApiAsync(gamertag);
        }
    }

    private static CompletableFuture<Identity> resolveIdentityFromGlobalApiAsync(String gamertag) {
        String encodedGamertag = URLEncoder.encode(gamertag, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri;
        try {
            uri = URI.create(GLOBAL_XUID_API + encodedGamertag);
        } catch (IllegalArgumentException ex) {
            DebugLog.debug("Invalid Bedrock gamertag for Global API lookup: " + gamertag);
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        DebugLog.debug("Global XUID lookup for " + gamertag
                                + " returned HTTP " + response.statusCode());
                        return null;
                    }
                    try {
                        JsonElement xuidElement = JsonParser.parseString(response.body())
                                .getAsJsonObject().get("xuid");
                        if (xuidElement == null || !xuidElement.isJsonPrimitive()) {
                            return null;
                        }
                        long xuid = xuidElement.getAsLong();
                        UUID uuid = createFloodgateUuid(xuid);
                        return uuid == null ? null : new Identity(uuid, gamertag, true);
                    } catch (RuntimeException ex) {
                        DebugLog.debug("Could not parse Global XUID lookup for " + gamertag
                                + ": " + ex.getMessage());
                        return null;
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    DebugLog.debug("Global XUID lookup failed for " + gamertag + ": " + cause.getMessage());
                    return null;
                });
    }

    private static UUID createFloodgateUuid(long xuid) {
        initialize();
        if (getInstanceMethod == null || createJavaPlayerIdMethod == null) {
            return null;
        }
        try {
            Object uuid = createJavaPlayerIdMethod.invoke(null, xuid);
            return uuid instanceof UUID value ? value : null;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static CompletableFuture<Object> adaptUuidFuture(Object future) {
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (future instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().thenApply(value -> value);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return resolveUuidFuture(future);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        });
    }

    private static Object resolveUuidFuture(Object future) throws ReflectiveOperationException {
        if (future == null) {
            return null;
        }
        if (Bukkit.isPrimaryThread()) {
            if (future instanceof CompletableFuture<?> completableFuture) {
                return completableFuture.getNow(null);
            }
            return null;
        }
        Method getMethod = future.getClass().getMethod("get", long.class, TimeUnit.class);
        return getMethod.invoke(future, 2L, TimeUnit.SECONDS);
    }

    private static Identity buildIdentity(Object floodgatePlayer)
            throws IllegalAccessException, InvocationTargetException {
        if (floodgatePlayer == null || getXuidMethod == null || getUsernameMethod == null
                || createJavaPlayerIdMethod == null) {
            return null;
        }
        Object xuidValue = getXuidMethod.invoke(floodgatePlayer);
        Object usernameValue = getUsernameMethod.invoke(floodgatePlayer);
        if (!(xuidValue instanceof Long xuid) || !(usernameValue instanceof String username) || username.isBlank()) {
            return null;
        }
        Object uuidValue = createJavaPlayerIdMethod.invoke(null, xuid.longValue());
        return uuidValue instanceof UUID floodgateUuid ? new Identity(floodgateUuid, username) : null;
    }

    /**
     * Resolves the Bedrock XUID represented by a Floodgate UUID. Floodgate can
     * provide it from its player cache; the UUID encoding is used as a fallback
     * for UUIDs obtained from the Global API.
     */
    public static String getXuid(UUID uuid) {
        if (uuid == null || !looksLikeFloodgateId(uuid)) {
            return null;
        }
        initialize();
        if (getInstanceMethod != null && getPlayerMethod != null && getXuidMethod != null) {
            try {
                Object api = getInstanceMethod.invoke(null);
                Object floodgatePlayer = getPlayerMethod.invoke(api, uuid);
                Object xuid = floodgatePlayer == null ? null : getXuidMethod.invoke(floodgatePlayer);
                if (xuid instanceof String value && !value.isBlank()) {
                    return value;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall back to the standard Floodgate UUID encoding below.
            }
        }
        String compact = uuid.toString().replace("-", "");
        if (!compact.startsWith("00000000000000000009") || compact.length() != 32) {
            return null;
        }
        try {
            long xuid = Long.parseUnsignedLong(compact.substring(16), 16);
            return Long.toUnsignedString(xuid);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String addPrefix(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String prefix = getPlayerPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return normalized;
        }
        if (normalized.startsWith(prefix)) {
            return normalized;
        }
        return prefix + normalized;
    }

    public static String stripPrefix(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String prefix = getPlayerPrefix();
        if (prefix != null && !prefix.isEmpty() && normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    public static String getPlayerPrefix() {
        initialize();
        return playerPrefix;
    }

    /** Clears cached reflection state so a plugin reload can re-detect Floodgate. */
    public static synchronized void reset() {
        getInstanceMethod = null;
        isFloodgatePlayerMethod = null;
        isFloodgateIdMethod = null;
        getPlayerMethod = null;
        createJavaPlayerIdMethod = null;
        getPlayerPrefixMethod = null;
        getUuidForMethod = null;
        getXuidMethod = null;
        getUsernameMethod = null;
        playerPrefix = null;
        initialized = false;
    }

    private static String readPlayerPrefix() {
        if (getPlayerPrefixMethod == null || getInstanceMethod == null) {
            return null;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            Object prefix = getPlayerPrefixMethod.invoke(api);
            return prefix instanceof String value ? value : null;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            getInstanceMethod = apiClass.getMethod("getInstance");
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            isFloodgateIdMethod = apiClass.getMethod("isFloodgateId", UUID.class);
            getPlayerMethod = apiClass.getMethod("getPlayer", UUID.class);
            createJavaPlayerIdMethod = apiClass.getMethod("createJavaPlayerId", long.class);
            getPlayerPrefixMethod = apiClass.getMethod("getPlayerPrefix");
            getUuidForMethod = apiClass.getMethod("getUuidFor", String.class);
            Class<?> playerClass = getPlayerMethod.getReturnType();
            getXuidMethod = playerClass.getMethod("getXuid");
            getUsernameMethod = playerClass.getMethod("getUsername");
            playerPrefix = readPlayerPrefix();
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            getInstanceMethod = null;
            isFloodgatePlayerMethod = null;
            isFloodgateIdMethod = null;
            getPlayerMethod = null;
            createJavaPlayerIdMethod = null;
            getPlayerPrefixMethod = null;
            getUuidForMethod = null;
            getXuidMethod = null;
            getUsernameMethod = null;
            playerPrefix = null;
            DebugLog.debug("Floodgate integration unavailable: " + ex.getClass().getSimpleName());
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
