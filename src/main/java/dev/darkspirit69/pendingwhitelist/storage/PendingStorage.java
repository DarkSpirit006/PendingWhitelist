package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.scheduler.PurgeTask;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PendingStorage {

    private static final Type MAP_TYPE = new TypeToken<Map<String, PendingEntry>>() {
    }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PendingWhitelistPlugin plugin;
    private final Path storageFile;
    private final Map<String, PendingEntry> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);

    public PendingStorage(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin.getDataFolder().toPath().resolve("pending.json");
    }

    public void loadFromDisk() {
        try {
            if (!Files.exists(storageFile)) {
                Files.createDirectories(storageFile.getParent());
                Files.writeString(storageFile, "{}", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }

            String contents = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (contents == null || contents.isBlank()) {
                pending.clear();
                return;
            }

            Map<String, PendingEntry> loaded = GSON.fromJson(contents, MAP_TYPE);
            if (loaded == null) {
                pending.clear();
                return;
            }
            pending.clear();
            pending.putAll(loaded);
        } catch (IOException | JsonParseException ex) {
            plugin.getLogger().warning("Failed to read pending.json: " + ex.getMessage());
            pending.clear();
            if (!Files.exists(storageFile)) {
                try {
                    Files.createDirectories(storageFile.getParent());
                    Files.writeString(storageFile, "{}", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException ioException) {
                    plugin.getLogger().warning("Failed to recreate pending.json: " + ioException.getMessage());
                }
            }
        }
    }

    public void recordAttempt(String username) {
        long now = Instant.now().toEpochMilli();
        pending.compute(username, (key, existing) -> {
            if (existing == null) {
                return new PendingEntry(1, now, now);
            }
            return new PendingEntry(existing.attempts() + 1, existing.firstAttempt(), now);
        });
        scheduleSave();
    }

    public boolean remove(String username) {
        boolean removed = pending.remove(username) != null;
        if (removed) {
            scheduleSave();
        }
        return removed;
    }

    public boolean addToWhitelist(String username) {
        if (Bukkit.getOfflinePlayer(username).isWhitelisted()) {
            return false;
        }
        Bukkit.getOfflinePlayer(username).setWhitelisted(true);
        return true;
    }

    public boolean isPending(String username) {
        return pending.containsKey(username);
    }

    public List<String> getPendingUsernames() {
        List<String> names = new ArrayList<>(pending.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<String> getPendingUsernamesSortedByRecencyDesc() {
        List<Map.Entry<String, PendingEntry>> entries = new ArrayList<>(pending.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue().lastAttempt(), left.getValue().lastAttempt()));
        List<String> names = new ArrayList<>(entries.size());
        for (Map.Entry<String, PendingEntry> entry : entries) {
            names.add(entry.getKey());
        }
        return names;
    }

    public void purgeExpiredEntries(long cutoffMillis) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PendingEntry> entry : pending.entrySet()) {
            if (entry.getValue().lastAttempt() < cutoffMillis) {
                toRemove.add(entry.getKey());
            }
        }
        for (String username : toRemove) {
            pending.remove(username);
        }
        if (!toRemove.isEmpty()) {
            scheduleSave();
        }
    }

    public int size() {
        return pending.size();
    }

    public void schedulePurgeCheck() {
        new PurgeTask(plugin, this).runTaskTimer(plugin, 20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void scheduleSave() {
        if (saveInProgress.get()) {
            return;
        }
        CompletableFuture.runAsync(this::saveToDisk);
    }

    public void flushSynchronously() {
        saveToDisk();
    }

    private void saveToDisk() {
        if (saveInProgress.getAndSet(true)) {
            return;
        }
        try {
            Files.createDirectories(storageFile.getParent());
            String serialized = GSON.toJson(pending);
            Files.writeString(storageFile, serialized, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pending.json: " + ex.getMessage());
        } finally {
            saveInProgress.set(false);
        }
    }
}
