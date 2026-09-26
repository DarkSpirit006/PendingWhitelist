package dev.darkspirit69.pendingwhitelist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class WhitelistFileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path whitelistFile;

    WhitelistFileStore(Path whitelistFile) {
        this.whitelistFile = whitelistFile;
    }

    boolean updateName(UUID uuid, String name) throws IOException {
        if (!Files.isRegularFile(whitelistFile)) {
            return false;
        }

        JsonElement root = JsonParser.parseString(Files.readString(whitelistFile, StandardCharsets.UTF_8));
        if (!root.isJsonArray()) {
            throw new JsonParseException("Expected a whitelist array");
        }

        boolean changed = false;
        JsonArray entries = root.getAsJsonArray();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement uuidElement = entry.get("uuid");
            if (uuidElement == null || !uuidElement.isJsonPrimitive()) {
                continue;
            }
            try {
                if (uuid.equals(UUID.fromString(uuidElement.getAsString()))) {
                    JsonElement nameElement = entry.get("name");
                    if (nameElement == null || !name.equals(nameElement.getAsString())) {
                        entry.addProperty("name", name);
                        changed = true;
                    }
                    break;
                }
            } catch (IllegalArgumentException ignored) {
                // Leave malformed entries untouched.
            }
        }
        if (!changed) {
            return false;
        }

        Path temporaryFile = Files.createTempFile(whitelistFile.getParent(), "whitelist-", ".tmp");
        try {
            Files.writeString(temporaryFile, GSON.toJson(entries) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, whitelistFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, whitelistFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}