package dev.darkspirit69.pendingwhitelist.update;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Coordinates asynchronous update checks and returns results on the server
 * thread.
 */
public final class UpdateService {

    private static final String ADMIN_PERMISSION = "pendingwhitelist.admin";
    private static final long UPDATE_CACHE_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(6);
    private static final long EMPTY_UPDATE_CACHE_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(15);

    private final PendingWhitelistPlugin plugin;
    private final ModrinthClient client;
    private final VersionComparator versionComparator;
    private CompletableFuture<UpdateResult> inFlightCheck;
    private UpdateResult cachedResult = UpdateResult.empty();
    private long cachedResultAt;

    public UpdateService(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.versionComparator = new VersionComparator();
        this.client = new ModrinthClient(plugin, versionComparator);
    }

    public void checkForPlayer(Player player, Consumer<UpdateResult> callback) {
        DebugLog.debug("Update check requested for player " + player.getName());
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        fetchAsync(result -> {
            if (!player.isOnline() || !player.hasPermission(ADMIN_PERMISSION)) {
                return;
            }
            callback.accept(result);
        });
    }

    public void checkForSender(CommandSender sender, Consumer<UpdateResult> callback) {
        DebugLog.debug("Update check requested by sender " + sender.getName());
        fetchAsync(result -> {
            if (sender instanceof Player player && (!player.isOnline() ||
                    !player.hasPermission(ADMIN_PERMISSION))) {
                return;
            }
            callback.accept(result);
        });
    }

    public boolean isNewer(String candidate, String current) {
        return versionComparator.isNewer(candidate, current);
    }

    public int countVersionsBehind(UpdateResult result, String current) {
        return versionComparator.countVersionsBehind(result.releaseVersions(), current, result.latestVersion());
    }

    private void fetchAsync(Consumer<UpdateResult> callback) {
        CompletableFuture<UpdateResult> check;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long cacheLifetime = cachedResult.hasRelease() ? UPDATE_CACHE_MILLIS : EMPTY_UPDATE_CACHE_MILLIS;
            if (cachedResultAt > 0 && now - cachedResultAt < cacheLifetime) {
                check = CompletableFuture.completedFuture(cachedResult);
                DebugLog.debug("Using cached Modrinth update result");
            } else if (inFlightCheck != null && !inFlightCheck.isDone()) {
                check = inFlightCheck;
                DebugLog.debug("Joining existing in-flight Modrinth update request");
            } else {
                DebugLog.debug("Starting new asynchronous Modrinth update request");
                check = CompletableFuture.supplyAsync(client::fetchLatestRelease);
                inFlightCheck = check;
            }
        }

        check.whenComplete((result, throwable) -> {
            UpdateResult finalResult = result;
            if (throwable != null) {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                DebugLog.error("Unexpected error while checking Modrinth for updates: " + cause.getMessage(), cause);
                finalResult = UpdateResult.empty();
            } else if (result != null) {
                synchronized (UpdateService.this) {
                    cachedResult = result;
                    cachedResultAt = System.currentTimeMillis();
                }
            }
            UpdateResult deliveredResult = finalResult == null ? UpdateResult.empty() : finalResult;
            DebugLog.debug("Modrinth update request completed");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) {
                    callback.accept(deliveredResult);
                }
            });
        });
    }
}
