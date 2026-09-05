package dev.darkspirit69.pendingwhitelist.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Central visual language for all player-facing PendingWhitelist messages and
 * GUI text.
 */
public final class MessageStyle {

    public static final NamedTextColor PRIMARY = NamedTextColor.AQUA;
    public static final NamedTextColor SECONDARY = NamedTextColor.GRAY;
    public static final NamedTextColor VALUE = NamedTextColor.WHITE;
    public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor WARNING = NamedTextColor.YELLOW;
    public static final NamedTextColor ERROR = NamedTextColor.RED;

    public static final String PRIMARY_LEGACY = "&b";
    public static final String SECONDARY_LEGACY = "&7";
    public static final String VALUE_LEGACY = "&f";
    public static final String SUCCESS_LEGACY = "&a";
    public static final String WARNING_LEGACY = "&e";
    public static final String ERROR_LEGACY = "&c";

    private MessageStyle() {
    }

    public static Component primary(String text) {
        return Component.text(text, PRIMARY);
    }

    public static Component secondary(String text) {
        return Component.text(text, SECONDARY);
    }

    public static Component value(String text) {
        return Component.text(text, VALUE);
    }

    public static Component success(String text) {
        return Component.text(text, SUCCESS);
    }

    public static Component warning(String text) {
        return Component.text(text, WARNING);
    }

    public static Component error(String text) {
        return Component.text(text, ERROR);
    }

    public static String primaryLegacy(String text) {
        return PRIMARY_LEGACY + text;
    }

    public static String secondaryLegacy(String text) {
        return SECONDARY_LEGACY + text;
    }

    public static String valueLegacy(String text) {
        return VALUE_LEGACY + text;
    }

    public static String successLegacy(String text) {
        return SUCCESS_LEGACY + text;
    }

    public static String warningLegacy(String text) {
        return WARNING_LEGACY + text;
    }

    public static String errorLegacy(String text) {
        return ERROR_LEGACY + text;
    }
}
