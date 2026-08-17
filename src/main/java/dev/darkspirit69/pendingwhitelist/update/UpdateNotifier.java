package dev.darkspirit69.pendingwhitelist.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class UpdateNotifier {

    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/pending-whitelist/version";
    private static final String PROJECT_URL = "https://modrinth.com/plugin/pending-whitelist";

    private final PendingWhitelistPlugin plugin;
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
            String latestVersion = fetchLatestVersion();
            if (latestVersion == null || !isNewer(latestVersion, plugin.getInstalledVersion())) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !player.hasPermission("pendingwhitelist.admin")) {
                    return;
                }
                Component message = Component.text("[PendingWhitelist] ", NamedTextColor.GOLD)
                        .append(Component.text("Update available: ", NamedTextColor.YELLOW))
                        .append(Component.text("v" + latestVersion, NamedTextColor.GREEN))
                        .append(Component.text(" (installed: v" + plugin.getInstalledVersion() + ") ",
                                NamedTextColor.GRAY))
                        .append(Component.text("[Modrinth]", NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(PROJECT_URL))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("Open PendingWhitelist on Modrinth", NamedTextColor.AQUA))));
                player.sendMessage(message);
            });
        });
    }

    public void checkNow(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String latestVersion = fetchLatestVersion();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (latestVersion == null) {
                    sender.sendMessage(Component.text("[PendingWhitelist] ", NamedTextColor.GOLD)
                            .append(Component.text("Could not determine the latest Modrinth version.",
                                    NamedTextColor.RED)));
                    return;
                }

                Component message = Component.text("[PendingWhitelist] ", NamedTextColor.GOLD)
                        .append(Component.text("Installed: v" + plugin.getInstalledVersion(), NamedTextColor.GRAY))
                        .append(Component.text(" | Latest: v" + latestVersion, NamedTextColor.GRAY));
                if (isNewer(latestVersion, plugin.getInstalledVersion())) {
                    message = message.append(Component.text(" [Modrinth]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(PROJECT_URL))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Open PendingWhitelist on Modrinth", NamedTextColor.AQUA))));
                } else {
                    message = message.append(Component.text(" (up to date)", NamedTextColor.GREEN));
                }
                sender.sendMessage(message);
            });
        });
    }

    private String fetchLatestVersion() {
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
                return null;
            }
            return findLatestRelease(JsonParser.parseString(response.body()).getAsJsonArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not check Modrinth for PendingWhitelist updates: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Modrinth update check was interrupted.");
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Modrinth returned an unexpected update response: " + ex.getMessage());
        }
        return null;
    }

    private String findLatestRelease(JsonArray versions) {
        String latest = null;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equalsIgnoreCase(getString(version, "version_type"))) {
                continue;
            }
            String versionNumber = normalizeVersion(getString(version, "version_number"));
            if (versionNumber != null && (latest == null || isNewer(versionNumber, latest))) {
                latest = versionNumber;
            }
        }
        return latest;
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
