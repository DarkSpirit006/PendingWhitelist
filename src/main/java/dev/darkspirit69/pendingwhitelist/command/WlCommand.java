package dev.darkspirit69.pendingwhitelist.command;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.command.handler.WlGeneralHandler;
import dev.darkspirit69.pendingwhitelist.command.handler.WlListHandler;
import dev.darkspirit69.pendingwhitelist.command.handler.WlMutationHandler;
import dev.darkspirit69.pendingwhitelist.completion.WhitelistCompletion;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** Coordinates /wl subcommands and delegates behavior to focused handlers. */
public final class WlCommand implements CommandExecutor, TabCompleter {

    private final WlCommandContext context;
    private final WlGeneralHandler generalHandler;
    private final WlListHandler listHandler;
    private final WlMutationHandler mutationHandler;
    private final UpdateNotifier updateNotifier;
    private final WhitelistCompletion completion;

    public WlCommand(PendingWhitelistPlugin plugin, PendingRepository repository, UpdateNotifier updateNotifier) {
        this.context = new WlCommandContext(plugin, repository, updateNotifier);
        this.generalHandler = new WlGeneralHandler(context);
        this.listHandler = new WlListHandler(context);
        this.mutationHandler = new WlMutationHandler(context);
        this.updateNotifier = updateNotifier;
        this.completion = new WhitelistCompletion(repository);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLog.debug("Command /" + label + " invoked by " + sender.getName()
                + " with " + args.length + " argument(s)");
        if (!sender.hasPermission(WlCommandContext.ADMIN_PERMISSION)) {
            TextUtil.send(sender, MessageStyle.errorLegacy("You do not have permission."));
            return true;
        }

        if (args.length == 0) {
            return generalHandler.openMain(sender);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        DebugLog.debug("Dispatching /wl subcommand: " + subcommand);
        return switch (subcommand) {
            case "pl" -> listHandler.pending(sender, args);
            case "list" -> listHandler.whitelisted(sender, args);
            case "add" -> args.length == 1 ? context.openGui(sender, WlGui.View.ADD)
                    : mutationHandler.add(sender, args);
            case "remove" -> args.length == 1 ? context.openGui(sender, WlGui.View.WHITELISTED)
                    : mutationHandler.remove(sender, args);
            case "rpl" -> args.length == 1 ? context.openGui(sender, WlGui.View.ADD)
                    : mutationHandler.removePendingOnly(sender, args);
            case "on" -> mutationHandler.toggleWhitelist(sender, args, true);
            case "off" -> mutationHandler.toggleWhitelist(sender, args, false);
            case "reload" -> mutationHandler.reload(sender, args);
            case "version" -> handleVersion(sender, args);
            default -> {
                generalHandler.sendUnknown(sender);
                yield true;
            }
        };
    }

    private boolean handleVersion(CommandSender sender, String[] args) {
        if (args.length != 1) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl version");
            return true;
        }
        updateNotifier.checkNow(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        DebugLog.debug("Tab completion requested by " + sender.getName()
                + " for /" + alias + " with " + args.length + " argument(s)");
        List<String> result = completion.onTabComplete(sender, command, alias, args);
        DebugLog.debug("Tab completion returned " + result.size() + " suggestion(s)");
        return result;
    }
}
