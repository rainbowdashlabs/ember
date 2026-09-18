/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ensures {@code data_tracking.json} is up-to-date with the live schema and
 * that every table × context is either TRACKED or IGNORED (with reason).
 *
 * <p>If this test fails, run {@code ./gradlew refreshDataTracking} to update
 * the tracking file, then {@code ./gradlew reviewDataTracking} to walk through
 * any unverified items.
 */
class DataTrackingTest extends RepositoryTestBase {

    private static DataTracking tracking;
    private static Map<String, SchemaReader.RawTable> liveSchema;

    @BeforeAll
    static void loadTracking() throws IOException, SQLException {
        tracking = DataTrackingLoader.loadFromClasspath();
        assertNotNull(tracking, "data_tracking.json not found on classpath");
        liveSchema = new SchemaReader(dataSource, schemaName).readTables();
    }

    @Test
    void allTablesAreTracked() {
        var trackedNames = tracking.tables() == null ? Map.<String, TableEntry>of() : tracking.tables();
        List<String> missing = new ArrayList<>();
        for (var name : liveSchema.keySet()) {
            if (!trackedNames.containsKey(name)) missing.add(name);
        }
        if (!missing.isEmpty()) {
            fail("The following tables exist in the schema but are not in data_tracking.json:\n  "
                    + String.join("\n  ", missing)
                    + "\n\nRun ./gradlew refreshDataTracking to add them.");
        }
    }

    @Test
    void noStaleTablesInTracking() {
        var trackedNames = tracking.tables() == null ? Map.<String, TableEntry>of() : tracking.tables();
        List<String> stale = new ArrayList<>();
        for (var name : trackedNames.keySet()) {
            if (!liveSchema.containsKey(name)) stale.add(name);
        }
        if (!stale.isEmpty()) {
            fail("The following tables are in data_tracking.json but no longer exist in the schema:\n  "
                    + String.join("\n  ", stale)
                    + "\n\nRun ./gradlew refreshDataTracking to remove them.");
        }
    }

