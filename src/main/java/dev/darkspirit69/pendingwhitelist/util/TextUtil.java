package dev.darkspirit69.pendingwhitelist.util;

import dev.darkspirit69.pendingwhitelist.text.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Objects;

/**
 * Small Adventure and legacy formatting helpers used by commands and
 * notifications.
 */
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
        Objects.requireNonNull(recipient, "recipient").sendMessage(component(message));
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
