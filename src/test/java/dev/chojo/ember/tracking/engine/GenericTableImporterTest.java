/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic export and import against a real database: an array column travels as the list of its
 * elements and arrives as an array of its element type, an array of ids follows the rows it names, a row
 * waits for the row it names, an optional reference arrives empty only once its row cannot come any more,
 * a partner station is found again by its uid but no other station, an account only where it arrived with
 * the run or belongs to the station, and a row whose required reference cannot be found is left behind
 * instead of failing the import.
 */
class GenericTableImporterTest extends RepositoryTestBase {

    private static GenericTableExporter exporter;
    private static GenericTableImporter importer;

    @BeforeAll
    static void loadEngine() throws IOException {
        var tracking = DataTrackingLoader.loadFromClasspath();
        exporter = new GenericTableExporter(tracking);
        importer = new GenericTableImporter(tracking);
    }

    @Test
    void anArrayColumnIsExportedAsTheListOfItsElements() {
        Station station = stationRepo.create("Array Export");
        int group = memberGroupRepo.create(station.id(), "Atemschutz").id();
        attendanceRepo.createPreset(
                station.id(),
                "Monat",
                List.of(StationUserType.MEMBER, StationUserType.TRIAL),
                List.of(group),
                "month",
                "exact");

        var row =
                exporter.export("attendance_report_preset", station.id(), 0, 10).getFirst();

        assertEquals(List.of("MEMBER", "TRIAL"), row.get("user_types"));
        assertEquals(List.of(group), row.get("group_ids"));
    }

    @Test
    void anArrayOfIdsFollowsTheRowsItNames() {
        Station source = stationRepo.create("Array Source");
        Station destination = stationRepo.create("Array Destination");
        int kept = memberGroupRepo.create(source.id(), "Atemschutz").id();
        int gone = memberGroupRepo.create(source.id(), "Maschinisten").id();
        int arrived = memberGroupRepo.create(destination.id(), "Atemschutz").id();
        attendanceRepo.createPreset(
                source.id(), "Quartal", List.of(StationUserType.TEAM), List.of(kept, gone), "quarter", "hour");
        var idMap = new IdRemapper();
        idMap.put("member_group", kept, arrived);

        var rows = exporter.export("attendance_report_preset", source.id(), 0, 10);
        assertEquals(1, importer.importRows(destination.id(), "attendance_report_preset", rows, idMap));

        var preset = attendanceRepo.findPresets(destination.id()).getFirst();
        assertEquals("Quartal", preset.name());
        assertEquals(List.of(StationUserType.TEAM), preset.userTypes());
        assertEquals(List.of(arrived), preset.groupIds(), "the group that arrived is named by its new id");
    }

    @Test
    void arrayElementsAreBoundAsTheColumnsElementType() {
        String joined = query("SELECT array_to_string(:times, ',') AS joined;")
                .single(ArrayValues.bind(call(), "times", List.of("08:00:00", "17:30"), "_time"))
                .map(row -> row.getString("joined"))
                .first()
                .orElseThrow();

        assertEquals("08:00:00,17:30:00", joined);
    }

