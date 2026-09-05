package dev.darkspirit69.pendingwhitelist.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Performs the network request to Modrinth and converts the response to domain
 * data.
 */
public final class ModrinthClient {

    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/pending-whitelist/version";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final PendingWhitelistPlugin plugin;
    private final HttpClient httpClient;
    private final VersionComparator versionComparator;

    public ModrinthClient(PendingWhitelistPlugin plugin, VersionComparator versionComparator) {
        this.plugin = plugin;
        this.versionComparator = versionComparator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public UpdateResult fetchLatestRelease() {
        DebugLog.debug("Starting Modrinth request: " + VERSIONS_URL);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(VERSIONS_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "PendingWhitelist/" + plugin.getInstalledVersion())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            DebugLog.debug("Modrinth response status: " + response.statusCode());
            if (response.statusCode() != 200) {
                DebugLog.warn("Could not check Modrinth for updates (HTTP " + response.statusCode() + ").");
                return UpdateResult.empty();
            }
            UpdateResult result = parseVersions(JsonParser.parseString(response.body()).getAsJsonArray());
            DebugLog.debug("Modrinth parsed latest release: " + result.latestVersion());
            return result;
        } catch (IOException ex) {
            DebugLog.warn("Could not check Modrinth for PendingWhitelist updates: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DebugLog.warn("Modrinth update check was interrupted.");
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
            DebugLog.warn("Modrinth returned an unexpected update response: " + ex.getMessage());
        }
        return UpdateResult.empty();
    }

    private UpdateResult parseVersions(JsonArray versions) {
        List<String> releases = new ArrayList<>();
        String latest = null;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            String type = getString(version, "version_type");
            if (!"release".equalsIgnoreCase(type)) {
                continue;
            }
            String normalized = versionComparator.normalize(getString(version, "version_number"));
            if (normalized == null || releases.contains(normalized)) {
                continue;
            }
            releases.add(normalized);
            if (latest == null || versionComparator.isNewer(normalized, latest)) {
                latest = normalized;
            }
        }

        releases.sort(new VersionReleaseComparator(versionComparator));
        return new UpdateResult(latest, List.copyOf(releases));
    }

    private String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static final class VersionReleaseComparator implements Comparator<String> {

        private final VersionComparator comparator;

        private VersionReleaseComparator(VersionComparator comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(String left, String right) {
            if (comparator.isNewer(left, right)) {
                return -1;
            }
            if (comparator.isNewer(right, left)) {
                return 1;
            }
            return 0;
        }
    }
}
