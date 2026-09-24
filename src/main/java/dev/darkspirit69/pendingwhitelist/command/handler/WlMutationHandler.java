package dev.darkspirit69.pendingwhitelist.command.handler;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.command.WlCommandContext;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
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
        return add(sender, args, false);
    }

    public boolean addBedrock(CommandSender sender, String[] args) {
        return add(sender, args, true);
    }

    private boolean add(CommandSender sender, String[] args, boolean bedrockOnly) {
        DebugLog.debug((bedrockOnly ? "Bedrock whitelist add" : "Whitelist add")
                + " requested by " + sender.getName());
        if (args.length < 2) {
            if (!bedrockOnly && sender instanceof Player) {
                context.openGui(sender, WlGui.View.ADD);
                return true;
            }
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: "
                    + (bedrockOnly ? "/wlb add <username> [username ...]" : "/wl add <username> [username ...]"));
            return true;
        }
        if (bedrockOnly && !FloodgateUtil.isAvailable()) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Floodgate is not available.");
            return true;
        }

        List<CompletableFuture<AddResult>> operations = new ArrayList<>(args.length - 1);
        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            PendingEntry pendingEntry = context.repository().findPendingEntry(username);
            CompletableFuture<Boolean> addFuture;
            if (bedrockOnly && pendingEntry != null && pendingEntry.uuid() != null
                    && !pendingEntry.uuid().isBlank()) {
                addFuture = context.repository().addFloodgatePlayerToWhitelistAsync(pendingEntry.uuid());
            } else {
                addFuture = bedrockOnly
                        ? context.repository().addFloodgatePlayerToWhitelistAsync(username)
                        : context.repository().addToWhitelistAsync(username);
            }
            CompletableFuture<AddResult> operation = addFuture
                    .handle((added, error) -> new AddResult(username, pendingEntry, Boolean.TRUE.equals(added), error));
            operations.add(operation);
        }

        CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new))
                .thenRun(() -> context.plugin().getServer().getScheduler().runTask(context.plugin(), () -> {
                    DebugLog.debug((bedrockOnly ? "Bedrock whitelist" : "Whitelist")
                            + " add operation completed for " + operations.size() + " identifier(s)");
                    finishAdd(sender, operations, bedrockOnly);
                }));
        return true;
    }

    private void finishAdd(CommandSender sender, List<CompletableFuture<AddResult>> operations, boolean bedrockOnly) {
        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (CompletableFuture<AddResult> operation : operations) {
            AddResult result = operation.join();
            if (result.error() != null) {
                DebugLog.error("Whitelist add failed for " + result.identifier() + ".", result.error());
                failed.add(result.identifier());
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
            } else {
                failed.add(result.identifier());
            }
        }

        sendAddedMessages(sender, added);
        sendResultGroup(sender, "Already whitelisted", alreadyWhitelisted, MessageStyle.WARNING, "");
        sendResultGroup(sender, "Could not add", failed, MessageStyle.ERROR, "to the whitelist");
        if (bedrockOnly && !failed.isEmpty()) {
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY
                    + "Bedrock players can only be resolved by name if they've joined a Geyser "
                    + "server before, or if the lookup service is available. Have them join once, "
                    + "or use their Floodgate UUID with /wlb add instead.");
        }
        playResultSound(sender, added, failed);
    }

    private void sendAddedMessages(CommandSender sender, List<String> identifiers) {
        for (String identifier : identifiers) {
            String displayName = context.repository().resolveDisplayNameForIdentifier(identifier);
            String name = displayName == null || displayName.isBlank() ? identifier : displayName;
            TextUtil.sendResult(sender, "Added", name, MessageStyle.SUCCESS, "to the whitelist");
        }
    }

    private record AddResult(String identifier, PendingEntry pendingEntry, boolean added, Throwable error) {
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

        sendResultGroup(sender, "Removed", removed, MessageStyle.SUCCESS, "from the whitelist");
        sendResultGroup(sender, "Could not remove", notFound, MessageStyle.ERROR, "from the whitelist");
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

        sendResultGroup(sender, "Removed", removed, MessageStyle.SUCCESS, "from pending players");
        sendResultGroup(sender, "Could not remove", notFound, MessageStyle.ERROR,
                "from pending players");
        playResultSound(sender, removed, notFound);
        return true;
    }

    public boolean toggleWhitelist(CommandSender sender, String[] args, boolean enabled) {
        DebugLog.debug("Whitelist toggle requested by " + sender.getName() + ": enabled=" + enabled);
        if (args.length != 1) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl " + (enabled ? "on" : "off"));
            return true;
        }

        if (!context.repository().setWhitelistEnabled(enabled)) {
            TextUtil.send(sender, MessageStyle.WARNING_LEGACY
                    + (enabled ? "Whitelist is already enabled." : "Whitelist is already disabled."));
            if (sender instanceof Player player) {
                SoundUtil.failure(player);
            }
            return true;
        }

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
            NamedTextColor actionColor, String suffix) {
        if (identifiers.isEmpty()) {
            return;
        }

        for (String identifier : identifiers) {
            sendPlayerLine(sender, action, identifier, actionColor, suffix);
        }
    }

    private void sendPlayerLine(CommandSender sender, String action, String identifier, NamedTextColor actionColor,
            String suffix) {
        String displayName = context.repository().resolveDisplayNameForIdentifier(identifier);
        String resolvedName = displayName == null || displayName.isBlank() ? identifier : displayName;
        TextUtil.sendResult(sender, action, resolvedName, actionColor, suffix);
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
