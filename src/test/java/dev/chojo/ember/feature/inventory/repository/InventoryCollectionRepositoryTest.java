/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.CollectionLine;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryCollection;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollectionLine;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryCollectionRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static Inventory games;
    private static Inventory radios;
    private static LendingRepository lendingRepo;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("collection@test.example", "Collection", "User");
        station = stationRepo.create("CollectionStation");
        partner = stationRepo.create("CollectionPartner");
        member = stationMemberRepo.create(station.id(), account.id());
        games = inventoryRepo.create(station.id(), "Spiele", InventoryType.INTERNAL, false);
        radios = inventoryRepo.create(station.id(), "Funkgeraete", InventoryType.INTERNAL, false, false);
        lendingRepo = new LendingRepository();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partner.id());
        accountRepo.delete(account.id());
    }

    private static InventoryItem item(Inventory inventory, String name) {
        return inventoryRepo.createItem(inventory.id(), null, name, null, InventoryItemMetadata.empty());
    }

    private static InventoryItem clusterItem(Inventory inventory, String name) {
        return inventoryRepo.createItem(
                inventory.id(), null, name, null, InventoryItemMetadata.empty(), ItemOwner.CLUSTER, null);
    }

    private static InventoryItem itemOfKind(Inventory inventory, String name, int artId) {
        return inventoryRepo.createItem(inventory.id(), null, name, null, artId, null, null, null);
    }

    @Test
    void collectionCrudAndSummaries() {
        InventoryCollection evening =
                collectionRepo.create(station.id(), "Jugendabend", "Spiele und Laminator", member.id());
        assertEquals(station.id(), evening.stationId());
        assertEquals("Jugendabend", evening.name());
        assertEquals("Spiele und Laminator", evening.note());
        assertEquals(member.id(), evening.createdBy());
        assertTrue(evening.createdAt().isBefore(java.time.Instant.now().plusSeconds(60)));

        InventoryCollection kit = collectionRepo.create(station.id(), "Funkset", "", null);
        assertEquals(
                evening.id(),
                collectionRepo.findById(evening.id()).orElseThrow().id());
        assertTrue(collectionRepo.findById(-1).isEmpty());

        collectionRepo.addLine(evening.id(), item(games, "Siedler").id(), null, null, 1);
        collectionRepo.addLine(evening.id(), null, null, games.id(), 2);

        List<InventoryCollectionRepository.CollectionSummary> summaries =
                collectionRepo.findSummariesByStation(station.id()).stream()
                        .filter(summary -> summary.collection().stationId() == station.id())
                        .filter(summary -> List.of("Funkset", "Jugendabend")
                                .contains(summary.collection().name()))
                        .toList();
        assertEquals(
                List.of("Funkset", "Jugendabend"),
                summaries.stream().map(summary -> summary.collection().name()).toList());
        assertEquals(0, summaries.get(0).lineCount());
        assertEquals(2, summaries.get(1).lineCount());

        assertTrue(collectionRepo.update(evening.id(), "Spieleabend", "neu"));
        InventoryCollection renamed = collectionRepo.findById(evening.id()).orElseThrow();
        assertEquals("Spieleabend", renamed.name());
        assertEquals("neu", renamed.note());
        assertFalse(collectionRepo.update(-1, "nirgends", ""));

        assertFalse(collectionRepo.delete(evening.id(), partner.id()));
        assertTrue(collectionRepo.delete(evening.id(), station.id()));
        assertFalse(collectionRepo.delete(evening.id(), station.id()));
        assertTrue(collectionRepo.findLines(evening.id()).isEmpty());
        assertTrue(collectionRepo.delete(kit.id(), station.id()));
    }

    @Test
    void linesAppendReorderAndGoWithTheirItem() {
        InventoryCollection kit = collectionRepo.create(station.id(), "Ordnung", "", null);
        InventoryItem first = item(radios, "Funk blau");
        InventoryItem second = item(radios, "Funk gruen");

        CollectionLine one = collectionRepo.addLine(kit.id(), first.id(), null, null, 1);
        CollectionLine two = collectionRepo.addLine(kit.id(), second.id(), null, null, 1);
        CollectionLine three = collectionRepo.addLine(kit.id(), null, null, radios.id(), 4);
        assertEquals(0, one.position());
        assertEquals(1, two.position());
        assertEquals(2, three.position());
        assertTrue(one.namesItem());
        assertFalse(three.namesItem());
        assertEquals(4, three.quantity());
        assertEquals(radios.id(), three.inventoryId());

        assertEquals(one.id(), collectionRepo.findLine(one.id()).orElseThrow().id());
        assertTrue(collectionRepo.findLine(-1).isEmpty());

        collectionRepo.reorderLines(kit.id(), List.of(three.id(), one.id(), two.id()));
        assertEquals(
                List.of(three.id(), one.id(), two.id()),
                collectionRepo.findLines(kit.id()).stream()
                        .map(CollectionLine::id)
                        .toList());

        assertTrue(collectionRepo.updateLineQuantity(three.id(), 6));
        assertEquals(6, collectionRepo.findLine(three.id()).orElseThrow().quantity());
        assertFalse(collectionRepo.updateLineQuantity(-1, 2));

        inventoryRepo.deleteItem(second.id());
        assertEquals(
                List.of(three.id(), one.id()),
                collectionRepo.findLines(kit.id()).stream()
                        .map(CollectionLine::id)
                        .toList());

        assertTrue(collectionRepo.deleteLine(one.id()));
        assertFalse(collectionRepo.deleteLine(one.id()));
        collectionRepo.delete(kit.id(), station.id());
        inventoryRepo.deleteItem(first.id());
    }

    @Test
    void aLineRefusesTwoTargetsAndNone() {
        InventoryCollection kit = collectionRepo.create(station.id(), "Verboten", "", null);
        InventoryItem piece = item(radios, "Funk rot");
        InventoryArt kind = artRepo.create(radios.id(), "Funk rot", "", 0);

        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), piece.id(), null, radios.id(), 1));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), piece.id(), kind.id(), null, 1));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), null, kind.id(), radios.id(), 1));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), null, null, null, 1));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), piece.id(), null, null, 2));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), null, null, radios.id(), 0));
        assertThrows(Exception.class, () -> collectionRepo.addLine(kit.id(), null, kind.id(), null, 0));

        collectionRepo.delete(kit.id(), station.id());
        artRepo.delete(kind.id());
        inventoryRepo.deleteItem(piece.id());
    }

    @Test
    void resolvingCountsWhatTheStationHasAndNotWhatItMerelyOwns() {
        Inventory drawer = inventoryRepo.create(station.id(), "Resolve", InventoryType.INTERNAL, false);
        InventoryCollection kit = collectionRepo.create(station.id(), "Resolve", "", null);

        InventoryItem resting = item(drawer, "Ruht");
        InventoryItem withMember = item(drawer, "Beim Gruppenfuehrer");
        InventoryItem withPartner = item(drawer, "Beim Partner");
        InventoryItem lost = item(drawer, "Verschwunden");
        InventoryItem owned = clusterItem(drawer, "Dem Kreis gehoerend");

        inventoryRepo.updateCustody(withMember.id(), ItemCustody.WITH_MEMBER, station.id(), member.id(), null);
        inventoryRepo.updateCustody(withPartner.id(), ItemCustody.WITH_PARTNER, station.id(), null, null);
        inventoryRepo.updateCustody(lost.id(), ItemCustody.LOST, station.id(), null, null);

        CollectionLine restingLine = collectionRepo.addLine(kit.id(), resting.id(), null, null, 1);
        CollectionLine memberLine = collectionRepo.addLine(kit.id(), withMember.id(), null, null, 1);
        CollectionLine partnerLine = collectionRepo.addLine(kit.id(), withPartner.id(), null, null, 1);
        CollectionLine lostLine = collectionRepo.addLine(kit.id(), lost.id(), null, null, 1);
        CollectionLine clusterLine = collectionRepo.addLine(kit.id(), owned.id(), null, null, 1);
        CollectionLine countLine = collectionRepo.addLine(kit.id(), null, null, drawer.id(), 4);

        List<ResolvedCollectionLine> resolved = collectionRepo.resolve(kit.id(), station.id(), null, null);
        assertEquals(6, resolved.size());
        assertEquals(restingLine.id(), resolved.get(0).lineId());
        assertEquals("Ruht", resolved.get(0).label());
        assertEquals(1, resolved.get(0).available());
        assertTrue(resolved.get(0).filled());

        assertEquals(memberLine.id(), resolved.get(1).lineId());
        assertEquals(1, resolved.get(1).available());

        assertEquals(partnerLine.id(), resolved.get(2).lineId());
        assertEquals(0, resolved.get(2).available());
        assertFalse(resolved.get(2).filled());
        assertEquals(1, resolved.get(2).missing());

        assertEquals(lostLine.id(), resolved.get(3).lineId());
        assertEquals(0, resolved.get(3).available());

        assertEquals(clusterLine.id(), resolved.get(4).lineId());
        assertEquals(1, resolved.get(4).available());
        assertEquals(1, resolved.get(4).clusterOwned());

        assertEquals(countLine.id(), resolved.get(5).lineId());
        assertEquals("Resolve", resolved.get(5).label());
        assertEquals(4, resolved.get(5).requested());
        assertEquals(3, resolved.get(5).available());
        assertEquals(1, resolved.get(5).clusterOwned());
        assertFalse(resolved.get(5).filled());

        List<ResolvedCollectionLine> elsewhere = collectionRepo.resolve(kit.id(), partner.id(), null, null);
        assertTrue(elsewhere.stream().allMatch(line -> line.available() == 0));

        collectionRepo.delete(kit.id(), station.id());
        inventoryRepo.delete(drawer.id());
    }

    /**
     * A line asking for a kind counts the pieces of that kind and nothing else, which is the whole
     * difference between asking for four blue radios and asking for four things out of the drawer.
     */
    @Test
    void aKindLineCountsOnlyThePiecesOfThatKind() {
        Inventory drawer = inventoryRepo.create(station.id(), "Kinds", InventoryType.INTERNAL, false, false);
        InventoryArt blau = artRepo.create(drawer.id(), "Funkgeraet blau", "", 0);
        InventoryArt gruen = artRepo.create(drawer.id(), "Funkgeraet gruen", "", 1);
        InventoryCollection kit = collectionRepo.create(station.id(), "Kinds", "", null);

        InventoryItem blueHere = itemOfKind(drawer, "Blau 1", blau.id());
        itemOfKind(drawer, "Blau 2", blau.id());
        InventoryItem blueLost = itemOfKind(drawer, "Blau 3", blau.id());
        itemOfKind(drawer, "Gruen 1", gruen.id());
        item(drawer, "Koffer ohne Art");
        inventoryRepo.updateCustody(blueLost.id(), ItemCustody.LOST, station.id(), null, null);

        CollectionLine blueLine = collectionRepo.addLine(kit.id(), null, blau.id(), null, 4);
        CollectionLine wholeDrawer = collectionRepo.addLine(kit.id(), null, null, drawer.id(), 4);

        List<ResolvedCollectionLine> resolved = collectionRepo.resolve(kit.id(), station.id(), null, null);
        assertEquals(blueLine.id(), resolved.get(0).lineId());
        assertEquals(blau.id(), resolved.get(0).artId());
        assertEquals("Funkgeraet blau", resolved.get(0).label());
        assertEquals(4, resolved.get(0).requested());
        assertEquals(2, resolved.get(0).available());
        assertEquals(2, resolved.get(0).missing());
        assertFalse(resolved.get(0).filled());

        assertEquals(wholeDrawer.id(), resolved.get(1).lineId());
        assertEquals(4, resolved.get(1).available());
        assertTrue(resolved.get(1).filled());

        assertEquals(
                List.of("Kinds"),
                collectionRepo.findCollectionsAskingForArt(blau.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());
        assertTrue(collectionRepo.findCollectionsAskingForArt(gruen.id()).isEmpty());

        artRepo.delete(blau.id());
        assertTrue(collectionRepo.findLines(kit.id()).stream().noneMatch(line -> line.id() == blueLine.id()));
        assertTrue(inventoryRepo.findItemById(blueHere.id()).isPresent());

        collectionRepo.delete(kit.id(), station.id());
        inventoryRepo.delete(drawer.id());
    }

    @Test
    void aWindowSubtractsWhatIsAlreadyPromised() {
        Inventory drawer = inventoryRepo.create(station.id(), "Fenster", InventoryType.INTERNAL, false);
        InventoryCollection kit = collectionRepo.create(station.id(), "Fenster", "", null);
        InventoryItem promised = item(drawer, "Versprochen");
        InventoryItem free = item(drawer, "Frei");

        collectionRepo.addLine(kit.id(), promised.id(), null, null, 1);
        collectionRepo.addLine(kit.id(), null, null, drawer.id(), 2);

        var request = lendingRepo.createRequest(
                partner.uid(),
                station.uid(),
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 12),
                member.id(),
                null,
                null,
                "");
        var requestItem = lendingRepo.addRequestItem(request.id(), drawer.id(), promised.id(), null, 1, null);
        lendingRepo.assignItem(requestItem.id(), promised.id());
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.APPROVED);

        List<ResolvedCollectionLine> overlapping =
                collectionRepo.resolve(kit.id(), station.id(), LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13));
        assertEquals(0, overlapping.get(0).available());
        assertEquals(1, overlapping.get(1).available());

        List<ResolvedCollectionLine> apart =
                collectionRepo.resolve(kit.id(), station.id(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));
        assertEquals(1, apart.get(0).available());
        assertEquals(2, apart.get(1).available());

        List<ResolvedCollectionLine> undated = collectionRepo.resolve(kit.id(), station.id(), null, null);
        assertEquals(1, undated.get(0).available());
        assertEquals(2, undated.get(1).available());

        lendingRepo.updateRequestStatus(request.id(), LendingStatus.RETURNED);
        List<ResolvedCollectionLine> returned =
                collectionRepo.resolve(kit.id(), station.id(), LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13));
        assertEquals(1, returned.get(0).available());

        assertEquals("Frei", inventoryRepo.findItemById(free.id()).orElseThrow().name());
        collectionRepo.delete(kit.id(), station.id());
        inventoryRepo.delete(drawer.id());
    }

    @Test
    void whatWouldLoseALine() {
        Inventory drawer = inventoryRepo.create(station.id(), "Warnung", InventoryType.INTERNAL, false, false);
        InventoryItem piece = item(drawer, "Laminator");
        InventoryArt kind = artRepo.create(drawer.id(), "Laminiergeraet", "", 0);
        InventoryCollection named = collectionRepo.create(station.id(), "Nennt das Stueck", "", null);
        InventoryCollection counted = collectionRepo.create(station.id(), "Zaehlt das Fach", "", null);
        InventoryCollection perKind = collectionRepo.create(station.id(), "Zaehlt die Art", "", null);
        InventoryCollection untouched = collectionRepo.create(station.id(), "Unbeteiligt", "", null);

        collectionRepo.addLine(named.id(), piece.id(), null, null, 1);
        collectionRepo.addLine(counted.id(), null, null, drawer.id(), 2);
        collectionRepo.addLine(perKind.id(), null, kind.id(), null, 2);
        collectionRepo.addLine(untouched.id(), null, null, games.id(), 1);

        assertEquals(
                List.of("Nennt das Stueck"),
                collectionRepo.findCollectionsHoldingItem(piece.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());
        assertTrue(collectionRepo.findCollectionsHoldingItem(-1).isEmpty());

        assertEquals(
                List.of("Nennt das Stueck", "Zaehlt das Fach", "Zaehlt die Art"),
                collectionRepo.findCollectionsTouchingInventory(drawer.id()).stream()
                        .map(InventoryCollection::name)
                        .toList());

        collectionRepo.delete(named.id(), station.id());
        collectionRepo.delete(counted.id(), station.id());
        collectionRepo.delete(perKind.id(), station.id());
        collectionRepo.delete(untouched.id(), station.id());
        inventoryRepo.delete(drawer.id());
    }
}
