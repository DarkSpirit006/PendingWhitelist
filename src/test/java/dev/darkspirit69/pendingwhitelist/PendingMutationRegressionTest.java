package dev.darkspirit69.pendingwhitelist;

import dev.darkspirit69.pendingwhitelist.model.PendingEntry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingMutationRegressionTest {

    @Test
    void shouldMatchPendingEntryByUuidWhenNameChanges() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        PendingEntry entry = new PendingEntry(uuid.toString(), "OldName", 1, 100L, 200L);

        assertTrue(entry.matchesIdentifier(uuid.toString()));
        assertTrue(entry.matchesIdentifier("123E4567-E89B-12D3-A456-426614174000"));
        assertFalse(entry.matchesIdentifier("NewName"));
    }
}
