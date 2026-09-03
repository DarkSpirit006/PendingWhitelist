package dev.darkspirit69.pendingwhitelist.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Checks Modrinth for a newer stable release without blocking the server thread. */
public final class UpdateNotifier {

    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/pending-whitelist/version";
    private static final String PROJECT_URL = "https://modrinth.com/plugin/pending-whitelist";
    private final PendingWhitelistPlugin plugin;
    private volatile long lastJoinCheckAt;
    private final HttpClient httpClient;

    public UpdateNotifier(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void notifyIfUpdateAvailable(Player player) {
        if (!player.hasPermission("pendingwhitelist.admin")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            VersionResult result = fetchVersionResult();
            if (result.latestVersion() == null) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !player.hasPermission("pendingwhitelist.admin")) {
                    return;
                }

                String installed = plugin.getInstalledVersion();
                if (!isNewer(result.latestVersion(), installed)) {
                    return;
                }

                int versionsBehind = countVersionsBehind(
                        result.releaseVersions(), installed, result.latestVersion());
                String amount = versionsBehind == 1 ? "1 version(s)" : versionsBehind + " version(s)";
                player.sendMessage(Component.text("You are " + amount + " behind.", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Download the new version at:", NamedTextColor.GRAY));
                player.sendMessage(Component.text(PROJECT_URL, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(PROJECT_URL))
                        .hoverEvent(HoverEvent.showText(Component.text(PROJECT_URL))));
            });
        });
    }

    public void checkNow(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            VersionResult result = fetchVersionResult();
            Bukkit.getScheduler().runTask(plugin, () -> sendVersionResult(sender, result));
        });
    }

    private void sendVersionResult(CommandSender sender, VersionResult result) {
        String installed = plugin.getInstalledVersion();
        if (result.latestVersion() == null) {
            sender.sendMessage(Component.text("Current version: v" + installed, NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Could not determine the latest Modrinth version.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Current version: v" + installed, NamedTextColor.GRAY));
        if (!isNewer(result.latestVersion(), installed)) {
            sender.sendMessage(Component.text("Plugin is up to date.", NamedTextColor.GREEN));
            return;
        }

        int versionsBehind = countVersionsBehind(result.releaseVersions(), installed, result.latestVersion());
        String amount = versionsBehind == 1 ? "1 version(s)" : versionsBehind + " version(s)";
        sender.sendMessage(Component.text("You are " + amount + " behind.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Download the new version at:", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(PROJECT_URL, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(PROJECT_URL))
                .hoverEvent(HoverEvent.showText(Component.text(PROJECT_URL))));
    }

    private VersionResult fetchVersionResult() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(VERSIONS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "PendingWhitelist/" + plugin.getInstalledVersion())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger()
                        .warning("Could not check Modrinth for updates (HTTP " + response.statusCode() + ").");
                return VersionResult.empty();
            }
            return findLatestRelease(JsonParser.parseString(response.body()).getAsJsonArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not check Modrinth for PendingWhitelist updates: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Modrinth update check was interrupted.");
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
            plugin.getLogger().warning("Modrinth returned an unexpected update response: " + ex.getMessage());
        }
        return VersionResult.empty();
    }

    private VersionResult findLatestRelease(JsonArray versions) {
        String latest = null;
        List<String> releases = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equalsIgnoreCase(getString(version, "version_type"))) {
                continue;
            }
            String versionNumber = normalizeVersion(getString(version, "version_number"));
            if (versionNumber == null || releases.contains(versionNumber)) {
                continue;
            }
            releases.add(versionNumber);
            if (latest == null || isNewer(versionNumber, latest)) {
                latest = versionNumber;
            }
        }
        releases.sort((left, right) -> isNewer(right, left) ? 1 : isNewer(left, right) ? -1 : 0);
        return new VersionResult(latest, releases);
    }

    private int countVersionsBehind(List<String> releases, String installed, String latest) {
        int count = 0;
        for (String release : releases) {
            if (isNewer(release, installed) && !isNewer(release, latest)) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private record VersionResult(String latestVersion, List<String> releaseVersions) {
        private static VersionResult empty() {
            return new VersionResult(null, java.util.List.of());
        }
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
