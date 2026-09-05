package dev.darkspirit69.pendingwhitelist.command.handler;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.command.WlCommandContext;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Handles whitelist mutations and plugin reload operations. */
public final class WlMutationHandler {

    private final WlCommandContext context;

    public WlMutationHandler(WlCommandContext context) {
        this.context = context;
    }

    public boolean add(CommandSender sender, String[] args) {
        DebugLog.debug("Whitelist add requested by " + sender.getName());
        if (args.length < 2) {
            if (sender instanceof Player) {
                context.openGui(sender, WlGui.View.ADD);
                return true;
            }
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl add <username> [username ...]");
            return true;
        }

        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            PendingEntry pendingEntry = context.repository().findPendingEntry(username);
            boolean addedToWhitelist = context.repository().addToWhitelist(username);
            if (addedToWhitelist) {
                added.add(username);
                if (pendingEntry != null) {
                    context.repository().removePendingOnly(username);
                }
            } else if (context.repository().isWhitelisted(username)) {
                alreadyWhitelisted.add(username);
            }
        }

        sendResultGroup(sender, "&a✓ Added", added, "✔", MessageStyle.SUCCESS, "whitelisted");
        sendResultGroup(sender, "&e• Already whitelisted", alreadyWhitelisted, "•", MessageStyle.WARNING,
                "already whitelisted");
        playResultSound(sender, added, alreadyWhitelisted);
        return true;
    }

    public boolean remove(CommandSender sender, String[] args) {
        DebugLog.debug("Whitelist removal requested by " + sender.getName());
        if (args.length < 2) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl remove <identifier> [identifier ...]");
            return true;
        }

        List<String> removed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (context.repository().removeFromWhitelist(identifier)) {
                removed.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sendResultGroup(sender, "&a✓ Removed", removed, "✔", MessageStyle.SUCCESS, "removed");
        sendResultGroup(sender, "&c✖ Not found", notFound, "•", MessageStyle.ERROR, "not found");
        playResultSound(sender, removed, notFound);
        return true;
    }

    public boolean removePendingOnly(CommandSender sender, String[] args) {
        DebugLog.debug("Pending-only removal requested by " + sender.getName());
        if (args.length < 2) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl rpl <identifier> [identifier ...]");
            return true;
        }

        List<String> removed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (context.repository().removePendingOnly(identifier)) {
                removed.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sendResultGroup(sender, "&a✓ Removed from pending list", removed, "✔", MessageStyle.SUCCESS,
                "removed from pending");
        sendResultGroup(sender, "&c✖ Not found in pending list", notFound, "•", MessageStyle.ERROR, "not found");
        playResultSound(sender, removed, notFound);
        return true;
    }

    public boolean toggleWhitelist(CommandSender sender, String[] args, boolean enabled) {
        DebugLog.debug("Whitelist toggle requested by " + sender.getName() + ": enabled=" + enabled);
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl " + (enabled ? "on" : "off"));
            return true;
        }

        boolean currentlyEnabled = context.plugin().getServer().hasWhitelist();
        if (currentlyEnabled == enabled) {
            TextUtil.send(sender, MessageStyle.WARNING_LEGACY
                    + (enabled ? "Whitelist is already enabled." : "Whitelist is already disabled."));
            if (sender instanceof Player player) {
                SoundUtil.failure(player);
            }
            return true;
        }

        context.plugin().getServer().setWhitelist(enabled);
        TextUtil.send(sender, (enabled ? MessageStyle.SUCCESS_LEGACY : MessageStyle.ERROR_LEGACY)
                + (enabled ? "Whitelist enabled." : "Whitelist disabled."));
        if (sender instanceof Player player) {
            SoundUtil.success(player);
        }
        return true;
    }

    public boolean reload(CommandSender sender, String[] args) {
        DebugLog.debug("Configuration reload requested by " + sender.getName());
        if (args.length != 1) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl reload");
            return true;
        }
        TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "Reloading PendingWhitelist...");
        if (context.plugin().reloadConfiguration()) {
            TextUtil.send(sender, MessageStyle.successLegacy("PendingWhitelist reloaded successfully."));
        } else {
            TextUtil.send(sender, MessageStyle.errorLegacy(
                    "PendingWhitelist could not be reloaded. Check the server console."));
        }
        return true;
    }

    private void sendResultGroup(CommandSender sender, String header, List<String> identifiers, String icon,
            NamedTextColor iconColor, String status) {
        if (identifiers.isEmpty()) {
            return;
        }

        TextUtil.send(sender, header);
        for (String identifier : identifiers) {
            sendPlayerLine(sender, icon, identifier, iconColor, status);
        }
    }

    private void sendPlayerLine(CommandSender sender, String icon, String identifier, NamedTextColor iconColor,
            String status) {
        String displayName = context.repository().resolveDisplayNameForIdentifier(identifier);
        String resolvedName = displayName == null || displayName.isBlank() ? identifier : displayName;
        PendingEntry entry = context.repository().findPendingEntry(identifier);
        String uuid = entry == null || entry.uuid() == null || entry.uuid().isBlank() ? "unknown" : entry.uuid();
        String attempts = entry == null ? "0" : String.valueOf(entry.attempts());

        if (sender instanceof Player player) {
            Component hover = Component.text()
                    .append(Component.text("Player: ", MessageStyle.SECONDARY))
                    .append(Component.text(resolvedName, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", MessageStyle.SECONDARY))
                    .append(Component.text(uuid, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("Status: ", MessageStyle.SECONDARY))
                    .append(Component.text(status, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("Attempts: ", MessageStyle.SECONDARY))
                    .append(Component.text(attempts, MessageStyle.VALUE))
                    .build();
            player.sendMessage(Component.text(icon + " ", iconColor)
                    .append(Component.text(resolvedName, MessageStyle.VALUE)
                            .hoverEvent(HoverEvent.showText(hover))));
        } else {
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + icon + " " + MessageStyle.VALUE_LEGACY
                    + resolvedName + " (" + status + ")");
        }
    }

    private void playResultSound(CommandSender sender, List<String> success, List<String> failure) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (!success.isEmpty()) {
            SoundUtil.success(player);
        } else if (!failure.isEmpty()) {
            SoundUtil.failure(player);
        }
    }
}
