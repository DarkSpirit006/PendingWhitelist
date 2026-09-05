package dev.darkspirit69.pendingwhitelist.storage;

import dev.darkspirit69.pendingwhitelist.model.PendingEntry;

import java.util.List;
import java.util.UUID;

/**
 * Application-facing contract for pending whitelist data and whitelist
 * operations.
 * Implementations own persistence and platform-specific data access.
 */
public interface PendingRepository {

    void loadFromDisk();

    void recordAttempt(String username, UUID uuid);

    boolean removeFromWhitelist(String identifier);

    boolean removePendingOnly(String identifier);

    void removePendingEntries(List<PendingEntry> entries);

    boolean isWhitelisted(String identifier);

    boolean addToWhitelist(UUID uuid, String username);

    boolean addToWhitelist(String identifier);

    boolean addFloodgatePlayerToWhitelist(UUID uuid, String username);

    boolean isFloodgateUuid(String identifier);

    String getKnownWhitelistName(UUID uuid);

    void rememberWhitelistName(UUID uuid, String name);

    void repairWhitelistJsonName(UUID uuid, String name);

    boolean isPending(String identifier);

    PendingEntry findPendingEntry(String identifier);

    String resolveDisplayNameForIdentifier(String identifier);

    List<String> getPendingUsernames();

    String resolveWhitelistedUuid(String name);

    List<String> getWhitelistedUsernames();

    List<PendingEntry> getPendingEntriesSortedByRecencyDesc();

    List<String> getPendingUsernamesSortedByRecencyDesc();

    int purgeExpiredEntries(long cutoffMillis);

    int size();

    void schedulePurgeCheck();

    void scheduleSave();

    void flushSynchronously();

    void shutdown();

}
