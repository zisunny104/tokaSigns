package dev.toka.signs;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.*;

public class OrphanLogTest {
    private static final Instant LIMIT = Instant.parse("2026-10-15T00:00:00Z");

    @Test public void oldLinesAreDropped() {
        assertTrue(OrphanCleaner.isOlder("2026-10-01T10:00:00Z orphan {\"id\":1}", LIMIT));
    }

    @Test public void newLinesAreKept() {
        assertFalse(OrphanCleaner.isOlder("2026-10-20T10:00:00Z orphan {\"id\":1}", LIMIT));
    }

    @Test public void linesWithoutATimeAreKept() {
        assertFalse(OrphanCleaner.isOlder("hand written note", LIMIT));
        assertFalse(OrphanCleaner.isOlder("", LIMIT));
    }
}
