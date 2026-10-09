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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        importer = new AccountTableImporter(accountRepo, stationMemberRepo);
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
    void anAccountFoundByAddressIsLeftAloneAndNotedForItsOwner() {
        var idMap = new IdRemapper();
        var context = new StationImportContext(station.id(), idMap);

        importer.importRows(context, List.of(residentRow(9004, "resident@arrival.test")));

        assertNull(idMap.get("account", 9004), "nothing of the bundle may point at an account it only named");
        assertFalse(idMap.arrived("account", resident.id()));
        assertEquals(resident.id(), context.foundAccount("resident@arrival.test"));
        var untouched = accountRepo.findById(resident.id()).orElseThrow();
        assertEquals("taken.name", untouched.username());
        assertEquals("Rita", untouched.firstName());
        assertFalse(accountRepo.isUnconfirmed(resident.id()));
    }

    @Test
    void anAccountOfAMemberOfTheStationIsMappedAsBefore() {
        var member = stationMemberRepo.create(station.id(), resident.id());
        try {
            var idMap = new IdRemapper();
            var context = new StationImportContext(station.id(), idMap);

            importer.importRows(context, List.of(residentRow(9005, "resident@arrival.test")));

            assertEquals(resident.id(), idMap.get("account", 9005));
            assertNull(context.foundAccount("resident@arrival.test"));
        } finally {
            stationMemberRepo.delete(member.id());
        }
    }

    @Test
    void aCreatedAccountWaitsForItsOwner() {
        var account = importOne(9006, "planted@arrival.test", null);

        assertTrue(accountRepo.isUnconfirmed(account.id()));
    }

    private static Map<String, Object> residentRow(int sourceId, String email) {
        var row = new HashMap<String, Object>();
        row.put("id", sourceId);
        row.put("uid", UUID.randomUUID().toString());
        row.put("email", email);
        row.put("username", "brought.along");
        row.put("first_name", "Someone");
        row.put("last_name", "Else");
        return row;
    }
}
