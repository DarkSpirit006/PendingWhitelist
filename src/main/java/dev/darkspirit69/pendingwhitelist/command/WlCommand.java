package dev.darkspirit69.pendingwhitelist.command;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.completion.WhitelistCompletion;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class WlCommand implements CommandExecutor, TabCompleter {

    private final PendingWhitelistPlugin plugin;
    private final PendingStorage pendingStorage;
    private final WhitelistCompletion completion;

    public WlCommand(PendingWhitelistPlugin plugin, PendingStorage pendingStorage) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
        this.completion = new WhitelistCompletion(pendingStorage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pendingwhitelist.admin")) {
            sender.sendMessage(TextUtil.color("&cYou do not have permission."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl <pl|list|add|remove|rpl|reload>"));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        return switch (subcommand) {
            case "pl" -> handlePendingList(sender, args);
            case "list" -> handleWhitelistedList(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "rpl" -> handleRemovePendingOnly(sender, args);
            case "approve" -> handleApprove(sender, args);
            case "deny" -> handleDeny(sender, args);
            case "reload" -> handleReload(sender, args);
            default -> {
                sender.sendMessage(TextUtil.color("&cUsage: /wl <pl|list|add|remove|rpl|reload>"));
                yield true;
            }
        };
    }

    private boolean handlePendingList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl pl [page]"));
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(TextUtil.color("&cPage must be a number."));
                return true;
            }
        }

        List<PendingEntry> entries = pendingStorage.getPendingEntriesSortedByRecencyDesc();
        if (entries.isEmpty()) {
            sender.sendMessage(TextUtil.color("&eNo pending players."));
            return true;
        }

        int pageSize = plugin.getConfiguredPageSize();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, entries.size());
        sender.sendMessage(TextUtil.color("&6━━ Pending players (page " + page + " of " + totalPages + ") ━━"));
        for (int i = start; i < end; i++) {
            PendingEntry entry = entries.get(i);
            String displayName = entry.displayName();
            String uuidText = entry.uuid() != null && !entry.uuid().isBlank() ? entry.uuid() : "unknown";

            if (sender instanceof org.bukkit.entity.Player player) {
                Component hover = Component.text()
                        .append(Component.text("Player: ", NamedTextColor.GRAY))
                        .append(Component.text(displayName, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("UUID: ", NamedTextColor.GRAY))
                        .append(Component.text(uuidText, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Attempts: ", NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(entry.attempts()), NamedTextColor.WHITE))
                        .build();
                Component message = Component.text("• ", NamedTextColor.GOLD)
                        .append(Component.text(displayName, NamedTextColor.WHITE));
                player.sendMessage(message.hoverEvent(HoverEvent.showText(hover)));
            } else {
                sender.sendMessage(TextUtil.color("&7• &f" + displayName));
            }
        }
        return true;
    }

    private boolean handleWhitelistedList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl list"));
            return true;
        }

        List<String> whitelisted = pendingStorage.getWhitelistedUsernames();
        if (whitelisted.isEmpty()) {
            sender.sendMessage(TextUtil.color("&eNo whitelisted players."));
            return true;
        }

        sender.sendMessage(TextUtil.color("&6━━ Whitelisted players ━━"));
        for (String name : whitelisted) {
            if (sender instanceof org.bukkit.entity.Player player) {
                Component hover = Component.text()
                        .append(Component.text("Player: ", NamedTextColor.GRAY))
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Status: ", NamedTextColor.GRAY))
                        .append(Component.text("Whitelisted", NamedTextColor.GREEN))
                        .build();
                player.sendMessage(Component.text("• ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(hover))));
            } else {
                sender.sendMessage(TextUtil.color("&a• &f" + name));
            }
        }
        return true;
    }

    private boolean handleApprove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl approve <identifier> [identifier ...]"));
            return true;
        }

        List<String> approved = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (pendingStorage.isPending(identifier)) {
                pendingStorage.addToWhitelist(identifier);
                pendingStorage.removePendingOnly(identifier);
                String label = pendingStorage.resolveDisplayNameForIdentifier(identifier);
                approved.add(label != null && !label.isBlank() ? label : identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sender.sendMessage(TextUtil.color("&a✓ Approved"));
        for (String identifier : approved) {
            sendPlayerLine(sender, "✔", identifier, NamedTextColor.GREEN, "approved");
        }
        sender.sendMessage(TextUtil.color("&c✖ Not found in pending list"));
        for (String identifier : notFound) {
            sendPlayerLine(sender, "•", identifier, NamedTextColor.RED, "not found");
        }
        return true;
    }

    private boolean handleDeny(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl deny <identifier> [identifier ...]"));
            return true;
        }

        List<String> denied = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (pendingStorage.removePendingOnly(identifier)) {
                denied.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sender.sendMessage(TextUtil.color("&a✓ Denied"));
        for (String identifier : denied) {
            sendPlayerLine(sender, "✔", identifier, NamedTextColor.GREEN, "denied");
        }
        sender.sendMessage(TextUtil.color("&c✖ Not found in pending list"));
        for (String identifier : notFound) {
            sendPlayerLine(sender, "•", identifier, NamedTextColor.RED, "not found");
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl add <username> [username ...]"));
            return true;
        }

        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            if (pendingStorage.isPending(username)) {
                boolean addedToWhitelist = pendingStorage.addToWhitelist(username);
                if (addedToWhitelist) {
                    pendingStorage.remove(username);
                    added.add(username);
                } else {
                    alreadyWhitelisted.add(username);
                }
            } else {
                if (Bukkit.getOfflinePlayer(username).isWhitelisted()) {
                    alreadyWhitelisted.add(username);
                } else {
                    unknown.add(username);
                }
            }
        }

        sender.sendMessage(TextUtil.color("&a✓ Added"));
        for (String username : added) {
            sendPlayerLine(sender, "✔", username, NamedTextColor.GREEN, "whitelisted");
        }
        sender.sendMessage(TextUtil.color("&e• Already whitelisted"));
        for (String username : alreadyWhitelisted) {
            sendPlayerLine(sender, "•", username, NamedTextColor.YELLOW, "already whitelisted");
        }
        sender.sendMessage(TextUtil.color("&c✖ Unknown"));
        for (String username : unknown) {
            sendPlayerLine(sender, "•", username, NamedTextColor.RED, "unknown");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl remove <identifier> [identifier ...]"));
            return true;
        }

        List<String> removed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (pendingStorage.remove(identifier)) {
                removed.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sender.sendMessage(TextUtil.color("&a✓ Removed"));
        for (String identifier : removed) {
            sendPlayerLine(sender, "✔", identifier, NamedTextColor.GREEN, "removed");
        }
        sender.sendMessage(TextUtil.color("&c✖ Not found"));
        for (String identifier : notFound) {
            sendPlayerLine(sender, "•", identifier, NamedTextColor.RED, "not found");
        }
        return true;
    }

    private boolean handleRemovePendingOnly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl rpl <identifier> [identifier ...]"));
            return true;
        }

        List<String> removed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String identifier = args[i];
            if (pendingStorage.removePendingOnly(identifier)) {
                removed.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sender.sendMessage(TextUtil.color("&a✓ Removed from pending list"));
        for (String identifier : removed) {
            sendPlayerLine(sender, "✔", identifier, NamedTextColor.GREEN, "removed from pending");
        }
        sender.sendMessage(TextUtil.color("&c✖ Not found in pending list"));
        for (String identifier : notFound) {
            sendPlayerLine(sender, "•", identifier, NamedTextColor.RED, "not found");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl reload"));
            return true;
        }
        plugin.reloadConfig();
        pendingStorage.loadFromDisk();
        sender.sendMessage(TextUtil.color("&aReload complete."));
        return true;
    }

    private void sendPlayerLine(CommandSender sender, String icon, String identifier, NamedTextColor iconColor,
            String status) {
        String displayName = pendingStorage.resolveDisplayNameForIdentifier(identifier);
        String resolvedName = displayName != null && !displayName.isBlank() ? displayName : identifier;
        PendingEntry entry = pendingStorage.findPendingEntry(identifier);
        String uuidText = entry != null && entry.uuid() != null && !entry.uuid().isBlank() ? entry.uuid() : "unknown";
        String attemptsText = entry != null ? String.valueOf(entry.attempts()) : "0";

        if (sender instanceof org.bukkit.entity.Player player) {
            Component hover = Component.text()
                    .append(Component.text("Player: ", NamedTextColor.GRAY))
                    .append(Component.text(resolvedName, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", NamedTextColor.GRAY))
                    .append(Component.text(uuidText, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Status: ", NamedTextColor.GRAY))
                    .append(Component.text(status, NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Attempts: ", NamedTextColor.GRAY))
                    .append(Component.text(attemptsText, NamedTextColor.WHITE))
                    .build();
            player.sendMessage(Component.text(icon + " ", iconColor)
                    .append(Component.text(resolvedName, NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(hover))));
        } else {
            sender.sendMessage(TextUtil.color("&7" + icon + " &f" + resolvedName + " &8(" + status + ")"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return completion.onTabComplete(sender, command, alias, args);
    }
}
