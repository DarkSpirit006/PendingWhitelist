package dev.darkspirit69.pendingwhitelist.completion;

import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhitelistCompletion implements TabCompleter {

    private final PendingStorage pendingStorage;

    public WhitelistCompletion(PendingStorage pendingStorage) {
        this.pendingStorage = pendingStorage;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return List.of("list", "add", "reload");
        }

        String subcommand = args[0].toLowerCase();
        if (!"add".equals(subcommand)) {
            return Collections.emptyList();
        }

        List<String> allSuggestions = pendingStorage.getPendingUsernames();
        List<String> result = new ArrayList<>();
        String current = args[args.length - 1].toLowerCase();

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
            if (suggestion.toLowerCase().startsWith(current)) {
                result.add(suggestion);
            }
        }

        return result;
    }
}
