package dev.darkspirit69.pendingwhitelist.command;

import dev.darkspirit69.pendingwhitelist.PendingWhitelistPlugin;
import dev.darkspirit69.pendingwhitelist.gui.WlGui;
import dev.darkspirit69.pendingwhitelist.completion.WhitelistCompletion;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.storage.PendingStorage;
import dev.darkspirit69.pendingwhitelist.update.UpdateNotifier;
import dev.darkspirit69.pendingwhitelist.util.SoundUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Handles the /wl command while keeping the GUI and legacy command paths in one place. */
public final class WlCommand implements CommandExecutor, TabCompleter {

    private static final String ROOT_USAGE = "&7Usage: /wl <pl|list|add|remove|rpl|on|off|reload|version>";

    private final PendingWhitelistPlugin plugin;
    private final PendingStorage pendingStorage;
    private final UpdateNotifier updateNotifier;
    private final WhitelistCompletion completion;

    public WlCommand(PendingWhitelistPlugin plugin, PendingStorage pendingStorage, UpdateNotifier updateNotifier) {
        this.plugin = plugin;
        this.pendingStorage = pendingStorage;
        this.updateNotifier = updateNotifier;
        this.completion = new WhitelistCompletion(pendingStorage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pendingwhitelist.admin")) {
            TextUtil.send(sender, "&cYou do not have permission.");
            return true;
        }

        if (args.length == 0) {
            return openGui(sender, WlGui.View.MAIN);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "pl" -> handlePendingList(sender, args);
            case "list" -> handleWhitelistedList(sender, args);
            case "add" -> args.length == 1 ? openGui(sender, WlGui.View.ADD) : handleAdd(sender, args);
            case "remove" -> args.length == 1 ? openGui(sender, WlGui.View.WHITELISTED) : handleRemove(sender, args);
            case "rpl" -> args.length == 1 ? openGui(sender, WlGui.View.ADD)
                    : handleRemovePendingOnly(sender, args);
            case "on" -> handleWhitelistToggle(sender, args, true);
            case "off" -> handleWhitelistToggle(sender, args, false);
            case "reload" -> handleReload(sender, args);
            case "version" -> handleVersion(sender, args);
            default -> {
                TextUtil.send(sender, "&cUnknown subcommand.");
                TextUtil.send(sender, ROOT_USAGE);
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean openGui(CommandSender sender, WlGui.View view) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            TextUtil.send(sender, "&cThis command must be used by a player when opening the GUI.");
            return true;
        }

        WlGui gui = new WlGui(plugin, pendingStorage, updateNotifier);
        switch (view) {
            case MAIN -> gui.openMain(player);
            case WHITELISTED -> gui.openWhitelisted(player, false);
            case ADD -> gui.openAdd(player);
            case CONFIG -> gui.openConfig(player);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(sender, "&8&m---------------- &bPendingWhitelist &8&m----------------");
        TextUtil.send(sender, "&b/wl pl [page] &7- View pending players");
        TextUtil.send(sender, "&b/wl list [page] &7- View whitelisted players");
        TextUtil.send(sender, "&b/wl add <name...> &7- Add players to the server whitelist");
        TextUtil.send(sender, "&b/wl remove <name...> &7- Remove players from the server whitelist");
        TextUtil.send(sender, "&b/wl rpl <name...> &7- Reject/remove pending requests");
        TextUtil.send(sender, "&b/wl on &7- Enable the server whitelist");
        TextUtil.send(sender, "&b/wl off &7- Disable the server whitelist");
        TextUtil.send(sender, "&b/wl reload &7- Reload the config");
        TextUtil.send(sender, "&b/wl version &7- Check the latest Modrinth version");
    }

    private boolean handleVersion(CommandSender sender, String[] args) {
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl version");
            return true;
        }
        updateNotifier.checkNow(sender);
        return true;
    }

    private boolean handlePendingList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            TextUtil.send(sender, "&cUsage: /wl pl [page]");
            return true;
        }

