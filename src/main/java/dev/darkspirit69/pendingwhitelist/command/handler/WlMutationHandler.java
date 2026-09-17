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
import java.util.concurrent.CompletableFuture;

/** Handles whitelist changes and configuration reloads. */
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

        List<CompletableFuture<AddResult>> operations = new ArrayList<>(args.length - 1);
        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            PendingEntry pendingEntry = context.repository().findPendingEntry(username);
            CompletableFuture<AddResult> operation = context.repository().addToWhitelistAsync(username)
                    .handle((added, error) -> new AddResult(username, pendingEntry, Boolean.TRUE.equals(added), error));
            operations.add(operation);
        }

        if (operations.stream().anyMatch(operation -> !operation.isDone())) {
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "Resolving player profiles...");
        }

        CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).thenRun(() ->
                context.plugin().getServer().getScheduler().runTask(context.plugin(), () -> {
                    DebugLog.debug("Whitelist add operation completed for " + operations.size() + " identifier(s)");
                    finishAdd(sender, operations);
                }));
        return true;
    }

    private void finishAdd(CommandSender sender, List<CompletableFuture<AddResult>> operations) {
        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();

        for (CompletableFuture<AddResult> operation : operations) {
            AddResult result = operation.join();
            if (result.error() != null) {
                DebugLog.error("Whitelist add failed for " + result.identifier() + ".", result.error());
                continue;
            }
            if (result.added()) {
                added.add(result.identifier());
                PendingEntry pendingEntry = result.pendingEntry();
                if (pendingEntry != null) {
                    String pendingIdentifier = pendingEntry.uuid() != null && !pendingEntry.uuid().isBlank()
                            ? pendingEntry.uuid()
                            : result.identifier();
                    context.repository().removePendingOnly(pendingIdentifier);
                }
            } else if (context.repository().isWhitelisted(result.identifier())) {
                alreadyWhitelisted.add(result.identifier());
            }
        }

        sendAddedMessages(sender, added);
        sendResultGroup(sender, "Already whitelisted:", alreadyWhitelisted, MessageStyle.WARNING, "",
                "already whitelisted");
        playResultSound(sender, added, alreadyWhitelisted);
    }

    private record AddResult(String identifier, PendingEntry pendingEntry, boolean added, Throwable error) {
    }

    private void sendAddedMessages(CommandSender sender, List<String> identifiers) {
        for (String identifier : identifiers) {
            String displayName = context.repository().resolveDisplayNameForIdentifier(identifier);
            String name = displayName == null || displayName.isBlank() ? identifier : displayName;
            TextUtil.send(sender, MessageStyle.SUCCESS_LEGACY + "Added " + MessageStyle.VALUE_LEGACY
                    + name + " " + MessageStyle.SECONDARY_LEGACY + "to the whitelist.");
        }
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

        sendResultGroup(sender, "Removed", removed, MessageStyle.SUCCESS, "from the whitelist", "removed");
        sendResultGroup(sender, "Could not remove", notFound, MessageStyle.ERROR, "from the whitelist",
                "not whitelisted");
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

        sendResultGroup(sender, "Removed", removed, MessageStyle.SUCCESS, "from pending players", "removed");
        sendResultGroup(sender, "Could not remove", notFound, MessageStyle.ERROR,
                "from pending players", "not found");
        playResultSound(sender, removed, notFound);
        return true;
    }

    public boolean toggleWhitelist(CommandSender sender, String[] args, boolean enabled) {
        DebugLog.debug("Whitelist toggle requested by " + sender.getName() + ": enabled=" + enabled);
        if (args.length != 1) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl " + (enabled ? "on" : "off"));
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
        TextUtil.send(sender, (enabled ? MessageStyle.SUCCESS_LEGACY : MessageStyle.WARNING_LEGACY)
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
        TextUtil.send(sender, MessageStyle.PRIMARY_LEGACY + "Reloading configuration...");
        if (context.plugin().reloadConfiguration()) {
            TextUtil.send(sender, MessageStyle.successLegacy("Configuration reloaded."));
        } else {
            TextUtil.send(sender, MessageStyle.errorLegacy(
                    "Could not reload configuration. Check the server console."));
        }
        return true;
    }

    private void sendResultGroup(CommandSender sender, String action, List<String> identifiers,
            NamedTextColor actionColor, String suffix, String status) {
        if (identifiers.isEmpty()) {
            return;
        }

        for (String identifier : identifiers) {
            sendPlayerLine(sender, action, identifier, actionColor, suffix, status);
        }
    }

    private void sendPlayerLine(CommandSender sender, String action, String identifier, NamedTextColor actionColor,
            String suffix, String status) {
        String displayName = context.repository().resolveDisplayNameForIdentifier(identifier);
        String resolvedName = displayName == null || displayName.isBlank() ? identifier : displayName;
        PendingEntry entry = context.repository().findPendingEntry(identifier);
        String uuid = entry == null || entry.uuid() == null || entry.uuid().isBlank() ? "unknown" : entry.uuid();
        String attempts = entry == null ? "0" : String.valueOf(entry.attempts());
        String messageSuffix = suffix.isBlank() ? "." : " " + suffix + ".";

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
            player.sendMessage(Component.text(action + " ", actionColor)
                    .append(Component.text(resolvedName, MessageStyle.VALUE))
                    .append(Component.text(messageSuffix, actionColor))
                    .hoverEvent(HoverEvent.showText(hover)));
        } else {
            String legacyColor = legacyColor(actionColor);
            TextUtil.send(sender, legacyColor + action + " " + MessageStyle.VALUE_LEGACY
                    + resolvedName + MessageStyle.SECONDARY_LEGACY + messageSuffix);
        }
    }

    private String legacyColor(NamedTextColor color) {
        if (color == MessageStyle.SUCCESS) {
            return MessageStyle.SUCCESS_LEGACY;
        }
        if (color == MessageStyle.WARNING) {
            return MessageStyle.WARNING_LEGACY;
        }
        if (color == MessageStyle.ERROR) {
            return MessageStyle.ERROR_LEGACY;
        }
        return MessageStyle.SECONDARY_LEGACY;
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
