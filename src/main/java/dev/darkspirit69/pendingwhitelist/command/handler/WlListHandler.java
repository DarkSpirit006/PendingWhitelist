package dev.darkspirit69.pendingwhitelist.command.handler;

import dev.darkspirit69.pendingwhitelist.command.WlCommandContext;
import dev.darkspirit69.pendingwhitelist.logging.DebugLog;
import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import dev.darkspirit69.pendingwhitelist.util.FloodgateUtil;
import dev.darkspirit69.pendingwhitelist.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Handles list and pagination commands. */
public final class WlListHandler {

    private final WlCommandContext context;

    public WlListHandler(WlCommandContext context) {
        this.context = context;
    }

    public boolean pending(CommandSender sender, String[] args) {
        DebugLog.debug("Listing pending entries for " + sender.getName());
        if (args.length > 2) {
            TextUtil.send(sender, "Usage: /wl pl [page]");
            return true;
        }

        int page = context.parsePage(sender, args, "/wl pl [page]");
        if (page < 1) {
            return true;
        }

        List<PendingEntry> entries = context.repository().getPendingEntriesSortedByRecencyDesc();
        if (entries.isEmpty()) {
            TextUtil.send(sender, "No pending players.");
            return true;
        }

        int pageSize = context.plugin().getConfiguredPageSize();
        int totalPages = context.pageCount(entries.size(), pageSize);
        page = context.clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        Component message = Component.text("Pending players (" + entries.size() + "):", MessageStyle.SECONDARY);
        for (int i = start; i < end; i++) {
            message = message.append(Component.newline()).append(pendingLine(entries.get(i)));
        }
        message = appendPageFooter(message, "/wl pl", page, totalPages);
        TextUtil.send(sender, message);
        return true;
    }

    public boolean whitelisted(CommandSender sender, String[] args) {
        DebugLog.debug("Listing whitelisted players for " + sender.getName());
        if (args.length > 2) {
            TextUtil.send(sender, "Usage: /wl list [page]");
            return true;
        }

        int page = context.parsePage(sender, args, "/wl list [page]");
        if (page < 1) {
            return true;
        }

        List<String> names = context.repository().getWhitelistedUsernames();
        if (names.isEmpty()) {
            TextUtil.send(sender, "No whitelisted players.");
            return true;
        }

        int pageSize = context.plugin().getConfiguredPageSize();
        int totalPages = context.pageCount(names.size(), pageSize);
        page = context.clampPage(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, names.size());

        Component message = whitelistedSummary(sender, names, start, end);
        message = appendPageFooter(message, "/wl list", page, totalPages);
        TextUtil.send(sender, message);
        return true;
    }

    private Component pendingLine(PendingEntry entry) {
        String displayName = entry.displayName();
        UUID pendingUuid = parseUuid(entry.uuid());
        if (pendingUuid != null && FloodgateUtil.isFloodgateId(pendingUuid)) {
            displayName = FloodgateUtil.stripPrefix(displayName);
        }
        return Component.text("- ", MessageStyle.SECONDARY)
                .append(Component.text(displayName, MessageStyle.VALUE));
    }

    private Component whitelistedSummary(CommandSender sender, List<String> names, int start, int end) {
        Component message = Component.text("Whitelisted players (" + names.size() + "):", MessageStyle.SECONDARY);
        boolean playerSender = sender instanceof Player;
        for (int i = start; i < end; i++) {
            String name = names.get(i);
            String uuid = context.repository().resolveWhitelistedUuid(name);
            String normalizedUuid = uuid == null || uuid.isBlank() ? "unknown" : uuid;
            String displayName = name;
            UUID parsedUuid = parseUuid(uuid);
            if (parsedUuid != null && FloodgateUtil.isFloodgateId(parsedUuid)) {
                displayName = FloodgateUtil.stripPrefix(displayName);
            }
            String type = parsedUuid != null && FloodgateUtil.isFloodgateId(parsedUuid) ? "Bedrock" : "Java";

            Component nameComponent = Component.text(displayName, MessageStyle.VALUE);
            if (playerSender) {
                Component hover = TextUtil.playerInfoHover(displayName, type, normalizedUuid, null);
                nameComponent = nameComponent.hoverEvent(HoverEvent.showText(hover));
            }

            message = message.append(Component.text(i == start ? " " : ", ", MessageStyle.SECONDARY))
                    .append(nameComponent);
        }
        return message;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Component appendPageFooter(Component message, String command, int page, int totalPages) {
        if (totalPages <= 1) {
            return message;
        }

        Component footer = Component.newline()
                .append(Component.text("Page " + page + " of " + totalPages, MessageStyle.SECONDARY));
        Component navigation = pageNavigation(command, page, totalPages);
        if (navigation != null) {
            footer = footer.append(Component.text("  ", MessageStyle.SECONDARY)).append(navigation);
        }
        return message.append(footer);
    }

    private Component pageNavigation(String command, int page, int totalPages) {
        Component navigation = Component.empty();
        if (page > 1) {
            navigation = navigation.append(Component.text("‹ Previous", MessageStyle.PRIMARY)
                    .clickEvent(ClickEvent.runCommand(command + " " + (page - 1))));
        }
        if (page > 1 && page < totalPages) {
            navigation = navigation.append(Component.text("  ", MessageStyle.SECONDARY));
        }
        if (page < totalPages) {
            navigation = navigation.append(Component.text("Next ›", MessageStyle.PRIMARY)
                    .clickEvent(ClickEvent.runCommand(command + " " + (page + 1))));
        }
        return navigation.children().isEmpty() ? null : navigation;
    }
}
