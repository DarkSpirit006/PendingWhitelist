package dev.darkspirit69.pendingwhitelist.update;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
            player.sendMessage(Component.text("You are " + versionsBehind + " version(s) behind.",
                    MessageStyle.WARNING));
            player.sendMessage(Component.text("Download the new version at:", MessageStyle.SECONDARY));
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
            sender.sendMessage(Component.text("Current version: v" + installed, MessageStyle.SECONDARY));
            sender.sendMessage(Component.text("Could not determine the latest Modrinth version.",
                    MessageStyle.ERROR));
            return;
        }

        sender.sendMessage(Component.text("Current version: v" + installed, MessageStyle.SECONDARY));
        if (!updateService.isNewer(result.latestVersion(), installed)) {
            sender.sendMessage(Component.text("Plugin is up to date.", MessageStyle.SUCCESS));
            return;
        }

        int versionsBehind = updateService.countVersionsBehind(result, installed);
        sender.sendMessage(Component.text("You are " + versionsBehind + " version(s) behind.", MessageStyle.WARNING));
        sender.sendMessage(Component.text("Download the new version at:", MessageStyle.SECONDARY));
        sender.sendMessage(updateLink());
    }

    private Component updateLink() {
        return Component.text(PROJECT_URL, MessageStyle.PRIMARY)
                .clickEvent(ClickEvent.openUrl(PROJECT_URL))
                .hoverEvent(HoverEvent.showText(Component.text(PROJECT_URL)));
    }
}
