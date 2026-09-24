package dev.darkspirit69.pendingwhitelist.update;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Presents update results to players and command senders. */
public final class UpdateNotifier {

    private static final String PROJECT_URL = "https://modrinth.com/plugin/pending-whitelist";
    private final PendingWhitelistPlugin plugin;
    private final UpdateService updateService;

    public UpdateNotifier(PendingWhitelistPlugin plugin) {
        this.plugin = plugin;
        this.updateService = new UpdateService(plugin);
    }

    public void notifyIfUpdateAvailable(Player player) {
        DebugLog.debug("Automatic update notification check for " + player.getName());
        updateService.checkForPlayer(player, result -> {
            String installed = plugin.getInstalledVersion();
            if (!result.hasRelease() || !updateService.isNewer(result.latestVersion(), installed)) {
                return;
            }
            int versionsBehind = updateService.countVersionsBehind(result, installed);
            TextUtil.send(player, "You are " + versionsBehind + " "
                    + versionWord(versionsBehind) + " behind.");
            TextUtil.send(player, "Download the new version at:");
            player.sendMessage(updateLink());
        });
    }

    public boolean isNewerVersion(String candidate, String current) {
        return updateService.isNewer(candidate, current);
    }

    public void checkForGui(Player player, java.util.function.Consumer<UpdateResult> callback) {
        updateService.checkForPlayer(player, callback);
    }

    public void checkNow(CommandSender sender) {
        DebugLog.debug("Manual update check requested by " + sender.getName());
        updateService.checkForSender(sender, result -> sendVersionResult(sender, result));
    }

    private void sendVersionResult(CommandSender sender, UpdateResult result) {
        String installed = plugin.getInstalledVersion();
        if (!result.hasRelease()) {
            TextUtil.send(sender, "Current version: v" + installed);
            TextUtil.send(sender, "Could not determine the latest Modrinth version.");
            return;
        }

        TextUtil.send(sender, "Current version: v" + installed);
        if (!updateService.isNewer(result.latestVersion(), installed)) {
            TextUtil.send(sender, "Plugin is up to date.");
            return;
        }

        int versionsBehind = updateService.countVersionsBehind(result, installed);
        TextUtil.send(sender, "You are " + versionsBehind + " "
                + versionWord(versionsBehind) + " behind.");
        TextUtil.send(sender, "Download the new version at:");
        sender.sendMessage(updateLink());
    }

    private String versionWord(int count) {
        return count == 1 ? "version" : "versions";
    }

    private Component updateLink() {
        return Component.text(PROJECT_URL, MessageStyle.PRIMARY)
                .clickEvent(ClickEvent.openUrl(PROJECT_URL));
    }
}
