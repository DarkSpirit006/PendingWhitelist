package dev.darkspirit69.pendingwhitelist.storage;

import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRoundTripPendingEntries() throws IOException {
        Path file = tempDir.resolve("pending.json");
        PendingFileStore store = new PendingFileStore(file);
        PendingEntry entry = new PendingEntry(
                "123e4567-e89b-12d3-a456-426614174000", "Player", 3, 100L, 300L);

        store.save(List.of(entry));

        assertEquals(List.of(entry), store.load());
    }

    @Test
    void shouldRecoverFromBackupWhenPrimaryIsMalformed() throws IOException {
        Path file = tempDir.resolve("pending.json");
        Path backup = tempDir.resolve("pending.json.bak");
        Files.writeString(backup, "[{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\","
                + "\"name\":\"Player\",\"attempts\":2,\"firstAttempt\":100,\"lastAttempt\":200}]",
                StandardCharsets.UTF_8);
        Files.writeString(file, "not-json", StandardCharsets.UTF_8);

        PendingFileStore store = new PendingFileStore(file);

        List<PendingEntry> loaded = store.load();

        assertEquals(1, loaded.size());
        assertEquals("Player", loaded.get(0).name());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("Player"));
    }

    @Test
    void shouldSkipMalformedArrayEntriesWithoutDroppingValidOnes() throws IOException {
        Path file = tempDir.resolve("pending.json");
        Files.writeString(file, "["
                + "{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\",\"name\":\"Valid\","
                + "\"attempts\":1,\"firstAttempt\":100,\"lastAttempt\":200},"
                + "{\"uuid\":{},\"name\":\"Broken\",\"attempts\":1,\"firstAttempt\":100,\"lastAttempt\":200}"
                + "]", StandardCharsets.UTF_8);

        List<PendingEntry> loaded = new PendingFileStore(file).load();

        assertEquals(1, loaded.size());
        assertEquals("Valid", loaded.get(0).name());
    }

    @Test
    void shouldRejectValidJsonWithUnsupportedRootType() throws IOException {
        Path file = tempDir.resolve("pending.json");
        Files.writeString(file, "42", StandardCharsets.UTF_8);

        PendingFileStore store = new PendingFileStore(file);

        assertThrows(com.google.gson.JsonParseException.class, store::load);
    }

    @Test
    void shouldKeepLegacyMapCompatibility() throws IOException {
        Path file = tempDir.resolve("pending.json");
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Files.writeString(file, "{\"Player\":{"
                + "\"uuid\":\"" + uuid + "\",\"name\":\"OldPlayer\","
                + "\"attempts\":2,\"firstAttempt\":100,\"lastAttempt\":200}}",
                StandardCharsets.UTF_8);

        List<PendingEntry> loaded = new PendingFileStore(file).load();

        assertEquals(List.of(new PendingEntry(uuid.toString(), "Player", 2, 100L, 200L)), loaded);
    }
}
