package dev.darkspirit69.pendingwhitelist.command.handler;

import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.command.WlCommandContext;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Handles list and pagination commands. */
public final class WlListHandler {

    private final WlCommandContext context;

    public WlListHandler(WlCommandContext context) {
        this.context = context;
    }

    public boolean pending(CommandSender sender, String[] args) {
        DebugLog.debug("Listing pending entries for " + sender.getName());
        if (args.length > 2) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl pl [page]");
            return true;
        }

        int page = context.parsePage(sender, args, "/wl pl [page]");
        if (page < 1) {
            return true;
        }

        List<PendingEntry> entries = context.repository().getPendingEntriesSortedByRecencyDesc();
        if (entries.isEmpty()) {
            TextUtil.send(sender, MessageStyle.secondaryLegacy("No pending players."));
            return true;
        }

        int pageSize = context.plugin().getConfiguredPageSize();
        int totalPages = context.pageCount(entries.size(), pageSize);
        page = context.clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "---------------- " + MessageStyle.PRIMARY_LEGACY
                + "Pending players " + MessageStyle.VALUE_LEGACY + "(" + entries.size() + ") "
                + MessageStyle.SECONDARY_LEGACY + "----------------");
        TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "Page " + MessageStyle.VALUE_LEGACY + page
                + MessageStyle.SECONDARY_LEGACY + "/" + MessageStyle.VALUE_LEGACY + totalPages);
        for (int i = start; i < end; i++) {
            sendPendingLine(sender, entries.get(i));
        }
        sendPageNavigation(sender, "/wl pl", page, totalPages);
        return true;
    }

    public boolean whitelisted(CommandSender sender, String[] args) {
        DebugLog.debug("Listing whitelisted players for " + sender.getName());
        if (args.length > 2) {
            TextUtil.send(sender, MessageStyle.ERROR_LEGACY + "Usage: /wl list [page]");
            return true;
        }

        int page = context.parsePage(sender, args, "/wl list [page]");
        if (page < 1) {
            return true;
        }

        List<String> names = context.repository().getWhitelistedUsernames();
        if (names.isEmpty()) {
            TextUtil.send(sender, MessageStyle.secondaryLegacy("No whitelisted players."));
            return true;
        }

        int pageSize = context.plugin().getConfiguredPageSize();
        int totalPages = context.pageCount(names.size(), pageSize);
        page = context.clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, names.size());

        TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "---------------- " + MessageStyle.PRIMARY_LEGACY
                + "Whitelisted players " + MessageStyle.VALUE_LEGACY + "(" + names.size() + ") "
                + MessageStyle.SECONDARY_LEGACY + "----------------");
        TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "Page " + MessageStyle.VALUE_LEGACY + page
                + MessageStyle.SECONDARY_LEGACY + "/" + MessageStyle.VALUE_LEGACY + totalPages);
        for (int i = start; i < end; i++) {
            sendWhitelistedLine(sender, names.get(i));
        }
        sendPageNavigation(sender, "/wl list", page, totalPages);
        return true;
    }

    private void sendPendingLine(CommandSender sender, PendingEntry entry) {
        String displayName = entry.displayName();
        String uuid = entry.uuid() == null || entry.uuid().isBlank() ? "unknown" : entry.uuid();

        if (sender instanceof Player player) {
            Component hover = Component.text()
                    .append(Component.text("Player: ", MessageStyle.SECONDARY))
                    .append(Component.text(displayName, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", MessageStyle.SECONDARY))
                    .append(Component.text(uuid, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("Attempts: ", MessageStyle.SECONDARY))
                    .append(Component.text(String.valueOf(entry.attempts()), MessageStyle.VALUE))
                    .build();
            player.sendMessage(Component.text("• ", MessageStyle.PRIMARY)
                    .append(Component.text(displayName, MessageStyle.VALUE))
                    .hoverEvent(HoverEvent.showText(hover)));
        } else {
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "• " + MessageStyle.VALUE_LEGACY + displayName);
        }
    }

    private void sendWhitelistedLine(CommandSender sender, String name) {
        if (sender instanceof Player player) {
            String uuid = context.repository().resolveWhitelistedUuid(name);
            Component hover = Component.text()
                    .append(Component.text("Player: ", MessageStyle.SECONDARY))
                    .append(Component.text(name, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", MessageStyle.SECONDARY))
                    .append(Component.text(uuid == null ? "unknown" : uuid, MessageStyle.VALUE))
                    .append(Component.newline())
                    .append(Component.text("Status: ", MessageStyle.SECONDARY))
                    .append(Component.text("Whitelisted", MessageStyle.SUCCESS))
                    .build();
            player.sendMessage(Component.text("• ", MessageStyle.SUCCESS)
                    .append(Component.text(name, MessageStyle.VALUE)
                            .hoverEvent(HoverEvent.showText(hover))));
        } else {
            TextUtil.send(sender, MessageStyle.SECONDARY_LEGACY + "• " + MessageStyle.VALUE_LEGACY + name);
        }
    }

    private void sendPageNavigation(CommandSender sender, String command, int page, int totalPages) {
        if (!(sender instanceof Player player) || totalPages <= 1) {
            return;
        }

        Component navigation = Component.empty();
        if (page > 1) {
            navigation = navigation.append(Component.text("‹ Previous", MessageStyle.PRIMARY)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command + " " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1)))));
        }
        if (page > 1 && page < totalPages) {
            navigation = navigation.append(Component.text("  ", MessageStyle.SECONDARY));
        }
        if (page < totalPages) {
            navigation = navigation.append(Component.text("Next ›", MessageStyle.PRIMARY)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command + " " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1)))));
        }
        player.sendMessage(navigation);
    }
}
