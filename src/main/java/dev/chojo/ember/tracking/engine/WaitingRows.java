/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.ForeignKey;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rows of one import run that name a row which has not arrived yet.
 *
 * <p>Tables are imported in the order of their foreign keys, but that order cannot hold everywhere:
 * a borrowed item names the line of the lending request it came in on, and a line names the item it
 * asks for. A row whose reference cannot be followed yet waits here instead of being given up, is
 * written as soon as the row it names arrives, and is only left behind once the run is over and that
 * row never came.
 *
 * <p>The run happens on one thread, so nothing here is synchronised.
 */
public final class WaitingRows {
    private final List<Waiting> rows = new ArrayList<>();
    private final Set<String> held = new HashSet<>();

    /**
     * Holds a row back until the row it names arrives.
     *
     * @param table   the table the row belongs to
     * @param row     the row as it was sent
     * @param blocker the reference that cannot be followed yet
     */
    void hold(String table, Map<String, Object> row, ForeignKey blocker) {
        Integer sourceId = GenericTableImporter.toInteger(row.get("id"));
        rows.add(new Waiting(table, row, blocker, sourceId));
        if (sourceId != null) held.add(key(table, sourceId));
    }

    /**
     * Whether a row of {@code table} with that id at the source is still waiting, which means it may
     * still arrive and a reference to it must not be emptied yet.
     *
     * @param table    the table
     * @param sourceId the row's id at the source
     * @return {@code true} while that row waits
     */
    boolean holds(String table, int sourceId) {
        return held.contains(key(table, sourceId));
    }

    /**
     * The waiting rows whose reference that held them back can be followed now. They stay held until
     * they are {@linkplain #release(Waiting) released}.
     *
     * @param idMap the ids the run has handed out so far
     * @return the rows to try again, in the order they were held
     */
    List<Waiting> unblocked(IdRemapper idMap) {
        return rows.stream().filter(waiting -> waiting.unblockedBy(idMap)).toList();
    }

    /**
     * Every waiting row, to be tried once more. They stay held until they are
     * {@linkplain #release(Waiting) released}.
     *
     * @return the rows, in the order they were held
     */
    List<Waiting> all() {
        return List.copyOf(rows);
    }

    /**
     * Lets a row go, to be written or held again with whatever holds it back now.
     *
     * @param waiting the row
     */
    void release(Waiting waiting) {
        rows.remove(waiting);
        Integer sourceId = waiting.sourceId();
        if (sourceId != null) held.remove(key(waiting.table(), sourceId));
    }

    /** Forgets every waiting row, once the run has given them up. */
    void clear() {
        rows.clear();
        held.clear();
    }

    /**
     * The waiting rows counted by table and by the reference that holds them back, which is what the
     * log names when the run leaves them behind.
     *
     * @return the count per table and reference, in the order they were first held
     */
    Map<String, Integer> countByReference() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Waiting waiting : rows) {
            String reference = waiting.table() + ": " + waiting.blocker().column() + " -> "
                    + waiting.blocker().refTable();
            counts.merge(reference, 1, Integer::sum);
        }
        return counts;
    }

    private static String key(String table, int sourceId) {
        return table + '#' + sourceId;
    }

    /**
     * One waiting row.
     *
     * @param table    the table it belongs to
     * @param row      the row as it was sent
     * @param blocker  the reference that held it back last
     * @param sourceId its id at the source, or {@code null} for a table without one
     */
    record Waiting(
            String table,
            Map<String, Object> row,
            ForeignKey blocker,
            @Nullable Integer sourceId) {

        /** Whether the row the blocking reference names has arrived since. */
        boolean unblockedBy(IdRemapper idMap) {
            Integer value = GenericTableImporter.toInteger(row.get(blocker.column()));
            return value != null && idMap.get(blocker.refTable(), value) != null;
        }
    }
}
