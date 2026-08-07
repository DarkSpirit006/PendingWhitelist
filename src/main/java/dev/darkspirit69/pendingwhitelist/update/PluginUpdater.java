package dev.darkspirit69.pendingwhitelist.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.Consumer;

public final class PluginUpdater {

    private static final String RELEASES_URL =
            "https://api.github.com/repos/DarkSpirit006/PendingWhitelist/releases/latest";
    private static final String PLUGIN_PREFIX = "PendingWhitelist-";

    private final PendingWhitelistPlugin plugin;
    private final HttpClient httpClient;

    public PluginUpdater(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void scheduleChecks() {
        long intervalTicks = plugin.getUpdateCheckIntervalHours() * 60L * 60L * 20L;
        plugin.getLogger().info("Automatic update checks enabled. Current version: v"
                + plugin.getDescription().getVersion() + ".");
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkForUpdate, 20L * 20L, intervalTicks);
    }

    public void checkNow() {
        plugin.getLogger().info("Manual update check requested by an administrator.");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkForUpdate);
    }

    public void fetchLatestVersion(Consumer<String> resultConsumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String latestVersion = null;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "PendingWhitelist-Plugin-Updater")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                    latestVersion = normalizeVersion(getString(release, "tag_name"));
                    if (latestVersion != null) {
                        plugin.getLogger().info("Latest GitHub version: v" + latestVersion + ".");
                    }
                } else {
                    plugin.getLogger().warning("Could not fetch the latest version (GitHub returned HTTP "
                            + response.statusCode() + ").");
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not fetch the latest PendingWhitelist version: " + ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("Latest version lookup was interrupted.");
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("GitHub returned an unexpected version response: " + ex.getMessage());
            }

            String version = latestVersion;
            Bukkit.getScheduler().runTask(plugin, () -> resultConsumer.accept(version));
        });
    }

    private void checkForUpdate() {
        String currentVersion = plugin.getDescription().getVersion();
        plugin.getLogger().info("Checking GitHub for a newer PendingWhitelist version...");
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "PendingWhitelist-Plugin-Updater")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Could not check for updates (GitHub returned HTTP "
                        + response.statusCode() + ").");
                return;
            }

            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            String tagName = getString(release, "tag_name");
            String latestVersion = normalizeVersion(tagName);
            if (latestVersion == null) {
                plugin.getLogger().warning("GitHub's latest release has an invalid version tag: " + tagName);
                return;
            }
            if (!isNewer(latestVersion, currentVersion)) {
                plugin.getLogger().info("PendingWhitelist is up to date (v" + currentVersion
                        + "; latest release: v" + latestVersion + ").");
                return;
            }

            plugin.getLogger().info("New PendingWhitelist version found: v" + latestVersion
                    + " (installed: v" + currentVersion + "). Downloading it automatically...");
            String assetUrl = findPluginAsset(release, latestVersion);
            if (assetUrl == null) {
                plugin.getLogger().warning("Release v" + latestVersion + " does not contain a plugin JAR.");
                return;
            }

            downloadUpdate(latestVersion, assetUrl);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not check for PendingWhitelist updates: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("PendingWhitelist update check was interrupted.");
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("GitHub returned an unexpected update response: " + ex.getMessage());
        }
    }

    private void downloadUpdate(String version, String assetUrl) throws IOException, InterruptedException {
        Path updateDirectory = plugin.getDataFolder().toPath().getParent().resolve("update");
        Files.createDirectories(updateDirectory);

        Path target = updateDirectory.resolve(PLUGIN_PREFIX + version + ".jar");
        if (Files.exists(target)) {
            plugin.getLogger().info("PendingWhitelist v" + version
                    + " is already staged and will be installed on the next restart.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(assetUrl))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "PendingWhitelist-Plugin-Updater")
                .GET()
                .build();
        Path temporaryFile = Files.createTempFile(updateDirectory, "PendingWhitelist-", ".tmp");

        try {
            HttpResponse<Path> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofFile(temporaryFile));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(temporaryFile);
                plugin.getLogger().warning("Could not download PendingWhitelist v" + version
                        + " (GitHub returned HTTP " + response.statusCode() + ").");
                return;
            }

            try {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("PendingWhitelist v" + version
                    + " downloaded. It will be installed when the server restarts.");
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private String findPluginAsset(JsonObject release, String version) {
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) {
            return null;
        }

        String expectedName = PLUGIN_PREFIX + version + ".jar";
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if (expectedName.equals(getString(asset, "name"))) {
                return getString(asset, "browser_download_url");
            }
        }
        return null;
    }

    private String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }
        String normalized = version.trim();
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches("\\d+\\.\\d+\\.\\d+") ? normalized : null;
    }

    private boolean isNewer(String candidate, String current) {
        String normalizedCurrent = normalizeVersion(current);
        if (normalizedCurrent == null) {
            return false;
        }

        String[] candidateParts = candidate.split("\\.");
        String[] currentParts = normalizedCurrent.split("\\.");
        for (int i = 0; i < 3; i++) {
            int candidatePart = Integer.parseInt(candidateParts[i]);
            int currentPart = Integer.parseInt(currentParts[i]);
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }
}
