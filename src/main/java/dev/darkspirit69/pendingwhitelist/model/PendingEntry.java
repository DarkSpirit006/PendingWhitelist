package dev.darkspirit69.pendingwhitelist.model;

public record PendingEntry(String uuid, String name, int attempts, long firstAttempt, long lastAttempt) {

    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid != null && !uuid.isBlank() ? uuid : "unknown";
    }

    public boolean matchesIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String normalized = identifier.trim();
        if (uuid != null && uuid.equalsIgnoreCase(normalized)) {
            return true;
        }
        return name != null && name.equalsIgnoreCase(normalized);
    }
}
