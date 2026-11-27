/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryTagService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tagging many pieces at once over HTTP: only somebody who may edit the inventory may do it, and a
 * piece of another station is refused as gone.
 */
class InventoryTagRoutesTest extends RepositoryTestBase {
    private static Station station;
    private static Station elsewhere;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int strangerInventoryId;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("TagRouteStation");
        elsewhere = stationRepo.create("TagRouteElsewhere");
        account = accountRepo.create("tagroute@test.example", "Tag", "Route");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Festmaterial", InventoryType.INTERNAL, false)
                .id();
        strangerInventoryId = inventoryRepo
                .create(elsewhere.id(), "Fremdes", InventoryType.INTERNAL, false)
                .id();
        harness = RouteHarness.serving(
                        new InventoryTagRoutes(inventoryTagService, mock(ClusterInventoryTagService.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(account.id());
    }

    private static Response batch(String requestBody, StationPermission... permissions) {
        return harness.request(client -> client.post(
                PREFIX + "/inventories/%d/item-tags/batch".formatted(inventoryId),
                body(requestBody),
                harness.as(signedIn(member, permissions))));
    }

    @Test
    void anEditorTagsManyPiecesAndGetsTheirTagsBack() {
        var bench = inventoryRepo.createItem(inventoryId, "TR-1", "Bierbank", null, null);
        var table = inventoryRepo.createItem(inventoryId, "TR-2", "Biertisch", null, null);

        var answer = batch(
                "{\"itemIds\": [%d, %d], \"add\": [\"Garnitur\"], \"remove\": []}".formatted(bench.id(), table.id()),
                StationPermission.INVENTORY_READ,
                StationPermission.INVENTORY_EDIT);

        assertEquals(200, answer.code());
        var rows = json(answer);
        assertEquals(2, rows.size());
        assertEquals(bench.id(), rows.get(0).get("itemId").asInt());
        assertEquals("Garnitur", rows.get(0).get("tags").get(0).get("name").asString());
        assertEquals("Garnitur", rows.get(1).get("tags").get(0).get("name").asString());

        var bare = json(batch(
                "{\"itemIds\": [%d, null], \"add\": [], \"remove\": [\"garnitur\"]}".formatted(bench.id()),
                StationPermission.INVENTORY_READ,
                StationPermission.INVENTORY_EDIT));
        assertEquals(1, bare.size());
        assertEquals(0, bare.get(0).get("tags").size(), "a piece left with no tag still answers, with none");

        for (var tag : inventoryTagService.findByStation(station.id())) {
            inventoryTagService.delete(station.id(), tag.id());
        }
        inventoryRepo.deleteItem(bench.id());
        inventoryRepo.deleteItem(table.id());
    }

    @Test
    void aReaderMayNotTag() {
        var bench = inventoryRepo.createItem(inventoryId, "TR-3", "Bierbank", null, null);

        var answer = batch(
                "{\"itemIds\": [%d], \"add\": [\"Garnitur\"], \"remove\": []}".formatted(bench.id()),
                StationPermission.INVENTORY_READ);

        assertEquals(403, answer.code());
        assertTrue(inventoryTagService.findTagsForItem(station.id(), bench.id()).isEmpty());
        inventoryRepo.deleteItem(bench.id());
    }

    @Test
    void aPieceOfAnotherStationIsRefusedAsGone() {
        var foreign = inventoryRepo.createItem(strangerInventoryId, "TR-4", "Fremdbank", null, null);

        var answer = batch(
                "{\"itemIds\": [%d], \"add\": [\"Garnitur\"], \"remove\": []}".formatted(foreign.id()),
                StationPermission.INVENTORY_READ,
                StationPermission.INVENTORY_EDIT);

        assertEquals("I-294", refusalOf(answer).code());
        inventoryRepo.deleteItem(foreign.id());
    }
}
