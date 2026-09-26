package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUpdateOnlyTheMatchingWhitelistEntryName() throws IOException {
        Path file = tempDir.resolve("whitelist.json");
        UUID bedrockUuid = UUID.fromString("00000000-0000-0000-0009-01ffcb274032");
        Files.writeString(file, "[{\"uuid\":\"" + bedrockUuid + "\",\"name\":\"unknown\"},"
                + "{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\",\"name\":\"JavaPlayer\"}]",
                StandardCharsets.UTF_8);

        assertTrue(new WhitelistFileStore(file).updateName(bedrockUuid, ".Salmon_bhoi3180"));

        var entries = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(".Salmon_bhoi3180", entries.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("JavaPlayer", entries.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void shouldLeaveTheWhitelistFileUntouchedWhenUuidIsAbsent() throws IOException {
        Path file = tempDir.resolve("whitelist.json");
        String contents = "[{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\",\"name\":\"JavaPlayer\"}]";
        Files.writeString(file, contents, StandardCharsets.UTF_8);

        assertFalse(new WhitelistFileStore(file).updateName(
                UUID.fromString("00000000-0000-0000-0009-01ffcb274032"), ".Salmon_bhoi3180"));

        assertEquals(contents, Files.readString(file, StandardCharsets.UTF_8));
    }
}