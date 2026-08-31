/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryTag;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTagServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station stranger;
    private static Account account;
    private static int inventoryId;
    private static int strangerInventoryId;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("tagsvc@test.example", "Tag", "Service");
        station = stationRepo.create("TagSvcStation");
        stranger = stationRepo.create("TagSvcStrangerStation");
        inventoryId = inventoryRepo
                .create(station.id(), "Gemeindematerial", InventoryType.INTERNAL, false)
                .id();
        strangerInventoryId = inventoryRepo
                .create(stranger.id(), "Fremdes", InventoryType.INTERNAL, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(stranger.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aWordAlreadyThereIsUsedRatherThanRepeated() {
        var first = inventoryTagService.create(station.id(), "Funk", "#3694FF");
        var again = inventoryTagService.create(station.id(), "  funk ", null);
        assertEquals(first.id(), again.id());
        assertEquals("#3694FF", again.color());
        assertEquals(
                first.id(),
                inventoryTagService.findById(first.id()).orElseThrow().id());
        assertEquals(1, inventoryTagService.findByStation(station.id()).size());
        inventoryTagService.delete(station.id(), first.id());
    }

    @Test
    void aWordThatIsNoWordIsRefused() {
        assertThrows(BadRequestResponse.class, () -> inventoryTagService.create(station.id(), "   ", null));
        assertThrows(BadRequestResponse.class, () -> inventoryTagService.create(station.id(), null, null));
    }

    @Test
    void renamingOntoAnotherWordIsRefusedAndRenamingItselfIsNot() {
        var funk = inventoryTagService.create(station.id(), "RenameFunk", null);
        var licht = inventoryTagService.create(station.id(), "RenameLicht", null);

        assertThrows(
                BadRequestResponse.class,
                () -> inventoryTagService.update(station.id(), licht.id(), "renamefunk", null, 0));

        var renamed = inventoryTagService.update(station.id(), licht.id(), " RenameLicht ", "#00C507", 3);
        assertEquals("RenameLicht", renamed.name());
        assertEquals("#00C507", renamed.color());
        assertEquals(3, renamed.position());

        inventoryTagService.delete(station.id(), funk.id());
        inventoryTagService.delete(station.id(), licht.id());
    }

    @Test
    void aWordOfAnotherStationIsNotThereAtAll() {
        var theirs = inventoryTagService.create(stranger.id(), "Fremd", null);
        assertThrows(
                NotFoundResponse.class, () -> inventoryTagService.update(station.id(), theirs.id(), "Neu", null, 0));
        assertThrows(NotFoundResponse.class, () -> inventoryTagService.delete(station.id(), theirs.id()));
        assertThrows(NotFoundResponse.class, () -> inventoryTagService.update(station.id(), -1, "Neu", null, 0));
        inventoryTagService.delete(stranger.id(), theirs.id());
    }

    @Test
    void theFormSpeaksInWordsAndWritesDownWhatIsMissing() {
        var item = inventoryRepo.createItem(inventoryId, "TS-100", "Funkgerät", null, null);

        var worn = inventoryTagService.setItemTags(
                station.id(), item.id(), Arrays.asList("Funk", " funk ", "  ", null, "Gemeinde"));
        assertEquals(2, worn.size());
        assertEquals(
                List.of("Funk", "Gemeinde"),
                worn.stream().map(InventoryTag::name).sorted().toList());

        assertEquals(
                2, inventoryTagService.findTagsForItem(station.id(), item.id()).size());
        assertEquals(
                2,
                inventoryTagService
                        .findTagsForItems(List.of(item.id()))
                        .get(item.id())
                        .size());
        assertEquals(2, inventoryTagService.countItemsPerTag(station.id()).size());

        assertTrue(
                inventoryTagService.setItemTags(station.id(), item.id(), null).isEmpty());
        assertTrue(inventoryTagService
                .setItemTags(station.id(), item.id(), List.of())
                .isEmpty());

        for (var tag : inventoryTagService.findByStation(station.id())) {
            inventoryTagService.delete(station.id(), tag.id());
        }
        inventoryRepo.deleteItem(item.id());
    }

    @Test
    void aThingOfAnotherStationIsNotThereAtAll() {
        var theirs = inventoryRepo.createItem(strangerInventoryId, "TS-200", "Fremdes Ding", null, null);
        assertThrows(NotFoundResponse.class, () -> inventoryTagService.findTagsForItem(station.id(), theirs.id()));
        assertThrows(
                NotFoundResponse.class,
                () -> inventoryTagService.setItemTags(station.id(), theirs.id(), List.of("Funk")));
        assertThrows(NotFoundResponse.class, () -> inventoryTagService.findTagsForItem(station.id(), -1));
        inventoryRepo.deleteItem(theirs.id());
    }

    @Test
    void aSearchForNoWordFindsNothingRatherThanEverything() {
        assertTrue(
                inventoryTagService.findItemsByTag(List.of(station.id()), null).isEmpty());
        assertTrue(
                inventoryTagService.findItemsByTag(List.of(station.id()), "  ").isEmpty());
        assertTrue(
                inventoryTagService.findSharedItemsByTag(station.id(), 1, null).isEmpty());
        assertTrue(
                inventoryTagService.findSharedItemsByTag(station.id(), 1, " ").isEmpty());
    }

    @Test
    void aWordFindsTheThingsWearingItAcrossInventories() {
        var second = inventoryRepo
                .create(station.id(), "Sonstiges", InventoryType.INTERNAL, false)
                .id();
        var radio = inventoryRepo.createItem(inventoryId, "TS-300", "Funkgerät blau", null, null);
        var antenna = inventoryRepo.createItem(second, "TS-301", "Antenne", null, null);
        inventoryTagService.setItemTags(station.id(), radio.id(), List.of("Funk"));
        inventoryTagService.setItemTags(station.id(), antenna.id(), List.of("Funk"));

        var found = inventoryTagService.findItemsByTag(List.of(station.id()), "FUNK");
        assertEquals(2, found.size());
        assertEquals(
                List.of("Antenne", "Funkgerät blau"),
                found.stream().map(item -> item.name()).sorted().toList());
        assertEquals("Funk", found.getFirst().tagName());

        for (var tag : inventoryTagService.findByStation(station.id())) {
            inventoryTagService.delete(station.id(), tag.id());
        }
        inventoryRepo.deleteItem(radio.id());
        inventoryRepo.deleteItem(antenna.id());
        inventoryRepo.delete(second);
    }
}
