package dev.toka.signs;

import java.util.*;

/** Remembers which sign records were found without their sign, pass after pass, so that one odd look (a chunk still loading) never removes anything. */
final class OrphanTracker {
    private record Entry(SignRegistry.SignRecord record, int passes, int lastPass) { }

    private final Map<Integer, Entry> entries = new HashMap<>();
    private int pass;

    void beginPass() { pass++; }

    /** The sign is there (or the place holds a sign block whose data is only late): the record is not a suspect. */
    void alive(int id) { entries.remove(id); }

    /** The sign of this record was not found in this pass. A record that changed since the last look starts again. Returns the passes counted. */
    int missing(SignRegistry.SignRecord record) {
        Entry old = entries.get(record.id());
        int count = old == null || !old.record().equals(record) ? 1 : old.lastPass() == pass ? old.passes() : old.passes() + 1;
        entries.put(record.id(), new Entry(record, count, pass));
        return count;
    }

    /** Forgets the records that are not in the registry any more. */
    void retain(Set<Integer> ids) { entries.keySet().retainAll(ids); }

    /** The records seen missing in this pass that were also missing in at least {@code needed} passes. */
    List<SignRegistry.SignRecord> confirmed(int needed) {
        List<SignRegistry.SignRecord> result = new ArrayList<>();
        for (Entry entry : entries.values()) if (entry.lastPass() == pass && entry.passes() >= needed) result.add(entry.record());
        result.sort(Comparator.comparingInt(SignRegistry.SignRecord::id));
        return result;
    }
}
