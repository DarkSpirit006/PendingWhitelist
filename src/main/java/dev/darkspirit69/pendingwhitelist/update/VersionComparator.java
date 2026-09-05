package dev.darkspirit69.pendingwhitelist.update;

/** Compares semantic plugin versions in x.y.z format. */
public final class VersionComparator {

    public boolean isNewer(String candidate, String current) {
        String normalizedCandidate = normalize(candidate);
        String normalizedCurrent = normalize(current);
        if (normalizedCandidate == null || normalizedCurrent == null) {
            return false;
        }

        String[] candidateParts = normalizedCandidate.split("\\.");
        String[] currentParts = normalizedCurrent.split("\\.");
        for (int i = 0; i < 3; i++) {
            int candidatePart = Integer.parseInt(candidateParts[i]);
            int currentPart = Integer.parseInt(currentParts[i]);
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    public String normalize(String version) {
        if (version == null) {
            return null;
        }
        String normalized = version.trim();
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches("\\d+\\.\\d+\\.\\d+") ? normalized : null;
    }

    public int countVersionsBehind(Iterable<String> releases, String current, String latest) {
        int count = 0;
        for (String release : releases) {
            if (isNewer(release, current) && !isNewer(release, latest)) {
                count++;
            }
        }
        return Math.max(count, 1);
    }
}
