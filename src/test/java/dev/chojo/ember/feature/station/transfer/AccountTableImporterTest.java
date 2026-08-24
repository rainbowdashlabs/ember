/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What happens to the name an account signs in with when the station it belongs to arrives from
 * another instance. Two instances name their people independently, so the name can already be
 * somebody else's here.
 */
class AccountTableImporterTest extends RepositoryTestBase {

    private static AccountTableImporter importer;
    private static Station station;
    private static Account resident;
    private static final List<Integer> arrived = new ArrayList<>();

    @BeforeAll
    static void setup() {
        importer = new AccountTableImporter(accountRepo);
        station = stationRepo.create("Arrival Station");
        resident = accountRepo.create("resident@arrival.test", "Rita", "Resident");
        accountRepo.updateUsername(resident.id(), "taken.name");
    }

    @AfterAll
    static void cleanup() {
        arrived.forEach(accountRepo::delete);
        accountRepo.delete(resident.id());
        stationRepo.delete(station.id());
    }

    private Account importOne(int sourceId, String email, String username) {
        var row = new HashMap<String, Object>();
        row.put("id", sourceId);
        row.put("uid", UUID.randomUUID().toString());
        row.put("email", email);
        row.put("username", username);
        row.put("first_name", "Neu");
        row.put("last_name", "Ankunft");

        var idMap = new IdRemapper();
        importer.importRows(new StationImportContext(station.id(), idMap), List.of((Map<String, Object>) row));

        int accountId = idMap.get("account", sourceId);
        arrived.add(accountId);
        return accountRepo.findById(accountId).orElseThrow();
    }

    @Test
    void aFreeNameArrivesWithTheAccount() {
        var account = importOne(9001, "free@arrival.test", "free.name");

        assertEquals("free.name", account.username());
    }

    @Test
    void aTakenNameIsDroppedAndTheAddressIsTheLoginNameAgain() {
        var account = importOne(9002, "colliding@arrival.test", "TAKEN.NAME");

        assertNull(account.username());
        assertEquals("colliding@arrival.test", account.loginName());
    }

    @Test
    void anArrivalWithNeitherHasNoWayIn() {
        var account = importOne(9003, null, "taken.name");

        assertNull(account.username());
        assertNull(account.loginName());
    }

    @Test
    void anAccountMergedByAddressKeepsTheNameItAlreadyHad() {
        var row = new HashMap<String, Object>();
        row.put("id", 9004);
        row.put("uid", UUID.randomUUID().toString());
        row.put("email", "resident@arrival.test");
        row.put("username", "brought.along");
        row.put("first_name", "Rita");
        row.put("last_name", "Resident");

        var idMap = new IdRemapper();
        importer.importRows(new StationImportContext(station.id(), idMap), List.of((Map<String, Object>) row));

        assertEquals(resident.id(), idMap.get("account", 9004));
        assertEquals(
                "taken.name", accountRepo.findById(resident.id()).orElseThrow().username());
    }
}
