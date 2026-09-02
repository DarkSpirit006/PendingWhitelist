package dev.darkspirit69.pendingwhitelist.completion;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Provides context-aware completion for the /wl command. */
public class WhitelistCompletion implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "pl",
            "list",
            "add",
            "remove",
            "rpl",
            "on",
            "off",
            "reload",
            "version");

    private final PendingStorage pendingStorage;

    public WhitelistCompletion(PendingStorage pendingStorage) {
        this.pendingStorage = pendingStorage;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pendingwhitelist.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 0) {
            return filterByPrefix(SUBCOMMANDS, "");
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return filterByPrefix(SUBCOMMANDS, args[0]);
        }

        if (!"add".equals(subcommand) && !"remove".equals(subcommand) && !"rpl".equals(subcommand)) {
            return Collections.emptyList();
        }

        List<String> allSuggestions = getRemovalSuggestions(subcommand);

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


    private List<String> getRemovalSuggestions(String subcommand) {
        List<String> suggestions = new ArrayList<>();
        if ("add".equals(subcommand)) {
            suggestions.addAll(getAddSuggestions());
        } else if ("rpl".equals(subcommand)) {
            suggestions.addAll(pendingStorage.getPendingUsernames());
        } else if ("remove".equals(subcommand)) {
            suggestions.addAll(pendingStorage.getWhitelistedUsernames());
        }
        return suggestions;
    }

    private List<String> getAddSuggestions() {
        List<String> whitelisted = pendingStorage.getWhitelistedUsernames();
        List<String> suggestions = new ArrayList<>(pendingStorage.getPendingUsernames());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                suggestions.add(name);
            }
        }

        return suggestions.stream()
                .filter(name -> !containsIgnoreCase(whitelisted, name))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
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