    @Test
    void anArrayValueThatIsNoListIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ArrayValues.elements("{a,b}"));
        assertEquals(List.of("a", "b"), ArrayValues.elements(new Object[] {"a", "b"}));
        assertTrue(ArrayValues.isArray("_int4"));
        assertFalse(ArrayValues.isArray("int4"));
        assertFalse(ArrayValues.isArray(null));
    }

    @Test
    void aRowFindsAnAccountThatArrivedWithTheRunByItsAddress() {
        Station destination = stationRepo.create("Filter Destination");
        var account = accountRepo.create("filter-owner@import.test", "Fia", "Filter", true);
        var idMap = new IdRemapper();
        idMap.markArrived("account", account.id());

        int imported = importer.importRows(
                destination.id(), "saved_filter", List.of(filterRow("Aktive", account.email(), account.uid())), idMap);

        assertEquals(1, imported);
        assertEquals(List.of("Aktive"), filterNamesOf(account.id()));
    }

    @Test
    void aRowFindsTheAccountOfAMemberOfTheStationByItsUid() {
        Station destination = stationRepo.create("Filter Member Destination");
        var account = accountRepo.create("filter-member@import.test", "Mia", "Member", true);
        stationMemberRepo.create(destination.id(), account.id());

        int imported = importer.importRows(
                destination.id(),
                "saved_filter",
                List.of(filterRow("Mitglieder", null, account.uid())),
                new IdRemapper());

        assertEquals(1, imported);
        assertEquals(List.of("Mitglieder"), filterNamesOf(account.id()));
    }

    @Test
    void aRowNeverNamesTheAccountOfAStranger() {
        Station destination = stationRepo.create("Filter Stranger Destination");
        Station elsewhere = stationRepo.create("Filter Stranger Elsewhere");
        var stranger = accountRepo.create("filter-stranger@import.test", "Sven", "Stranger", true);
        stationMemberRepo.create(elsewhere.id(), stranger.id());

        int imported = importer.importRows(
                destination.id(),
                "saved_filter",
                List.of(filterRow("Fremd", stranger.email(), stranger.uid())),
                new IdRemapper());

        assertEquals(0, imported, "neither its address nor its uid reaches an account the station does not have");
        assertEquals(List.of(), filterNamesOf(stranger.id()));
    }

    @Test
    void aRowWhoseRequiredAccountIsNowhereIsLeftBehind() {
        Station destination = stationRepo.create("Filter Nowhere");

        int imported = importer.importRows(
                destination.id(),
                "saved_filter",
                List.of(filterRow("Verwaist", "nobody@import.test", UUID.randomUUID())),
                new IdRemapper());

        assertEquals(0, imported, "the filter stays behind rather than failing the import");
    }

    @Test
    void aRowWaitsForTheRowItNames() {
        Station source = stationRepo.create("Waiting Source");
        Station destination = stationRepo.create("Waiting Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        inventoryRepo.createItem(radios, "HRT-9", "Handfunkgerät 9", null, null);
        var idMap = new IdRemapper();
        var waiting = new WaitingRows();

        assertEquals(
                0,
                importer.importRows(
                        destination.id(), "inventory_item", rowsOf("inventory_item", source), idMap, waiting));
        assertEquals(
                1, importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap, waiting));
        assertEquals(1, importer.admitWaiting(destination.id(), idMap, waiting), "the item follows its inventory");

        assertEquals(List.of("HRT-9"), internalIds(destination));
        assertEquals(0, importer.settle(destination.id(), idMap, waiting), "nothing is left waiting");
    }

    @Test
    void anOptionalReferenceArrivesEmptyWhenItsRowNeverComes() {
        Station asking = stationRepo.create("Optional Asking");
        Station lending = stationRepo.create("Optional Lending");
        Station destination = stationRepo.create("Optional Destination");
        int breathing = inventoryRepo
                .create(lending.id(), "Atemschutz", InventoryType.INTERNAL, false)
                .id();
        var requests = new LendingRepository();
        var request = requests.createRequest(
                UUID.randomUUID(),
                asking.uid(),
                lending.uid(),
                LocalDate.now(),
                LocalDate.now(),
                null,
                null,
                null,
                "Übung");
        requests.addRequestItem(request.id(), breathing, null, null, 2, null);
        var idMap = new IdRemapper();
        var waiting = new WaitingRows();

        importer.importRows(destination.id(), "federation_lending_request", requestRowsOf(asking), idMap, waiting);
        assertEquals(
                0,
                importer.importRows(
                        destination.id(),
                        "federation_lending_request_item",
                        rowsOf("federation_lending_request_item", asking),
                        idMap,
                        waiting),
                "the inventory it names may still come");
        assertEquals(1, importer.settle(destination.id(), idMap, waiting));

        var line = requests.findItemsByRequest(idMap.get("federation_lending_request", request.id()))
                .getFirst();
        assertNull(line.inventoryId(), "the lender's inventory stays with the lender");
        assertEquals(2, line.quantity());
    }

    @Test
    void anOptionalReferenceWaitsWhileItsRowCanStillBeWritten() {
        Station source = stationRepo.create("Lent Source");
        Station partner = stationRepo.create("Lent Partner");
        Station destination = stationRepo.create("Lent Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int item = inventoryRepo
                .createItem(radios, "HRT-4", "Handfunkgerät 4", null, null)
                .id();
        itemCustodyService.lendToPartner(item, partner.id());
        var requests = new LendingRepository();
        var request = requests.createRequest(
                UUID.randomUUID(),
                partner.uid(),
                source.uid(),
                LocalDate.now(),
                LocalDate.now(),
                null,
                null,
                null,
                "Übung");
        requests.addRequestItem(request.id(), radios, item, null, 1, null);
        var items = rowsOf("inventory_item", source);
        items.forEach(row -> row.put("custody_partner_station_uid", UUID.randomUUID()));
        var idMap = new IdRemapper();
        idMap.put("station", source.id(), destination.id());
        var waiting = new WaitingRows();

        importer.importRows(destination.id(), "federation_lending_request", requestRowsOf(source), idMap, waiting);
        importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap, waiting);
        importer.importRows(
                destination.id(),
                "federation_lending_request_item",
                rowsOf("federation_lending_request_item", source),
                idMap,
                waiting);
        assertEquals(
                0,
                importer.importRows(destination.id(), "inventory_item", items, idMap, waiting),
                "the partner may still come");
        assertEquals(2, importer.settle(destination.id(), idMap, waiting));

        var arrived = inventoryRepo.findItemsByStation(destination.id()).getFirst();
        assertEquals(ItemCustody.WITH_PARTNER, arrived.custody());
        assertNull(arrived.custodyPartnerStationId(), "a partner nowhere here is lent to on another installation");
        var line = requests.findItemsByRequest(idMap.get("federation_lending_request", request.id()))
                .getFirst();
        assertEquals(arrived.id(), line.itemId(), "the line waited for the piece instead of losing it");
    }

    @Test
    void anOptionalReferenceToARowThatNeverComesIsGivenUpLast() {
        Station source = stationRepo.create("Stuck Source");
        Station partner = stationRepo.create("Stuck Partner");
        Station destination = stationRepo.create("Stuck Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int item = inventoryRepo
                .createItem(radios, "HRT-5", "Handfunkgerät 5", null, null)
                .id();
        var requests = new LendingRepository();
        var request = requests.createRequest(
                UUID.randomUUID(),
                partner.uid(),
                source.uid(),
                LocalDate.now(),
                LocalDate.now(),
                null,
                null,
                null,
                "Übung");
        requests.addRequestItem(request.id(), null, item, null, 1, null);
        var idMap = new IdRemapper();
        var waiting = new WaitingRows();

        importer.importRows(destination.id(), "federation_lending_request", requestRowsOf(source), idMap, waiting);
        importer.importRows(destination.id(), "inventory_item", rowsOf("inventory_item", source), idMap, waiting);
        importer.importRows(
                destination.id(),
                "federation_lending_request_item",
                rowsOf("federation_lending_request_item", source),
                idMap,
                waiting);
        assertEquals(1, importer.settle(destination.id(), idMap, waiting), "the line, not the item without inventory");

        var line = requests.findItemsByRequest(idMap.get("federation_lending_request", request.id()))
                .getFirst();
        assertNull(line.itemId());
        assertEquals(List.of(), internalIds(destination));
    }

    @Test
    void aLentPieceFindsThePartnerHoldingItByItsUid() {
        Station source = stationRepo.create("Uid Source");
        Station partner = stationRepo.create("Uid Partner");
        Station destination = stationRepo.create("Uid Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int item = inventoryRepo
                .createItem(radios, "HRT-6", "Handfunkgerät 6", null, null)
                .id();
        itemCustodyService.lendToPartner(item, partner.id());
        partnership(partner, destination);
        var idMap = new IdRemapper();
        idMap.put("station", source.id(), destination.id());

        importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap);
        var row = rowsOf("inventory_item", source).getFirst();
        assertEquals(partner.uid().toString(), String.valueOf(row.get("custody_partner_station_uid")));
        assertEquals(1, importer.importRows(destination.id(), "inventory_item", List.of(row), idMap));

        var arrived = inventoryRepo.findItemsByStation(destination.id()).getFirst();
        assertEquals(partner.id(), arrived.custodyPartnerStationId());
        assertEquals(destination.id(), arrived.custodyStationId());
    }

    @Test
    void aLentPieceNamesNoStationThatIsNotAPartnerOfTheImportedOne() {
        Station source = stationRepo.create("Uid Stranger Source");
        Station stranger = stationRepo.create("Uid Stranger");
        Station asking = stationRepo.create("Uid Stranger Asking");
        Station destination = stationRepo.create("Uid Stranger Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int item = inventoryRepo
                .createItem(radios, "HRT-7", "Handfunkgerät 7", null, null)
                .id();
        itemCustodyService.lendToPartner(item, stranger.id());
        pendingRequest(asking, destination);
        var idMap = new IdRemapper();
        idMap.put("station", source.id(), destination.id());
        importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap);
        var row = rowsOf("inventory_item", source).getFirst();

        assertEquals(1, importer.importRows(destination.id(), "inventory_item", List.of(row), idMap));
        row.put("custody_partner_station_uid", asking.uid());
        row.put("internal_id", "HRT-7b");
        assertEquals(1, importer.importRows(destination.id(), "inventory_item", List.of(row), idMap));

        assertTrue(
                inventoryRepo.findItemsByStation(destination.id()).stream()
                        .allMatch(arrived -> arrived.custodyPartnerStationId() == null),
                "neither a stranger nor a station that only asked to pair is named");
    }

    /** A partnership the partner suspended is one it stopped trusting, and lets no import name it. */
    @Test
    void aLentPieceNamesNoStationWhosePartnershipIsSuspended() {
        Station source = stationRepo.create("Uid Suspended Source");
        Station partner = stationRepo.create("Uid Suspended Partner");
        Station destination = stationRepo.create("Uid Suspended Destination");
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int item = inventoryRepo
                .createItem(radios, "HRT-8", "Handfunkgerät 8", null, null)
                .id();
        itemCustodyService.lendToPartner(item, partner.id());
        var federation = new FederationRepository();
        federation.updatePartnerStatus(partnership(partner, destination), FederationPartner.FederationStatus.SUSPENDED);
        var idMap = new IdRemapper();
        idMap.put("station", source.id(), destination.id());
        importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap);

        assertEquals(
                1, importer.importRows(destination.id(), "inventory_item", rowsOf("inventory_item", source), idMap));

        assertNull(inventoryRepo.findItemsByStation(destination.id()).getFirst().custodyPartnerStationId());
    }

    @Test
    void aBorrowedPieceFindsItsOwnerHereByItsUid() {
        var borrowing = borrowFromOwner("Here");
        partnership(borrowing.owner(), borrowing.destination());

        assertEquals(1, borrowing.importBorrowed());

        var arrived =
                inventoryRepo.findBorrowedItems(borrowing.destination().id()).getFirst();
        assertEquals("PA-Here", arrived.item().internalId());
        assertEquals(borrowing.owner().id(), arrived.ownerStationId());
        assertEquals(borrowing.owner().uid(), arrived.ownerStationUid());
    }

    @Test
    void aBorrowedPieceWhoseOwnerIsNowhereHereKeepsItsOwnerByUid() {
        var borrowing = borrowFromOwner("Elsewhere");
        UUID elsewhere = UUID.randomUUID();
        borrowing.borrowed().forEach(row -> row.put("owner_station_uid", elsewhere));

        assertEquals(1, borrowing.importBorrowed());

        var arrived =
                inventoryRepo.findBorrowedItems(borrowing.destination().id()).getFirst();
        assertNull(arrived.ownerStationId(), "the owner runs on another installation");
        assertEquals(elsewhere, arrived.ownerStationUid());
    }

    @Test
    void aBorrowedPieceNamesNoOwnerHereThatIsNotAPartner() {
        var borrowing = borrowFromOwner("Stranger");

        assertEquals(1, borrowing.importBorrowed());

        var arrived =
                inventoryRepo.findBorrowedItems(borrowing.destination().id()).getFirst();
        assertNull(arrived.ownerStationId(), "a station that is no partner of the imported one is not reached");
        assertEquals(borrowing.owner().uid(), arrived.ownerStationUid());
    }

    @Test
    void anOwnerThatMovedAwayIsNotFoundHere() {
        var borrowing = borrowFromOwner("Moved");
        partnership(borrowing.owner(), borrowing.destination());
        stationRepo.markMovedAway(borrowing.owner().id(), "https://elsewhere.example");

        assertEquals(1, borrowing.importBorrowed());

        var arrived =
                inventoryRepo.findBorrowedItems(borrowing.destination().id()).getFirst();
        assertNull(arrived.ownerStationId(), "the copy the owner left here is not where it runs");
        assertEquals(borrowing.owner().uid(), arrived.ownerStationUid());
    }

    /**
     * A station that borrowed a piece from an owner on its installation, about to move to a
     * destination: everything but the borrowed copy is already there.
     */
    private Borrowing borrowFromOwner(String name) {
        Station source = stationRepo.create("Borrow Source " + name);
        Station owner = stationRepo.create("Borrow Owner " + name);
        Station destination = stationRepo.create("Borrow Destination " + name);
        int breathing = inventoryRepo
                .create(owner.id(), "Atemschutz", InventoryType.INTERNAL, false)
                .id();
        var piece = inventoryRepo.createItem(breathing, "PA-" + name, "Pressluftatmer " + name, null, null);
        var requests = new LendingRepository();
        var request = requests.createRequest(
                UUID.randomUUID(),
                source.uid(),
                owner.uid(),
                LocalDate.now(),
                LocalDate.now(),
                null,
                null,
                null,
                "Übung");
        int line = requests.addRequestItem(request.id(), breathing, piece.id(), null, 1, null)
                .id();
        borrowedGearService.handOver(piece, owner.id(), owner.uid(), source.id(), line);
        var idMap = new IdRemapper();
        idMap.put("station", source.id(), destination.id());
        var borrowed = rowsOf("inventory_item", source);
        assertEquals(owner.uid().toString(), String.valueOf(borrowed.getFirst().get("owner_station_uid")));

        importer.importRows(destination.id(), "inventory", rowsOf("inventory", source), idMap);
        importer.importRows(destination.id(), "federation_lending_request", requestRowsOf(source), idMap);
        importer.importRows(
                destination.id(),
                "federation_lending_request_item",
                rowsOf("federation_lending_request_item", source),
                idMap);
        return new Borrowing(owner, destination, borrowed, idMap);
    }

    /**
     * A borrowed copy on its way to the destination.
     *
     * @param owner       the station owning the piece
     * @param destination the station the borrower arrives as
     * @param borrowed    the borrower's exported item rows
     * @param idMap       the ids handed out so far
     */
    private record Borrowing(Station owner, Station destination, List<Map<String, Object>> borrowed, IdRemapper idMap) {
        int importBorrowed() {
            return importer.importRows(destination.id(), "inventory_item", borrowed, idMap);
        }
    }

    /**
     * The partner's own, active partnership with the imported station, which lets the import name it.
     *
     * @return the partnership's id
     */
    private static int partnership(Station partner, Station imported) {
        var federation = new FederationRepository();
        var row = federation.createPartner(partner.id(), imported.uid(), null, null, null);
        federation.activatePartner(row.id(), "key");
        return row.id();
    }

    /** A partnership the station asked for that is still pending, which no import may rely on. */
    private static void pendingRequest(Station asking, Station imported) {
        new FederationRepository().createPartner(asking.id(), imported.uid(), null, null, null);
    }

    private static List<String> filterNamesOf(int accountId) {
        return savedFilterRepo.findByAccountAndTable(accountId, FilterTableType.MEMBERS).stream()
                .map(SavedFilter::name)
                .toList();
    }

    private static List<Map<String, Object>> rowsOf(String table, Station station) {
        return exporter.export(table, station.id(), 0, 100);
    }

    /** The station's lending requests, each under a fresh uid, since the source's copy shares the database. */
    private static List<Map<String, Object>> requestRowsOf(Station station) {
        var rows = rowsOf("federation_lending_request", station);
        rows.forEach(row -> row.put("uid", UUID.randomUUID()));
        return rows;
    }

    private static List<String> internalIds(Station station) {
        return inventoryRepo.findItemsByStation(station.id()).stream()
                .map(InventoryItem::internalId)
                .toList();
    }

    private static Map<String, Object> filterRow(String name, @Nullable Object email, UUID uid) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("table_type", FilterTableType.MEMBERS.name());
        row.put("name", name);
        row.put("filter_data", "{}");
        row.put("position", 0);
        row.put("account_email", email);
        row.put("account_uid", uid.toString());
        return row;
    }
}
