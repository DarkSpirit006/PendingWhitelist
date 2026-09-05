package dev.darkspirit69.pendingwhitelist.command.handler;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.command.WlCommandContext;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import org.bukkit.command.CommandSender;

/** Handles the root /wl command and help output. */
public final class WlGeneralHandler {

        private static final String ROOT_USAGE = MessageStyle.SECONDARY_LEGACY
                        + "Usage: /wl <pl|list|add|remove|rpl|on|off|reload|version>";
        private final WlCommandContext context;

        public WlGeneralHandler(WlCommandContext context) {
                this.context = context;
        }

        public boolean openMain(CommandSender sender) {
                DebugLog.debug("Opening main GUI for " + sender.getName());
                return context.openGui(sender, WlGui.View.MAIN);
        }

        public void sendHelp(CommandSender sender) {
                DebugLog.debug("Sending /wl help to " + sender.getName());
                TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "-------------------------------");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "PendingWhitelist");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl pl [page] "
                                + MessageStyle.SECONDARY_LEGACY + "- View pending players");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl list [page] "
                                + MessageStyle.SECONDARY_LEGACY + "- View whitelisted players");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl add <name...> "
                                + MessageStyle.SECONDARY_LEGACY + "- Add players to the server whitelist");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl remove <name...> "
                                + MessageStyle.SECONDARY_LEGACY + "- Remove players from the server whitelist");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl rpl <name...> "
                                + MessageStyle.SECONDARY_LEGACY + "- Reject/remove pending requests");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl on "
                                + MessageStyle.SECONDARY_LEGACY + "- Enable the server whitelist");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl off "
                                + MessageStyle.SECONDARY_LEGACY + "- Disable the server whitelist");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl reload "
                                + MessageStyle.SECONDARY_LEGACY + "- Restart the plugin and reload its config");
                TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "/wl version "
                                + MessageStyle.SECONDARY_LEGACY + "- Check the latest Modrinth version");
                TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "-------------------------------");
        }

        public void sendUnknown(CommandSender sender) {
                DebugLog.debug("Unknown /wl subcommand requested by " + sender.getName());
                TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Unknown subcommand.");
                TextUtil.send(sender, ROOT_USAGE);
                sendHelp(sender);
        }
}
