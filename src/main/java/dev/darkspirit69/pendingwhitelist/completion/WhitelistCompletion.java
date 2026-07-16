package dev.darkspirit69.pendingwhitelist.completion;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WhitelistCompletion implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "pl",
            "list",
            "add",
            "remove",
            "rpl",
            "reload");

    private final PendingStorage pendingStorage;

    public WhitelistCompletion(PendingStorage pendingStorage) {
        this.pendingStorage = pendingStorage;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pendingwhitelist.admin")) {
            return Collections.emptyList();
        }

        if (args.length <= 1) {
            String current = args.length == 0 ? "" : args[0];
            return filterByPrefix(SUBCOMMANDS, current);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!"add".equals(subcommand) && !"remove".equals(subcommand) && !"rpl".equals(subcommand)) {
            return Collections.emptyList();
        }

        List<String> allSuggestions = new ArrayList<>();
        if ("add".equals(subcommand) || "rpl".equals(subcommand)) {
            allSuggestions.addAll(pendingStorage.getPendingUsernames());
        } else if ("remove".equals(subcommand)) {
            allSuggestions.addAll(pendingStorage.getPendingUsernames());
            allSuggestions.addAll(pendingStorage.getWhitelistedUsernames());
        }

        List<String> availableSuggestions = new ArrayList<>();

        for (String suggestion : allSuggestions) {
            if (args.length > 1) {
                boolean alreadyEntered = false;
                for (int i = 1; i < args.length - 1; i++) {
                    if (suggestion.equalsIgnoreCase(args[i])) {
                        alreadyEntered = true;
                        break;
                    }
                }
                if (alreadyEntered) {
                    continue;
                }
            }
            availableSuggestions.add(suggestion);
        }

        return filterByPrefix(availableSuggestions, args[args.length - 1]);
    }

    private List<String> filterByPrefix(List<String> suggestions, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                result.add(suggestion);
            }
        }

        return result;
    }
}
