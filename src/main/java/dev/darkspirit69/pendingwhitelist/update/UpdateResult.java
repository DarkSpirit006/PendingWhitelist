package dev.darkspirit69.pendingwhitelist.update;

import java.util.List;

/** Immutable result of a Modrinth update lookup. */
public record UpdateResult(String latestVersion, List<String> releaseVersions) {

    public static UpdateResult empty() {
        return new UpdateResult(null, List.of());
    }

    public boolean hasRelease() {
        return latestVersion != null;
    }
}
