package dev.darkspirit69.pendingwhitelist.completion;

import dev.darkspirit69.pendingwhitelist.storage.PendingRepository;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    private static final long ADD_SUGGESTIONS_CACHE_MILLIS = 500L;

    private final PendingRepository pendingStorage;
    private List<String> cachedAddSuggestions = List.of();
    private long cachedAddSuggestionsAt;

    public WhitelistCompletion(PendingRepository pendingStorage) {
        this.pendingStorage = pendingStorage;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        DebugLog.debug("WhitelistCompletion invoked for " + sender.getName());
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
        long now = System.currentTimeMillis();
        if (now - cachedAddSuggestionsAt < ADD_SUGGESTIONS_CACHE_MILLIS) {
            return cachedAddSuggestions;
        }

        Set<String> whitelisted = getWhitelistedNames();
        Set<String> seen = new HashSet<>();
        List<String> pendingBedrock = new ArrayList<>();
        List<String> pendingJava = new ArrayList<>();
        List<String> onlineBedrock = new ArrayList<>();
        List<String> onlineJava = new ArrayList<>();
        List<String> offlineBedrock = new ArrayList<>();
        List<String> offlineJava = new ArrayList<>();

        collectPendingSuggestions(whitelisted, seen, pendingBedrock, pendingJava);
        collectOnlineSuggestions(whitelisted, seen, onlineBedrock, onlineJava);
        collectOfflineSuggestions(whitelisted, seen, offlineBedrock, offlineJava);

        sortSuggestions(pendingBedrock);
        sortSuggestions(pendingJava);
        sortSuggestions(onlineBedrock);
        sortSuggestions(onlineJava);
        sortSuggestions(offlineBedrock);
        sortSuggestions(offlineJava);

        List<String> suggestions = new ArrayList<>();
        suggestions.addAll(pendingBedrock);
        suggestions.addAll(pendingJava);
        suggestions.addAll(onlineBedrock);
        suggestions.addAll(onlineJava);
        suggestions.addAll(offlineBedrock);
        suggestions.addAll(offlineJava);
        cachedAddSuggestions = List.copyOf(suggestions);
        cachedAddSuggestionsAt = now;
        return cachedAddSuggestions;
    }

    private Set<String> getWhitelistedNames() {
        Set<String> whitelisted = new HashSet<>();
        for (String name : pendingStorage.getWhitelistedUsernames()) {
            if (name != null) {
                whitelisted.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return whitelisted;
    }

    private void collectPendingSuggestions(
            Set<String> whitelisted,
            Set<String> seen,
            List<String> bedrock,
            List<String> java) {
        for (var entry : pendingStorage.getPendingEntriesSortedByRecencyDesc()) {
            String name = entry.name();
            if (name == null || name.isBlank()) {
                name = entry.displayName();
            }
            if (!isAvailable(name, whitelisted, seen)) {
                continue;
            }
            if (pendingStorage.isFloodgateUuid(entry.uuid())) {
                bedrock.add(name);
            } else {
                java.add(name);
            }
        }
    }

    private void collectOnlineSuggestions(
            Set<String> whitelisted,
            Set<String> seen,
            List<String> bedrock,
            List<String> java) {
        for (var player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (!isAvailable(name, whitelisted, seen) || pendingStorage.isPending(name)) {
                continue;
            }
            if (pendingStorage.isFloodgateUuid(player.getUniqueId().toString())) {
                bedrock.add(name);
            } else {
                java.add(name);
            }
        }
    }

    private void collectOfflineSuggestions(
            Set<String> whitelisted,
            Set<String> seen,
            List<String> bedrock,
            List<String> java) {
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.isOnline() || !player.hasPlayedBefore()) {
                continue;
            }
            String name = player.getName();
            if (!isAvailable(name, whitelisted, seen)) {
                continue;
            }
            if (pendingStorage.isFloodgateUuid(player.getUniqueId().toString())) {
                bedrock.add(name);
            } else {
                java.add(name);
            }
        }
    }

    private boolean isAvailable(String name, Set<String> whitelisted, Set<String> seen) {
        if (name == null || name.isBlank() || whitelisted.contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!seen.add(normalized)) {
            return false;
        }
        return true;
    }

    private void sortSuggestions(List<String> suggestions) {
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
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
