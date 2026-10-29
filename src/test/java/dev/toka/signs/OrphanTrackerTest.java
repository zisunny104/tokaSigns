package dev.toka.signs;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class OrphanTrackerTest {
    private static SignRegistry.SignRecord record(int id, String code) {
        return new SignRegistry.SignRecord(id, 1, 64, 1, "w", 10, code, null, -1);
    }

    @Test public void needsSeveralPassesInARow() {
        var tracker = new OrphanTracker();
        var sign = record(1, "a");
        for (int pass = 1; pass <= 3; pass++) {
            tracker.beginPass();
            assertEquals(pass, tracker.missing(sign));
            assertEquals(pass >= 3, tracker.confirmed(3).contains(sign));
        }
    }

    @Test public void aSignSeenAliveStartsAgain() {
        var tracker = new OrphanTracker();
        var sign = record(1, "a");
        tracker.beginPass(); tracker.missing(sign);
        tracker.beginPass(); tracker.missing(sign);
        tracker.beginPass(); tracker.alive(1);
        tracker.beginPass();
        assertEquals(1, tracker.missing(sign));
        assertTrue(tracker.confirmed(3).isEmpty());
    }

    @Test public void aChangedRecordStartsAgain() {
        var tracker = new OrphanTracker();
        tracker.beginPass(); tracker.missing(record(1, "a"));
        tracker.beginPass(); tracker.missing(record(1, "a"));
        tracker.beginPass();
        assertEquals(1, tracker.missing(record(1, "b")));
    }

    @Test public void aRecordNotLookedAtInThePassIsNotConfirmed() {
        var tracker = new OrphanTracker();
        var sign = record(1, "a");
        for (int i = 0; i < 3; i++) { tracker.beginPass(); tracker.missing(sign); }
        tracker.beginPass();
        assertTrue("its chunk was not loaded in this pass", tracker.confirmed(3).isEmpty());
    }

    @Test public void forgetsRemovedRecords() {
        var tracker = new OrphanTracker();
        tracker.beginPass(); tracker.missing(record(1, "a")); tracker.missing(record(2, "a"));
        tracker.retain(Set.of(2));
        tracker.beginPass(); tracker.missing(record(2, "a"));
        tracker.beginPass();
        assertEquals(1, tracker.missing(record(1, "a")));
    }
}
