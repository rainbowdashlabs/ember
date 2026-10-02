/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The GDPR export against a real database: rows found by identity, lookups flattened into them,
 * and a lookup that cannot be followed refused by name instead of turned into broken SQL.
 */
class GenericGdprExporterTest extends RepositoryTestBase {

    private static DataTracking tracking;
    private static int accountId;

    @BeforeAll
    static void seed() throws IOException {
        tracking = DataTrackingLoader.loadFromClasspath();
        var account = accountRepo.create("gdpr-exporter@example.com", "Gdpr", "Exporter", true);
        accountRepo.createCredential(account.id(), "{bcrypt:hash:salt}");
        accountId = account.id();
    }

    private static DataTracking withLookups(String tableName, List<Lookup> lookups) {
        TableEntry original = tracking.tables().get(tableName);
        var tweaked = new TableEntry(
                original.feature(),
                original.scope(),
                original.tableHash(),
                original.columns(),
                original.foreignKeys(),
                lookups,
                original.outputShape(),
                original.flatField(),
                original.customScope(),
                original.stationTransfer(),
                original.gdprExport(),
                original.gdprDeletion());
        var tables = new LinkedHashMap<>(tracking.tables());
        tables.put(tableName, tweaked);
        return new DataTracking(
                tracking.version(), tracking.schemaHash(), tracking.generatedAt(), tables, tracking.fileStores());
    }

    @Test
    void exportsTheRowsOfAnAccountWithTheirLookups() {
        var export = new GenericGdprExporter(tracking).exportByIdentity(IdentityType.ACCOUNT_ID, accountId);

        var credentials = export.get("account_credential");
        assertEquals(1, credentials.size());
        assertEquals("gdpr-exporter@example.com", credentials.getFirst().get("account_email"));
    }

    @Test
    void aLookupWithoutATrackedForeignKeyIsRefusedByName() {
        var broken = withLookups("account_credential", List.of(new Lookup("password_hash", "email", "leaked")));

        var failure = assertThrows(IllegalStateException.class, () -> new GenericGdprExporter(broken)
                .exportByIdentity(IdentityType.ACCOUNT_ID, accountId));

        assertTrue(failure.getMessage().contains("account_credential"), failure.getMessage());
        assertTrue(failure.getMessage().contains("password_hash"), failure.getMessage());
    }

    @Test
    void theStationTransferRefusesTheSameLookup() {
        var broken = withLookups("user_tag", List.of(new Lookup("name", "name", "tag_name")));
        var station = stationRepo.create("GdprExporterTestStation");

        var failure = assertThrows(IllegalStateException.class, () -> new GenericTableExporter(broken)
                .export("user_tag", station.id(), 0, 100));

        assertTrue(failure.getMessage().contains("user_tag"), failure.getMessage());
    }
}
