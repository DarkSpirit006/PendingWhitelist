package dev.darkspirit69.pendingwhitelist.gui;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Collects the data shown in the admin GUI. */
final class WlGuiData {

    private static final int PLAYER_SLOTS = 36;

    private final PendingRepository repository;
    private List<WlGui.AddCandidate> addCandidatesCache;
    private List<WlGui.AddCandidate> addLayoutCache;
    private List<List<WlGui.AddCandidate>> addCandidateGroupsCache;
    private List<WlGui.WhitelistEntry> whitelistEntriesCache;
    private List<WlGui.WhitelistEntry> whitelistLayoutCache;

    WlGuiData(PendingRepository repository) {
        this.repository = repository;
    }

    List<PendingEntry> getPendingEntries() {
        return repository.getPendingEntriesSortedByRecencyDesc();
    }

    int getPendingPageCount() {
        return pageCount(getPendingEntries().size());
    }

    List<WlGui.WhitelistEntry> getWhitelistEntries() {
        if (whitelistEntriesCache != null) {
            return whitelistEntriesCache;
        }
        DebugLog.debug("Preparing whitelist GUI entries");
        List<WlGui.WhitelistEntry> entries = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            UUID uuid = player.getUniqueId();
            String name = normalizeDisplayName(repository.resolveDisplayName(uuid));
            if (name == null) {
                name = uuid.toString();
            }
            if (!name.equals(uuid.toString())) {
                repository.rememberWhitelistName(uuid, name);
            }
            entries.add(new WlGui.WhitelistEntry(player, name));
        }
        entries.sort(Comparator.comparing(entry -> nonNullSortKey(entry.name()), String.CASE_INSENSITIVE_ORDER));
        whitelistEntriesCache = List.copyOf(entries);
        return whitelistEntriesCache;
    }

    List<WlGui.WhitelistEntry> getWhitelistLayout() {
        if (whitelistLayoutCache != null) {
            return whitelistLayoutCache;
        }
        DebugLog.debug("Preparing whitelist GUI layout");
        List<WlGui.WhitelistEntry> onlineBedrock = new ArrayList<>();
        List<WlGui.WhitelistEntry> onlineJava = new ArrayList<>();
        List<WlGui.WhitelistEntry> offlineBedrock = new ArrayList<>();
        List<WlGui.WhitelistEntry> offlineJava = new ArrayList<>();
        for (WlGui.WhitelistEntry entry : getWhitelistEntries()) {
            boolean bedrock = FloodgateUtil.isFloodgateId(entry.player().getUniqueId());
            if (entry.player().isOnline()) {
                if (bedrock) {
                    onlineBedrock.add(entry);
                } else {
                    onlineJava.add(entry);
                }
            } else if (bedrock) {
                offlineBedrock.add(entry);
            } else {
                offlineJava.add(entry);
            }
        }
        Comparator<WlGui.WhitelistEntry> comparator = Comparator.comparing(entry -> nonNullSortKey(entry.name()),
                String.CASE_INSENSITIVE_ORDER);
        List<List<WlGui.WhitelistEntry>> groups = List.of(
                onlineBedrock, onlineJava, offlineBedrock, offlineJava);
        for (List<WlGui.WhitelistEntry> group : groups) {
            group.sort(comparator);
        }
        List<WlGui.WhitelistEntry> layout = new ArrayList<>();
        for (List<WlGui.WhitelistEntry> group : groups) {
            addWhitelistGroupToLayout(layout, group);
        }
        whitelistLayoutCache = Collections.unmodifiableList(layout);
        return whitelistLayoutCache;
    }

    private void addWhitelistGroupToLayout(List<WlGui.WhitelistEntry> layout,
            List<WlGui.WhitelistEntry> group) {
        if (group.isEmpty()) {
            return;
        }
        while (!layout.isEmpty() && layout.size() % 9 != 0) {
            layout.add(null);
        }
        layout.addAll(group);
    }

    List<OfflinePlayer> getWhitelistedPlayers() {
        List<OfflinePlayer> players = new ArrayList<>();
        for (WlGui.WhitelistEntry entry : getWhitelistEntries()) {
            players.add(entry.player());
        }
        return players;
    }

    List<String> getWhitelistedNames() {
        List<String> names = new ArrayList<>();
        for (WlGui.WhitelistEntry entry : getWhitelistEntries()) {
            names.add(entry.name());
        }
        return names;
    }

    WlGui.WhitelistEntry getWhitelistedEntryAtSlot(int page, int slot) {
        if (slot < 0 || slot >= PLAYER_SLOTS) {
            return null;
        }
        List<WlGui.WhitelistEntry> layout = getWhitelistLayout();
        int index = (page - 1) * PLAYER_SLOTS + slot;
        return index >= 0 && index < layout.size() ? layout.get(index) : null;
    }

    int getWhitelistedPageCount() {
        return pageCount(getWhitelistLayout().size());
    }

    int findWhitelistedEntrySlot(int page, WlGui.WhitelistEntry entry) {
        if (entry == null) {
            return -1;
        }
        List<WlGui.WhitelistEntry> layout = getWhitelistLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        for (int index = start; index < end; index++) {
            WlGui.WhitelistEntry current = layout.get(index);
            if (entry.equals(current)) {
                return index - start;
            }
        }
        return -1;
    }

    List<WlGui.WhitelistEntry> getVisibleWhitelistedEntries(int page) {
        List<WlGui.WhitelistEntry> layout = getWhitelistLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        List<WlGui.WhitelistEntry> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WlGui.WhitelistEntry entry = layout.get(index);
            if (entry != null) {
                visible.add(entry);
            }
        }
        return visible;
    }

    List<OfflinePlayer> getVisibleWhitelistedPlayers(int page) {
        List<OfflinePlayer> players = new ArrayList<>();
        for (WlGui.WhitelistEntry entry : getVisibleWhitelistedEntries(page)) {
            players.add(entry.player());
        }
        return players;
    }

    List<WlGui.AddCandidate> getAddCandidates() {
        if (addCandidatesCache != null) {
            return addCandidatesCache;
        }
        DebugLog.debug("Preparing Add GUI candidates");

        List<List<WlGui.AddCandidate>> groups = createAddCandidateGroups();
        addCandidateGroupsCache = List.copyOf(groups);
        List<WlGui.AddCandidate> candidates = new ArrayList<>();
        for (List<WlGui.AddCandidate> group : groups) {
            candidates.addAll(group);
        }
        addCandidatesCache = List.copyOf(candidates);
        return addCandidatesCache;
    }

    private List<List<WlGui.AddCandidate>> createAddCandidateGroups() {
        List<List<WlGui.AddCandidate>> groups = new ArrayList<>();
        List<WlGui.AddCandidate> pendingBedrock = new ArrayList<>();
        List<WlGui.AddCandidate> pendingJava = new ArrayList<>();
        List<WlGui.AddCandidate> onlineBedrock = new ArrayList<>();
        List<WlGui.AddCandidate> onlineJava = new ArrayList<>();
        List<WlGui.AddCandidate> offlineBedrock = new ArrayList<>();
        List<WlGui.AddCandidate> offlineJava = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Set<UUID> whitelistedUuids = new HashSet<>();
        Set<String> whitelistedNames = new HashSet<>();

        collectWhitelistedPlayers(whitelistedUuids, whitelistedNames);
        collectPendingCandidates(pendingBedrock, pendingJava, seen, whitelistedUuids, whitelistedNames);
        collectOnlineCandidates(onlineBedrock, onlineJava, seen, whitelistedUuids, whitelistedNames);
        collectOfflineCandidates(offlineBedrock, offlineJava, seen, whitelistedUuids, whitelistedNames);

        groups.add(pendingBedrock);
        groups.add(pendingJava);
        groups.add(onlineBedrock);
        groups.add(onlineJava);
        groups.add(offlineBedrock);
        groups.add(offlineJava);
        Comparator<WlGui.AddCandidate> comparator = Comparator.comparing(candidate -> nonNullSortKey(candidate.name()),
                String.CASE_INSENSITIVE_ORDER);
        for (List<WlGui.AddCandidate> group : groups) {
            group.sort(comparator);
        }
        return groups;
    }

    private void collectWhitelistedPlayers(Set<UUID> whitelistedUuids, Set<String> whitelistedNames) {
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            UUID uuid = player.getUniqueId();
            whitelistedUuids.add(uuid);
            String name = normalizeDisplayName(repository.resolveDisplayName(uuid));
            if (name != null) {
                whitelistedNames.add(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void collectPendingCandidates(List<WlGui.AddCandidate> pendingBedrock, List<WlGui.AddCandidate> pendingJava,
            Set<UUID> seen, Set<UUID> whitelistedUuids, Set<String> whitelistedNames) {
        for (PendingEntry entry : getPendingEntries()) {
            UUID uuid = parseUuid(entry.uuid());
            String name = pendingName(entry);
            if (!isAddCandidate(name, uuid, whitelistedUuids, whitelistedNames)) {
                continue;
            }
            OfflinePlayer player = uuid == null
                    ? Bukkit.getOfflinePlayerIfCached(name)
                    : Bukkit.getOfflinePlayer(uuid);
            if (player == null) {
                continue;
            }
            UUID playerUuid = player.getUniqueId();
            WlGui.AddCandidate candidate = new WlGui.AddCandidate(
                    player, name, true, isBedrock(playerUuid), player.isOnline());
            seen.add(playerUuid);
            addCandidateToGroup(candidate, pendingBedrock, pendingJava);
        }
    }

    private String pendingName(PendingEntry entry) {
        String name = normalizeDisplayName(entry.name());
        UUID uuid = parseUuid(entry.uuid());
        if (name == null && uuid != null) {
            name = normalizeDisplayName(repository.resolveDisplayName(uuid));
        }
        if (name == null) {
            name = normalizeDisplayName(entry.displayName());
        }
        if (uuid != null && isBedrock(uuid)) {
            name = FloodgateUtil.stripPrefix(name);
        }
        return name;
    }

    private boolean isAddCandidate(String name, UUID uuid, Set<UUID> whitelistedUuids, Set<String> whitelistedNames) {
        return name != null && !name.isBlank() && !"unknown".equalsIgnoreCase(name)
                && !isWhitelisted(uuid, name, whitelistedUuids, whitelistedNames);
    }

    private boolean isBedrock(UUID uuid) {
        return uuid != null && FloodgateUtil.isAvailable() && FloodgateUtil.isFloodgateId(uuid);
    }

    private void addCandidateToGroup(WlGui.AddCandidate candidate, List<WlGui.AddCandidate> bedrock,
            List<WlGui.AddCandidate> java) {
        if (candidate.bedrock()) {
            bedrock.add(candidate);
        } else {
            java.add(candidate);
        }
    }

    private void collectOnlineCandidates(List<WlGui.AddCandidate> onlineBedrock, List<WlGui.AddCandidate> onlineJava,
            Set<UUID> seen, Set<UUID> whitelistedUuids, Set<String> whitelistedNames) {
        for (OfflinePlayer player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String name = normalizeDisplayName(repository.resolveDisplayName(uuid));
            if (!isOtherCandidate(uuid, name, seen, whitelistedUuids, whitelistedNames)) {
                continue;
            }
            WlGui.AddCandidate candidate = new WlGui.AddCandidate(player, name, false,
                    isBedrock(uuid), true);
            addCandidateToGroup(candidate, onlineBedrock, onlineJava);
        }
    }

    private void collectOfflineCandidates(List<WlGui.AddCandidate> offlineBedrock,
            List<WlGui.AddCandidate> offlineJava, Set<UUID> seen, Set<UUID> whitelistedUuids,
            Set<String> whitelistedNames) {
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.isOnline() || !player.hasPlayedBefore()) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            String name = normalizeDisplayName(repository.resolveDisplayName(uuid));
            if (!isOtherCandidate(uuid, name, seen, whitelistedUuids, whitelistedNames)) {
                continue;
            }
            WlGui.AddCandidate candidate = new WlGui.AddCandidate(player, name, false,
                    isBedrock(uuid), false);
            addCandidateToGroup(candidate, offlineBedrock, offlineJava);
        }
    }

    private boolean isOtherCandidate(UUID uuid, String name, Set<UUID> seen,
            Set<UUID> whitelistedUuids, Set<String> whitelistedNames) {
        return name != null && !whitelistedUuids.contains(uuid)
                && !whitelistedNames.contains(name.toLowerCase(Locale.ROOT))
                && seen.add(uuid);
    }

    boolean hasPendingCandidates() {
        return !getGroup(0).isEmpty() || !getGroup(1).isEmpty();
    }

    boolean hasOnlineCandidates() {
        return !getGroup(2).isEmpty() || !getGroup(3).isEmpty();
    }

    boolean hasOfflineCandidates() {
        return !getGroup(4).isEmpty() || !getGroup(5).isEmpty();
    }

    private List<WlGui.AddCandidate> getGroup(int index) {
        getAddCandidates();
        return addCandidateGroupsCache.get(index);
    }

    List<WlGui.AddCandidate> getVisibleAddCandidates(int page) {
        List<WlGui.AddCandidate> layout = getAddLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        List<WlGui.AddCandidate> visible = new ArrayList<>();
        for (int index = start; index < end; index++) {
            WlGui.AddCandidate candidate = layout.get(index);
            if (candidate != null) {
                visible.add(candidate);
            }
        }
        return visible;
    }

    List<WlGui.AddCandidate> getAddLayout() {
        if (addLayoutCache != null) {
            return addLayoutCache;
        }
        DebugLog.debug("Preparing Add GUI layout");
        getAddCandidates();
        List<WlGui.AddCandidate> layout = new ArrayList<>();
        for (List<WlGui.AddCandidate> group : addCandidateGroupsCache) {
            addGroupToLayout(layout, group);
        }
        addLayoutCache = Collections.unmodifiableList(layout);
        return addLayoutCache;
    }

    private void addGroupToLayout(List<WlGui.AddCandidate> layout, List<WlGui.AddCandidate> group) {
        if (group.isEmpty()) {
            return;
        }
        while (!layout.isEmpty() && layout.size() % 9 != 0) {
            layout.add(null);
        }
        layout.addAll(group);
    }

    int findCandidateSlot(int page, WlGui.AddCandidate candidate) {
        if (candidate == null) {
            return -1;
        }
        List<WlGui.AddCandidate> layout = getAddLayout();
        int start = (page - 1) * PLAYER_SLOTS;
        int end = Math.min(start + PLAYER_SLOTS, layout.size());
        for (int index = start; index < end; index++) {
            if (candidate.equals(layout.get(index))) {
                return index - start;
            }
        }
        return -1;
    }

    WlGui.AddCandidate getAddCandidateAtSlot(int page, int slot) {
        int index = (page - 1) * PLAYER_SLOTS + slot;
        List<WlGui.AddCandidate> layout = getAddLayout();
        if (slot < 0 || slot >= PLAYER_SLOTS || index < 0 || index >= layout.size()) {
            return null;
        }
        return layout.get(index);
    }

    int getAddPageCount() {
        return pageCount(getAddLayout().size(), PLAYER_SLOTS);
    }

    void invalidateAddCandidates() {
        DebugLog.debug("Add GUI data cache invalidated");
        addCandidatesCache = null;
        addLayoutCache = null;
        addCandidateGroupsCache = null;
    }

    void invalidateWhitelistCache() {
        DebugLog.debug("Whitelist GUI data cache invalidated");
        whitelistEntriesCache = null;
        whitelistLayoutCache = null;
    }

    private boolean isWhitelisted(UUID uuid, String name, Set<UUID> uuids, Set<String> names) {
        if (uuid != null && uuids.contains(uuid)) {
            return true;
        }
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    private int pageCount(int size) {
        return pageCount(size, PLAYER_SLOTS);
    }

    private int pageCount(int size, int slotsPerPage) {
        return Math.max(1, (size + slotsPerPage - 1) / slotsPerPage);
    }

    private String nonNullSortKey(String value) {
        return value == null ? "" : value;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }



}