    @Test
    void noTableSchemaHasChangedWithoutReview() {
        List<String> changed = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var raw = liveSchema.get(entry.getKey());
            if (raw == null) continue;
            String expected = HashComputer.tableHash(raw);
            String stored = entry.getValue().tableHash();
            if (!expected.equals(stored)) {
                changed.add(entry.getKey());
            }
        }
        if (!changed.isEmpty()) {
            fail("The following tables have changed since the tracking file was last refreshed:\n  "
                    + String.join("\n  ", changed)
                    + "\n\nRun ./gradlew refreshDataTracking to update.");
        }
    }

    @Test
    void topLevelSchemaHashIsCurrent() {
        // Recompute from the tracking file's current tableHashes; should match stored top-level hash
        var sorted = new TreeMap<>(tracking.tables());
        String recomputed = HashComputer.schemaHash(sorted);
        assertEquals(
                tracking.schemaHash(),
                recomputed,
                "Top-level schemaHash does not match the combined tableHashes. Run ./gradlew refreshDataTracking.");
    }

    @Test
    void ignoredContextsHaveReason() {
        List<String> missing = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var t = entry.getValue();
            String name = entry.getKey();
            checkIgnoredReason(
                    missing,
                    name,
                    "stationTransfer",
                    t.stationTransfer() == null ? null : t.stationTransfer().status(),
                    t.stationTransfer() == null ? null : t.stationTransfer().reason());
            checkIgnoredReason(
                    missing,
                    name,
                    "gdprExport",
                    t.gdprExport() == null ? null : t.gdprExport().status(),
                    t.gdprExport() == null ? null : t.gdprExport().reason());
            checkIgnoredReason(
                    missing,
                    name,
                    "gdprDeletion",
                    t.gdprDeletion() == null ? null : t.gdprDeletion().status(),
                    t.gdprDeletion() == null ? null : t.gdprDeletion().reason());
        }
        if (!missing.isEmpty()) {
            fail("The following IGNORED contexts are missing a reason:\n  " + String.join("\n  ", missing));
        }
    }

    @Test
    void retainStrategyHasLegalBasis() {
        List<String> missing = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var deletion = entry.getValue().gdprDeletion();
            if (deletion == null || deletion.strategies() == null) continue;
            for (var strategy : deletion.strategies()) {
                if (strategy.strategy() == Strategy.RETAIN
                        && (strategy.legalBasis() == null
                                || strategy.legalBasis().isBlank())) {
                    missing.add(entry.getKey() + "." + strategy.column());
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("The following RETAIN strategies are missing a legalBasis:\n  " + String.join("\n  ", missing));
        }
    }

    @Test
    void noUnverifiedStatusesRemain() {
        List<String> unverified = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var t = entry.getValue();
            String name = entry.getKey();
            if (t.stationTransfer() != null && t.stationTransfer().status() == Status.UNVERIFIED)
                unverified.add(name + " (stationTransfer)");
            if (t.gdprExport() != null && t.gdprExport().status() == Status.UNVERIFIED)
                unverified.add(name + " (gdprExport)");
            if (t.gdprDeletion() != null && t.gdprDeletion().status() == Status.UNVERIFIED)
                unverified.add(name + " (gdprDeletion)");
        }
        if (!unverified.isEmpty()) {
            fail("The following table × context combinations are still UNVERIFIED ("
                    + unverified.size() + " total):\n  "
                    + String.join("\n  ", unverified.subList(0, Math.min(20, unverified.size())))
                    + (unverified.size() > 20 ? "\n  ... and " + (unverified.size() - 20) + " more" : "")
                    + "\n\nRun ./gradlew reviewDataTracking to walk through them.");
        }
    }

    @Test
    void noUnverifiedColumnsRemain() {
        List<String> unverified = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var t = entry.getValue();
            if (t.columns() == null) continue;
            for (var col : t.columns()) {
                if (!col.verified()) {
                    unverified.add(entry.getKey() + "." + col.name());
                }
            }
        }
        if (!unverified.isEmpty()) {
            fail("The following columns are not yet verified (" + unverified.size() + " total):\n  "
                    + String.join("\n  ", unverified.subList(0, Math.min(20, unverified.size())))
                    + (unverified.size() > 20 ? "\n  ... and " + (unverified.size() - 20) + " more" : "")
                    + "\n\nRun ./gradlew reviewDataTracking to verify them.");
        }
    }

    /**
     * Every column a rule names has to be a column the table actually has.
     *
     * <p>The columns of this file are refreshed from the live schema, while the rules written against
     * them are kept by hand, so a renamed or dropped column leaves its rules pointing at nothing. The
     * engine skips a rule it cannot resolve, which means an erasure declared here and believed to be
     * running quietly does not run at all: deleting an account reported one such skip per stale rule
     * and carried on. Nothing else in this file compares the two halves, so it is compared here.
     */
    @Test
    void everyRuleNamesAColumnTheTableHas() {
        List<String> dangling = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            var table = entry.getValue();
            if (table.columns() == null) continue;
            var columns = table.columns().stream().map(ColumnEntry::name).collect(Collectors.toSet());

            var deletion = table.gdprDeletion();
            if (deletion != null && deletion.strategies() != null) {
                for (var strategy : deletion.strategies()) {
                    if (!columns.contains(strategy.column())) {
                        dangling.add(
                                entry.getKey() + "." + strategy.column() + " (deletion " + strategy.strategy() + ")");
                    }
                }
            }

            var export = table.gdprExport();
            if (export != null && export.identityColumns() != null) {
                for (var identity : export.identityColumns()) {
                    if (!columns.contains(identity.column())) {
                        dangling.add(entry.getKey() + "." + identity.column() + " (identity " + identity.type() + ")");
                    }
                }
            }
        }
        if (!dangling.isEmpty()) {
            fail("These rules name columns their table does not have (" + dangling.size() + " total):\n  "
                    + String.join("\n  ", dangling)
                    + "\n\nName the column the table really carries, or drop the rule.");
        }
    }

    @Test
    void formatVersionMatches() {
        assertEquals(
                DataTracking.CURRENT_VERSION,
                tracking.version(),
                "data_tracking.json format version mismatch - schema migration may be needed");
    }

    @Test
    void schemaHashIsSet() {
        assertNotNull(tracking.schemaHash(), "schemaHash is null");
        assertNotEquals("", tracking.schemaHash(), "schemaHash is empty");
    }

    private static void checkIgnoredReason(
            List<String> out, String table, String context, Status status, String reason) {
        if (status == Status.IGNORED && (reason == null || reason.isBlank())) {
            out.add(table + " (" + context + ")");
        }
    }
}
