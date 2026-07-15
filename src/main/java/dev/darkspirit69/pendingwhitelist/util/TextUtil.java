package dev.darkspirit69.pendingwhitelist.util;

import org.bukkit.ChatColor;

public final class TextUtil {

    private TextUtil() {
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
