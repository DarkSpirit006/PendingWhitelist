package dev.darkspirit69.pendingwhitelist.util;

import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/** Formatting helpers used by command output and notifications. */
public final class TextUtil {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    public static String color(String message) {
        return SECTION_SERIALIZER.serialize(component(message));
    }

    public static Component component(String message) {
        return AMPERSAND_SERIALIZER.deserialize(Objects.requireNonNull(message, "message"));
    }

    public static void send(CommandSender recipient, String message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(
                component(Objects.requireNonNull(message, "message")).colorIfAbsent(MessageStyle.SECONDARY));
    }

    public static void send(CommandSender recipient, Component message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(Objects.requireNonNull(message, "message"));
    }

    public static void sendResult(CommandSender recipient, String action, String value,
            NamedTextColor actionColor, String suffix) {
        String messageSuffix = suffix == null || suffix.isBlank() ? "." : " " + suffix + ".";
        Component message = Component.text(action, actionColor)
                .append(Component.space())
                .append(Component.text(value, MessageStyle.VALUE))
                .append(Component.text(messageSuffix, MessageStyle.SECONDARY));
        send(recipient, message);
    }

    public static void sendCountResult(CommandSender recipient, String action, int count,
            NamedTextColor actionColor, String suffix, List<String> names) {
        Objects.requireNonNull(names, "names");
        String word = count == 1 ? "player" : "players";
        Component hover = Component.text(action + " players", MessageStyle.SECONDARY);
        if (names.isEmpty()) {
            hover = hover.append(Component.newline())
                    .append(Component.text("None", MessageStyle.VALUE));
        } else {
            for (String name : names) {
                hover = hover.append(Component.newline())
                        .append(Component.text("• ", MessageStyle.SECONDARY))
                        .append(Component.text(name, MessageStyle.VALUE));
            }
        }
        Component countComponent = Component.text(count + " " + word, MessageStyle.VALUE)
                .hoverEvent(HoverEvent.showText(hover));
        String messageSuffix = suffix == null || suffix.isBlank() ? "." : " " + suffix + ".";
        Component message = Component.text(action, actionColor)
                .append(Component.space())
                .append(countComponent)
                .append(Component.text(messageSuffix, MessageStyle.SECONDARY));
        send(recipient, message);
    }

    public static Component playerInfoHover(String displayName, String type, String uuid, Integer attempts) {
        Component hover = Component.text()
                .append(Component.text("Player: ", MessageStyle.SECONDARY))
                .append(Component.text(displayName, MessageStyle.VALUE))
                .append(Component.newline())
                .append(Component.text("Type: ", MessageStyle.SECONDARY))
                .append(Component.text(type, MessageStyle.VALUE))
                .append(Component.newline())
                .append(Component.text("UUID: ", MessageStyle.SECONDARY))
                .append(Component.text(uuid == null || uuid.isBlank() ? "unknown" : uuid, MessageStyle.VALUE))
                .build();
        if (attempts != null) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Attempts: ", MessageStyle.SECONDARY))
                    .append(Component.text(String.valueOf(attempts), MessageStyle.VALUE));
        }
        return hover;
    }

    public static void sendSuccess(CommandSender recipient, String message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(MessageStyle.success(message));
    }

    public static void sendWarning(CommandSender recipient, String message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(MessageStyle.warning(message));
    }

    public static void sendError(CommandSender recipient, String message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(MessageStyle.error(message));
    }

    public static void sendPrimary(CommandSender recipient, String message) {
        Objects.requireNonNull(recipient, "recipient").sendMessage(MessageStyle.primary(message));
    }
}
