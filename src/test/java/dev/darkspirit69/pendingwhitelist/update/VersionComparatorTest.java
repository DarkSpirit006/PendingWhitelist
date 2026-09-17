package dev.darkspirit69.pendingwhitelist.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

    private final VersionComparator comparator = new VersionComparator();

    @Test
    void shouldNormalizeValidVersions() {
        assertEquals("2.2.2", comparator.normalize("v2.2.2"));
        assertEquals("1.20.1", comparator.normalize(" 1.20.1 "));
        assertNull(comparator.normalize("2.2"));
        assertNull(comparator.normalize("release"));
    }

    @Test
    void shouldCompareSemanticVersions() {
        assertTrue(comparator.isNewer("2.2.2", "2.2.1"));
        assertTrue(comparator.isNewer("2.10.0", "2.9.9"));
        assertFalse(comparator.isNewer("2.2.1", "2.2.1"));
        assertFalse(comparator.isNewer("2.2.0", "2.2.1"));
    }

    @Test
    void shouldCountReleasedVersionsBehindCurrent() {
        int count = comparator.countVersionsBehind(
                List.of("2.0.0", "2.1.0", "2.2.0", "2.2.1", "2.2.2"), "2.0.0", "2.2.2");

        assertEquals(4, count);
    }
}
