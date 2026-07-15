package dev.darkspirit69.pendingwhitelist.command;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.completion.WhitelistCompletion;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
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
            sender.sendMessage(TextUtil.color("&cUsage: /wl <list|add|reload>"));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        return switch (subcommand) {
            case "list" -> handleList(sender, args);
            case "add" -> handleAdd(sender, args);
            case "reload" -> handleReload(sender, args);
            default -> {
                sender.sendMessage(TextUtil.color("&cUsage: /wl <list|add|reload>"));
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl list [page]"));
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

        List<String> usernames = pendingStorage.getPendingUsernamesSortedByRecencyDesc();
        if (usernames.isEmpty()) {
            sender.sendMessage(TextUtil.color("&eNo pending players."));
            return true;
        }

        int pageSize = plugin.getConfiguredPageSize();
        int totalPages = Math.max(1, (int) Math.ceil(usernames.size() / (double) pageSize));
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, usernames.size());
        sender.sendMessage(TextUtil.color("&aPending players (page " + page + " of " + totalPages + ")"));
        for (int i = start; i < end; i++) {
            sender.sendMessage(TextUtil.color("&7- &f" + usernames.get(i)));
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

        sender.sendMessage(TextUtil.color("&aAdded:"));
        for (String username : added) {
            sender.sendMessage(TextUtil.color("&a✔ " + username));
        }
        sender.sendMessage(TextUtil.color("&eAlready whitelisted:"));
        for (String username : alreadyWhitelisted) {
            sender.sendMessage(TextUtil.color("&e• " + username));
        }
        sender.sendMessage(TextUtil.color("&cUnknown:"));
        for (String username : unknown) {
            sender.sendMessage(TextUtil.color("&c• " + username));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(TextUtil.color("&cUsage: /wl reload"));
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(TextUtil.color("&aReload complete."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return completion.onTabComplete(sender, command, alias, args);
    }
}
