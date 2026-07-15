package dev.darkspirit69.pendingwhitelist.model;

public record PendingEntry(int attempts, long firstAttempt, long lastAttempt) {
}
