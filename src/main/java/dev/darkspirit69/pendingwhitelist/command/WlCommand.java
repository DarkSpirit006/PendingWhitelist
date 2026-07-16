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
import java.util.Locale;

public class WlCommand implements CommandExecutor, TabCompleter {

    private static final String ROOT_USAGE = "&cUsage: /wl <pl|list|add|remove|rpl|reload>";

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
            TextUtil.send(sender, "&cYou do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "pl" -> handlePendingList(sender, args);
            case "list" -> handleWhitelistedList(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "rpl" -> handleRemovePendingOnly(sender, args);
            case "reload" -> handleReload(sender, args);
            default -> {
                TextUtil.send(sender, "&cUnknown subcommand.");
                TextUtil.send(sender, ROOT_USAGE);
                sendHelp(sender);
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(sender, "&8&m--------&r &6PendingWhitelist &8&m--------");
        TextUtil.send(sender, "&e/wl pl [page] &7- View pending players");
        TextUtil.send(sender, "&e/wl list &7- View whitelisted players");
        TextUtil.send(sender, "&e/wl add <name...> &7- Whitelist pending players");
        TextUtil.send(sender, "&e/wl remove <name...> &7- Remove from whitelist and pending");
        TextUtil.send(sender, "&e/wl rpl <name...> &7- Remove only from pending");
        TextUtil.send(sender, "&e/wl reload &7- Reload the config");
    }

    private boolean handlePendingList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            TextUtil.send(sender, "&cUsage: /wl pl [page]");
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                TextUtil.send(sender, "&cPage must be a number.");
                return true;
            }
        }

        List<PendingEntry> entries = pendingStorage.getPendingEntriesSortedByRecencyDesc();
        if (entries.isEmpty()) {
            TextUtil.send(sender, "&eNo pending players.");
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
        TextUtil.send(sender, "&8&m--------&r &6Pending players &7(" + entries.size() + ") &8&m--------");
        TextUtil.send(sender, "&7Page &f" + page + "&7/&f" + totalPages);
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
                TextUtil.send(sender, "&8- &f" + displayName);
            }
        }
        return true;
    }

    private boolean handleWhitelistedList(CommandSender sender, String[] args) {
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl list");
            return true;
        }

        List<String> whitelisted = pendingStorage.getWhitelistedUsernames();
        if (whitelisted.isEmpty()) {
            TextUtil.send(sender, "&eNo whitelisted players.");
            return true;
        }

        TextUtil.send(sender, "&8&m--------&r &6Whitelisted players &7(" + whitelisted.size() + ") &8&m--------");
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
                TextUtil.send(sender, "&8- &f" + name);
            }
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "&cUsage: /wl add <username> [username ...]");
            return true;
        }

        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            if (pendingStorage.isPending(username)) {
                boolean addedToWhitelist = pendingStorage.addToWhitelist(username);
                pendingStorage.removePendingOnly(username);
                if (addedToWhitelist) {
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

        sendResultGroup(sender, "&a✓ Added", added, "✔", NamedTextColor.GREEN, "whitelisted");
        sendResultGroup(sender, "&e• Already whitelisted", alreadyWhitelisted, "•", NamedTextColor.YELLOW,
                "already whitelisted");
        sendResultGroup(sender, "&c✖ Unknown", unknown, "•", NamedTextColor.RED, "unknown");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "&cUsage: /wl remove <identifier> [identifier ...]");
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

        sendResultGroup(sender, "&a✓ Removed", removed, "✔", NamedTextColor.GREEN, "removed");
        sendResultGroup(sender, "&c✖ Not found", notFound, "•", NamedTextColor.RED, "not found");
        return true;
    }

    private boolean handleRemovePendingOnly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            TextUtil.send(sender, "&cUsage: /wl rpl <identifier> [identifier ...]");
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

        sendResultGroup(sender, "&a✓ Removed from pending list", removed, "✔", NamedTextColor.GREEN,
                "removed from pending");
        sendResultGroup(sender, "&c✖ Not found in pending list", notFound, "•", NamedTextColor.RED, "not found");
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl reload");
            return true;
        }
        plugin.reloadConfig();
        pendingStorage.loadFromDisk();
        TextUtil.send(sender, "&aReload complete.");
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
            TextUtil.send(sender, "&7" + icon + " &f" + resolvedName + " &8(" + status + ")");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return completion.onTabComplete(sender, command, alias, args);
    }
}
