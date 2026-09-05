package dev.darkspirit69.pendingwhitelist.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Optional Floodgate integration kept isolated so the plugin also works without
 * Floodgate.
 */
public final class FloodgateUtil {

    private static final String API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
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

    public record Identity(UUID floodgateUuid, String username) {
    }

    public static boolean isFloodgatePlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        initialize();
        return invokeBoolean(isFloodgatePlayerMethod, uuid);
    }

    public static boolean isFloodgateId(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        initialize();
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

    public static Identity resolveIdentifierIdentity(String identifier) {
        String normalized = normalize(identifier);
        if (normalized == null) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(normalized);
        Identity onlineIdentity = resolveOnlineIdentity(online);
        if (onlineIdentity != null) {
            return onlineIdentity;
        }

        UUID uuid = parseUuid(normalized);
        if (uuid != null && isFloodgateId(uuid)) {
            return new Identity(uuid, normalized);
        }

        initialize();
        if (getInstanceMethod == null || getUuidForMethod == null) {
            return null;
        }

        String gamertag = stripPrefix(normalized);
        try {
            Object api = getInstanceMethod.invoke(null);
            Object future = getUuidForMethod.invoke(api, gamertag);
            Method getMethod = future.getClass().getMethod("get", long.class, TimeUnit.class);
            Object value = getMethod.invoke(future, 2L, TimeUnit.SECONDS);
            return value instanceof UUID resolved ? new Identity(resolved, gamertag) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
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
        } catch (ReflectiveOperationException ex) {
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
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
