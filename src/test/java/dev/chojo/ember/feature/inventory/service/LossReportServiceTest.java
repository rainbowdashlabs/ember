/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reporting a loss to the body above the station: what it needs before it will look at one, and what walks
 * once it has.
 */
class LossReportServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private Cluster freshCluster() {
        var cluster = clusterService.create("Kreisverband Verlust " + NAMES.incrementAndGet(), null);
        clusterInventoryService.setUsesInventory(cluster.id(), true);
        return cluster;
    }

    private Station stationOf(Cluster cluster) {
        return clusterService.createStation(cluster.id(), "Wache Verlust " + NAMES.incrementAndGet());
    }

    private int memberAt(Station station) {
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("verlust" + n + "@test.com", "Ver", "Lust" + n);
        return stationMemberRepo.create(station.id(), account.id()).id();
    }

    /**
     * The chain the demo gives an association: the member announces, the station takes back and posts, the
     * association receives, sends a replacement and the station hands it over.
     */
    private void exchangeFlow(Cluster cluster) {
        var flow = clusterInventoryService.createFlow(cluster.id(), "Tausch", MovementPurpose.EXCHANGE);
        movementFlowService.addStep(
                flow.id(),
                "Mitglied meldet an",
                StepActor.MEMBER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_MEMBER,
                false);
        movementFlowService.addStep(
                flow.id(),
                "Wache nimmt zurück",
                StepActor.STATION,
                StepSubject.OUTGOING,
                ItemCustody.AT_STATION,
                false);
        movementFlowService.addStep(
                flow.id(), "Wache schickt weg", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(), "Verband nimmt an", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        movementFlowService.addStep(
                flow.id(),
                "Verband schickt Ersatz",
                StepActor.OWNER,
                StepSubject.INCOMING,
                ItemCustody.IN_TRANSIT,
                true);
        movementFlowService.addStep(
                flow.id(), "Wache gibt aus", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_MEMBER, false);
    }

    /** A piece of the association's gear, in the hands of a member of one of its stations, and missing. */
    private int lostItemAt(Cluster cluster, Station station, int memberId) {
        var inventory = inventoryRepo.create(
                station.id(), "Einsatzkleidung " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, false);
        int itemId = inventoryRepo
                .createItem(
                        inventory.id(),
                        "VL-" + NAMES.incrementAndGet(),
                        "Jacke",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id())
                .id();
        itemCustodyService.assignToMember(itemId, memberId, "Ver Lust");
        itemCustodyService.markLost(itemId, "Beim Einsatz liegen geblieben", memberId);
        return itemId;
    }

    @Test
    void aReportWalksTheReplacementLegAndNeverTheReturnLeg() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        exchangeFlow(cluster);
        int memberId = memberAt(station);
        int itemId = lostItemAt(cluster, station, memberId);

        var movement = lossReportService.report(station.id(), itemId, "Wir brauchen Ersatz", null, memberId);

        assertTrue(movement.lostReport());
        assertEquals(MovementState.OPEN, movement.state());
        assertEquals(itemId, movement.outgoingItemId());

        // The announcement was the report itself; what waits is the association's own step
        var steps = itemMovementService.stepsOf(movement);
        assertEquals(3, steps.size(), "the return leg is not walked, because there is nothing to walk back");
        assertEquals("Mitglied meldet an", steps.get(0).label());
        assertEquals("Verband schickt Ersatz", steps.get(1).label());
        assertEquals("Wache gibt aus", steps.get(2).label());
        assertEquals(steps.get(1).id(), movement.currentStepId());

        // The gear is still missing. Reporting it says nothing about where it is.
        var item = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(ItemCustody.LOST, item.custody());
        assertEquals("Beim Einsatz liegen geblieben", item.lostNote(), "the member's note is not overwritten");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aRefusedReplacementLeavesTheLossStanding() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        exchangeFlow(cluster);
        int memberId = memberAt(station);
        int itemId = lostItemAt(cluster, station, memberId);

        var movement = lossReportService.report(station.id(), itemId, "Wir brauchen Ersatz", null, memberId);
        var closed = itemMovementService.decline(
                movement.id(), new ItemMovementService.Actor(0, false, true), "Kein Ersatz vorhanden");

        assertEquals(MovementState.DECLINED, closed.state());
        assertEquals("Kein Ersatz vorhanden", closed.closeReason());
        assertEquals(
                ItemCustody.LOST,
                inventoryRepo.findItemById(itemId).orElseThrow().custody(),
                "a refusal does not find the jacket");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void anAssociationSaysWhatAReportHasToCarry() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        exchangeFlow(cluster);
        int memberId = memberAt(station);
        int itemId = lostItemAt(cluster, station, memberId);

        clusterInventoryService.setLossReportRequires(cluster.id(), LossReportRequirement.DOCUMENT);
        assertThrows(
                BadRequestResponse.class,
                () -> lossReportService.report(station.id(), itemId, null, null, memberId),
                "a note is short of what was asked for");
        assertThrows(
                BadRequestResponse.class,
                () -> lossReportService.report(station.id(), itemId, "Weg", null, memberId),
                "and so is a note without the document");

        var document = new LossReportService.Attachment("verlust.txt", "text/plain", "Verlustmeldung".getBytes());
        var movement = lossReportService.report(station.id(), itemId, "Weg", document, memberId);

        var attached = lossReportService.documentOf(movement.id()).orElseThrow();
        assertEquals("verlust.txt", attached.fileName());
        assertEquals(
                "Verlustmeldung",
                new String(lossReportService.read(station.id(), attached).orElseThrow()));

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void thereIsNothingToReportWhenTheGearIsNotMissingOrNobodyOwnsIt() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        exchangeFlow(cluster);
        int memberId = memberAt(station);

        var inventory =
                inventoryRepo.create(station.id(), "Eigenes " + NAMES.incrementAndGet(), InventoryType.MIXED, false);
        int held = inventoryRepo
                .createItem(
                        inventory.id(),
                        "VH-" + NAMES.incrementAndGet(),
                        "Helm",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id())
                .id();
        assertThrows(
                BadRequestResponse.class,
                () -> lossReportService.report(station.id(), held, "Weg", null, memberId),
                "gear nobody has reported missing is not a loss");

        int own = inventoryRepo
                .createItem(
                        inventory.id(), "VO-" + NAMES.incrementAndGet(), "Helm", null, null, ItemOwner.STATION, null)
                .id();
        itemCustodyService.markLost(own, null, null);
        assertThrows(
                BadRequestResponse.class,
                () -> lossReportService.report(station.id(), own, "Weg", null, memberId),
                "the station's own loss has nobody to report it to");
        assertFalse(lossReportService.requirementFor(own).isPresent());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }
}