        int page = parsePage(sender, args, "/wl pl [page]");
        if (page < 1) {
            return true;
        }

        List<PendingEntry> entries = pendingStorage.getPendingEntriesSortedByRecencyDesc();
        if (entries.isEmpty()) {
            TextUtil.send(sender, "&7No pending players.");
            return true;
        }

        int pageSize = plugin.getConfiguredPageSize();
        int totalPages = pageCount(entries.size(), pageSize);
        page = clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        TextUtil.send(sender, "&8&m---------------- &bPending players &7(" + entries.size() + ") &8&m----------------");
        TextUtil.send(sender, "&7Page &f" + page + "&7/&f" + totalPages);
        for (int i = start; i < end; i++) {
            PendingEntry entry = entries.get(i);
            sendPendingListLine(sender, entry);
        }
        sendPageNavigation(sender, "/wl pl", page, totalPages, NamedTextColor.AQUA);
        return true;
    }

    private void sendPendingListLine(CommandSender sender, PendingEntry entry) {
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
            player.sendMessage(Component.text("• ", NamedTextColor.AQUA)
                    .append(Component.text(displayName, NamedTextColor.WHITE))
                    .hoverEvent(HoverEvent.showText(hover)));
        } else {
            TextUtil.send(sender, "&7• &f" + displayName);
        }
    }

    private boolean handleWhitelistedList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            TextUtil.send(sender, "&cUsage: /wl list [page]");
            return true;
        }

        int page = parsePage(sender, args, "/wl list [page]");
        if (page < 1) {
            return true;
        }

        List<String> whitelisted = pendingStorage.getWhitelistedUsernames();
        if (whitelisted.isEmpty()) {
            TextUtil.send(sender, "&7No whitelisted players.");
            return true;
        }

        int pageSize = plugin.getConfiguredPageSize();
        int totalPages = pageCount(whitelisted.size(), pageSize);
        page = clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, whitelisted.size());

        TextUtil.send(sender, "&8&m---------------- &bWhitelisted players &7("
                + whitelisted.size() + ") &8&m----------------");
        TextUtil.send(sender, "&7Page &f" + page + "&7/&f" + totalPages);
        for (int i = start; i < end; i++) {
            sendWhitelistedListLine(sender, whitelisted.get(i));
        }
        sendPageNavigation(sender, "/wl list", page, totalPages, NamedTextColor.AQUA);
        return true;
    }

    private void sendWhitelistedListLine(CommandSender sender, String name) {
        if (sender instanceof org.bukkit.entity.Player player) {
            String uuid = pendingStorage.resolveWhitelistedUuid(name);
            Component hover = Component.text()
                    .append(Component.text("Player: ", NamedTextColor.GRAY))
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", NamedTextColor.GRAY))
                    .append(Component.text(uuid == null ? "unknown" : uuid, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Status: ", NamedTextColor.GRAY))
                    .append(Component.text("Whitelisted", NamedTextColor.GREEN))
                    .build();
            player.sendMessage(Component.text("• ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.WHITE)
                            .hoverEvent(HoverEvent.showText(hover))));
        } else {
            TextUtil.send(sender, "&7• &f" + name);
        }
    }

    private void sendPageNavigation(CommandSender sender, String command, int page, int totalPages,
            NamedTextColor color) {
        if (!(sender instanceof org.bukkit.entity.Player player) || totalPages <= 1) {
            return;
        }

        Component navigation = Component.empty();
        if (page > 1) {
            navigation = navigation.append(Component.text("‹ Previous", color)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command + " " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1)))));
        }
        if (page > 1 && page < totalPages) {
            navigation = navigation.append(Component.text("  ", NamedTextColor.DARK_GRAY));
        }
        if (page < totalPages) {
            navigation = navigation.append(Component.text("Next ›", color)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command + " " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1)))));
        }
        if (!navigation.equals(Component.empty())) {
            player.sendMessage(navigation);
        }
    }

    private int parsePage(CommandSender sender, String[] args, String usage) {
        if (args.length == 1) {
            return 1;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            TextUtil.send(sender, "&cPage must be a number.");
            TextUtil.send(sender, "&7Usage: " + usage);
            return -1;
        }
    }

    private int pageCount(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private int clampPage(int page, int totalPages) {
        return Math.max(1, Math.min(page, totalPages));
    }

    private boolean handleWhitelistToggle(CommandSender sender, String[] args, boolean enabled) {
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl " + (enabled ? "on" : "off"));
            return true;
        }

        boolean currentlyEnabled = plugin.getServer().hasWhitelist();
        if (currentlyEnabled == enabled) {
            TextUtil.send(sender, enabled ? "&eWhitelist is already enabled." : "&eWhitelist is already disabled.");
            if (sender instanceof org.bukkit.entity.Player player) {
                SoundUtil.failure(player);
            }
            return true;
        }

        plugin.getServer().setWhitelist(enabled);
        TextUtil.send(sender, enabled ? "&aWhitelist enabled." : "&cWhitelist disabled.");
        if (sender instanceof org.bukkit.entity.Player player) {
            SoundUtil.success(player);
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof org.bukkit.entity.Player player) {
                new WlGui(plugin, pendingStorage, updateNotifier).openAdd(player);
                return true;
            }
            TextUtil.send(sender, "&cUsage: /wl add <username> [username ...]");
            return true;
        }

        List<String> added = new ArrayList<>();
        List<String> alreadyWhitelisted = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String username = args[i];
            PendingEntry pendingEntry = pendingStorage.findPendingEntry(username);
            boolean addedToWhitelist = pendingStorage.addToWhitelist(username);
            if (addedToWhitelist) {
                added.add(username);
                if (pendingEntry != null) {
                    pendingStorage.removePendingOnly(username);
                }
            } else if (pendingStorage.isWhitelisted(username)) {
                alreadyWhitelisted.add(username);
            }
        }

        sendResultGroup(sender, "&a✓ Added", added, "✔", NamedTextColor.GREEN, "whitelisted");
        sendResultGroup(sender, "&e• Already whitelisted", alreadyWhitelisted, "•", NamedTextColor.YELLOW,
                "already whitelisted");
        if (sender instanceof org.bukkit.entity.Player player) {
            if (!added.isEmpty()) {
                SoundUtil.success(player);
            } else if (!alreadyWhitelisted.isEmpty()) {
                SoundUtil.failure(player);
            }
        }
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
            if (pendingStorage.removeFromWhitelist(identifier)) {
                removed.add(identifier);
            } else {
                notFound.add(identifier);
            }
        }

        sendResultGroup(sender, "&a✓ Removed", removed, "✔", NamedTextColor.GREEN, "removed");
        sendResultGroup(sender, "&c✖ Not found", notFound, "•", NamedTextColor.RED, "not found");
        if (sender instanceof org.bukkit.entity.Player player) {
            if (!removed.isEmpty()) {
                SoundUtil.success(player);
            } else if (!notFound.isEmpty()) {
                SoundUtil.failure(player);
            }
        }
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
        if (sender instanceof org.bukkit.entity.Player player) {
            if (!removed.isEmpty()) {
                SoundUtil.success(player);
            } else if (!notFound.isEmpty()) {
                SoundUtil.failure(player);
            }
        }
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            TextUtil.send(sender, "&cUsage: /wl reload");
            return true;
        }
        plugin.reloadConfig();
        if (pendingStorage.reloadFromDisk()) {
            TextUtil.send(sender, "&aReload complete.");
        } else {
            TextUtil.send(sender, "&cReload failed. Check the server console for details.");
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
                    .append(Component.text(status, NamedTextColor.WHITE))
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
