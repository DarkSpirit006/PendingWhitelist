package dev.darkspirit69.pendingwhitelist.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides normal logging and optional detailed diagnostics controlled by
 * config.
 */
public final class DebugLog {

    private static volatile Logger logger = Logger.getLogger("PendingWhitelist");
    private static volatile boolean debugEnabled;

    private DebugLog() {
    }

    /** Refreshes the logger and debug flag from the plugin configuration. */
    public static void initialize(Logger pluginLogger, boolean enabled) {
        logger = pluginLogger;
        debugEnabled = enabled;
    }

    public static boolean isEnabled() {
        return debugEnabled;
    }

    public static void debug(String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + message);
        }
    }

    public static void info(String message) {
        logger.info("[INFO] " + message);
    }

    public static void warn(String message) {
        logger.warning("[WARN] " + message);
    }

    public static void error(String message) {
        logger.severe("[ERROR] " + message);
    }

    public static void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, "[ERROR] " + message, throwable);
    }
}
