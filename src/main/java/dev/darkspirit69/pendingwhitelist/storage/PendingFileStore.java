package dev.darkspirit69.pendingwhitelist.storage;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes pending entries using an atomic temporary-file replacement.
 */
final class PendingFileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageFile;
    private final Path backupFile;

    PendingFileStore(Path storageFile) {
        this.storageFile = storageFile;
        this.backupFile = storageFile.resolveSibling(storageFile.getFileName() + ".bak");
    }

    List<PendingEntry> load() throws IOException, JsonParseException {
        ensureStorageFile();
        try {
            List<PendingEntry> entries = readEntries(storageFile);
            DebugLog.debug("Pending file loaded successfully: entries=" + entries.size());
            return entries;
        } catch (IOException | JsonParseException ex) {
            DebugLog.warn("Primary pending file read failed; checking backup: " + ex.getMessage());
            if (!Files.isRegularFile(backupFile)) {
                throw ex;
            }
            List<PendingEntry> recovered = readEntries(backupFile);
            DebugLog.info("Recovered pending data from backup file: entries=" + recovered.size());
            replaceWithRecovery(storageFile, backupFile);
            return recovered;
        }
    }

    void save(List<PendingEntry> snapshot) throws IOException {
        Files.createDirectories(storageFile.getParent());
        Path temporaryFile = Files.createTempFile(storageFile.getParent(), "pending-", ".tmp");
        try {
            writeAndSync(temporaryFile, GSON.toJson(snapshot));
            if (Files.exists(storageFile)) {
                moveAtomically(storageFile, backupFile);
            }
            moveAtomically(temporaryFile, storageFile);
            DebugLog.debug("Pending file replacement completed successfully");
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void ensureStorageFile() throws IOException {
        Files.createDirectories(storageFile.getParent());
        if (Files.isRegularFile(storageFile)) {
            return;
        }
        if (Files.isRegularFile(backupFile)) {
            moveAtomically(backupFile, storageFile);
            return;
        }
        Files.writeString(storageFile, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private List<PendingEntry> readEntries(Path file) throws IOException, JsonParseException {
        String contents = Files.readString(file, StandardCharsets.UTF_8);
        if (contents.isBlank()) {
            return List.of();
        }
        return parseEntries(contents);
    }

    private void writeAndSync(Path file, String contents) throws IOException {
        Files.writeString(file, contents, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void replaceWithRecovery(Path target, Path source) throws IOException {
        Path recoveryFile = Files.createTempFile(target.getParent(), "pending-recovery-", ".tmp");
        try {
            Files.copy(source, recoveryFile, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(recoveryFile, target);
        } finally {
            Files.deleteIfExists(recoveryFile);
        }
    }

    private List<PendingEntry> parseEntries(String contents) {
        JsonElement root = JsonParser.parseString(contents);
        if (root.isJsonNull()) {
            return List.of();
        }
        if (root.isJsonArray()) {
            return parseArray(root.getAsJsonArray());
        }
        if (root.isJsonObject()) {
            return parseLegacyMap(contents);
        }
        return List.of();
    }

    private List<PendingEntry> parseArray(JsonArray array) {
        List<PendingEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            entries.add(new PendingEntry(
                    normalize(getString(object, "uuid")),
                    normalize(getString(object, "name")),
                    getInt(object, "attempts"),
                    getLong(object, "firstAttempt"),
                    getLong(object, "lastAttempt")));
        }
        return entries;
    }

    private List<PendingEntry> parseLegacyMap(String contents) {
        Map<String, PendingEntry> legacy = GSON.fromJson(contents,
                new TypeToken<Map<String, PendingEntry>>() {
                }.getType());
        if (legacy == null) {
            return List.of();
        }

        List<PendingEntry> entries = new ArrayList<>();
        for (Map.Entry<String, PendingEntry> legacyEntry : legacy.entrySet()) {
            PendingEntry value = legacyEntry.getValue();
            if (value != null) {
                entries.add(new PendingEntry(value.uuid(), legacyEntry.getKey(), value.attempts(),
                        value.firstAttempt(), value.lastAttempt()));
            }
        }
        return entries;
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private int getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0 : element.getAsInt();
    }

    private long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? 0L : element.getAsLong();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
