package dev.darkspirit69.pendingwhitelist.command;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared dependencies and common command operations used by /wl handlers. */
public final class WlCommandContext {

    public static final String ADMIN_PERMISSION = "pendingwhitelist.admin";

    private final PendingWhitelistPlugin plugin;
    private final PendingRepository repository;
    private final UpdateNotifier updateNotifier;

    public WlCommandContext(PendingWhitelistPlugin plugin, PendingRepository repository,
            UpdateNotifier updateNotifier) {
        this.plugin = plugin;
        this.repository = repository;
        this.updateNotifier = updateNotifier;
    }

    public PendingWhitelistPlugin plugin() {
        return plugin;
    }

    public PendingRepository repository() {
        return repository;
    }

    public UpdateNotifier updateNotifier() {
        return updateNotifier;
    }

    public boolean openGui(CommandSender sender, WlGui.View view) {
        DebugLog.debug("Opening GUI view " + view + " for " + sender.getName());
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, MessageStyle.errorLegacy(
                    "This command must be used by a player when opening the GUI."));
            return true;
        }

        WlGui gui = new WlGui(plugin, repository);
        switch (view) {
            case MAIN -> gui.openMain(player);
            case WHITELISTED -> gui.openWhitelisted(player, false);
            case ADD -> gui.openAdd(player);
            case CONFIG -> gui.openConfig(player);
        }
        return true;
    }

    public int parsePage(CommandSender sender, String[] args, String usage) {
        DebugLog.debug("Parsing command page for " + sender.getName());
        if (args.length == 1) {
            return 1;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            TextUtil.send(sender, MessageStyle.errorLegacy("Page must be a number."));
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "Usage: " + usage);
            return -1;
        }
    }

    public int pageCount(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    public int clampPage(int page, int totalPages) {
        return Math.max(1, Math.min(page, totalPages));
    }
}
