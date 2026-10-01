/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seams the inventory rework left open for the cluster, now closed.
 */
class ClusterInventoryServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private Cluster freshCluster() {
        return clusterService.create("Kreisverband Gerät " + NAMES.incrementAndGet(), null);
    }

    private Station stationOf(Cluster cluster) {
        return clusterService.createStation(cluster.id(), "Wache Gerät " + NAMES.incrementAndGet());
    }

    /** A cluster-owned item sitting in an inventory at one of its stations. */
    private int clusterItemAt(Cluster cluster, Station station) {
        var inventory = inventoryRepo.create(
                station.id(), "Einsatzkleidung " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, false);
        return inventoryRepo
                .createItem(
                        inventory.id(),
                        "HK-" + NAMES.incrementAndGet(),
                        "Helm",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id())
                .id();
    }

    @Test
    void aClusterSeesEverythingItOwnsWhereverItIs() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);

        var items = clusterInventoryService.findItems(cluster.id());

        assertEquals(1, items.size());
        assertEquals(itemId, items.getFirst().itemId());
        assertEquals(ItemCustody.AT_STATION, items.getFirst().custody());
        assertEquals(station.name(), items.getFirst().stationName());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    /**
     * A size belongs to the inventory that recorded it, and what an association owns mostly sits in an
     * inventory at one of its stations. Looking the sizes up on the association's own station therefore
     * found none of them and every row was named after a raw id.
     */
    @Test
    void gearAtAMemberStationIsNamedWithTheSizeThatStationRecorded() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var inventory = inventoryRepo.create(
                station.id(), "Einsatzkleidung " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, true);
        inventoryRepo.createSize(inventory.id(), "XXL", 0, null);
        int sizeId = inventoryRepo.findSizes(inventory.id()).getFirst().id();
        inventoryRepo.createItem(
                inventory.id(),
                "HK-" + NAMES.incrementAndGet(),
                "Jacke",
                sizeId,
                null,
                ItemOwner.CLUSTER,
                cluster.id());

        var items = clusterInventoryService.findItems(cluster.id());

        assertEquals(1, items.size());
        assertEquals("XXL", items.getFirst().sizeLabel());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    /**
     * What the statistics page reads: one block per kind of thing, the sizes inside the block they
     * belong to, and nothing at all from gear the association does not own.
     */
    @Test
    void whatTheAssociationOwnsIsCountedByKindAndBySize() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var jackets =
                inventoryRepo.create(station.id(), "Jacken " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, true);
        inventoryRepo.createSize(jackets.id(), "52", 0, null);
        int sizeId = inventoryRepo.findSizes(jackets.id()).getFirst().id();
        inventoryRepo.createItem(
                jackets.id(), "J-" + NAMES.incrementAndGet(), "Jacke", sizeId, null, ItemOwner.CLUSTER, cluster.id());
        inventoryRepo.createItem(
                jackets.id(), "J-" + NAMES.incrementAndGet(), "Jacke", sizeId, null, ItemOwner.CLUSTER, cluster.id());
        var helmets =
                inventoryRepo.create(station.id(), "Helme " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, false);
        inventoryRepo.createItem(
                helmets.id(), "H-" + NAMES.incrementAndGet(), "Helm", null, null, ItemOwner.CLUSTER, cluster.id());
        inventoryRepo.createItem(
                helmets.id(), "H-" + NAMES.incrementAndGet(), "Helm", null, null, ItemOwner.STATION, null);

        var stats = clusterInventoryService.statistics(cluster.id());

        assertEquals(2, stats.size(), "one block per kind of thing, and the station's own helmet in neither");
        var jacketStat = stats.stream()
                .filter(stat -> stat.inventoryId() == jackets.id())
                .findFirst()
                .orElseThrow();
        assertEquals(2, jacketStat.total());
        assertEquals(2, jacketStat.atStation(), "gear recorded at a station is held there");
        assertEquals(1, jacketStat.sizes().size());
        assertEquals("52", jacketStat.sizes().getFirst().label());
        assertEquals(2, jacketStat.sizes().getFirst().total());
        var helmetStat = stats.stream()
                .filter(stat -> stat.inventoryId() == helmets.id())
                .findFirst()
                .orElseThrow();
        assertEquals(1, helmetStat.total(), "only the one the association owns");
        assertTrue(helmetStat.sizes().isEmpty(), "an inventory that keeps no sizes has no size rows");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    /**
     * The ready-made chains arrive the first time the settings screen asks for them, and asking again
     * does not write a second set.
     */
    @Test
    void theSettingsScreenFindsTheReadyMadeChains() {
        var cluster = freshCluster();

        assertTrue(clusterInventoryService.findFlows(cluster.id()).isEmpty(), "reading alone writes nothing");

        var first = clusterInventoryService.findFlowsForSettings(cluster.id());
        var second = clusterInventoryService.findFlowsForSettings(cluster.id());

        assertEquals(4, first.size(), "one chain per purpose");
        assertEquals(first.size(), second.size(), "asking twice does not write them twice");
        assertTrue(
                first.stream().anyMatch(flow -> flow.purpose() == MovementPurpose.REQUEST),
                "a station asking for something is one of them");
    }

    @Test
    void aStationCannotRenameOrDeleteGearItDoesNotOwn() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);

        var described = assertThrows(
                RefusalResponse.class,
                () -> inventoryService.updateItem(itemId, "HK-neu", "Anderer Helm", null, null, null));
        assertEquals(InventoryRefusal.INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE, described.refusal());
        assertThrows(RefusalResponse.class, () -> inventoryService.deleteItem(itemId, null));

        var own = inventoryRepo.create(station.id(), "Eigenes", InventoryType.INTERNAL, false);
        var ownItem = inventoryRepo.createItem(own.id(), "EG-1", "Eigener Helm", null, null);
        assertTrue(
                inventoryService
                        .updateItem(ownItem.id(), "EG-1", "Umbenannt", null, null, null)
                        .isPresent(),
                "what the station owns it may still change");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aClusterMayRenameAndDeleteItsOwnGear() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);

        var renamed = inventoryService.updateItem(itemId, "HK-neu", "Anderer Helm", null, null, cluster.id());
        assertTrue(renamed.isPresent());
        assertEquals("Anderer Helm", renamed.get().name());

        var other = freshCluster();
        assertThrows(
                RefusalResponse.class,
                () -> inventoryService.updateItem(itemId, "HK-fremd", "Fremder Helm", null, null, other.id()),
                "another association is still a stranger to it");

        assertTrue(inventoryService.deleteItem(itemId, cluster.id()));

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    /**
     * A chain that turned out wrong used to be permanent: the oldest unarchived one for a purpose wins
     * silently, creating was the only act available, and it was the one act that could not help.
     */
    @Test
    void aChainCanBeReadCorrectedAndRetired() {
        var cluster = freshCluster();
        var flow = clusterInventoryService.createFlow(cluster.id(), "Ausgabe", MovementPurpose.ISSUE);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> clusterInventoryService.createFlow(cluster.id(), "Ausgabe neu", MovementPurpose.ISSUE));
        assertEquals(ClusterRefusal.CLUSTER_INVENTORY_FLOW_PURPOSE_TAKEN, refused.refusal());
        assertTrue(refused.getMessage().contains("Ausgabe"), "and it says which one is in the way");

        var step = clusterInventoryService.addStep(
                cluster.id(),
                flow.id(),
                "Verband gibt aus",
                StepActor.OWNER,
                StepSubject.OUTGOING,
                ItemCustody.IN_TRANSIT,
                false);
        assertEquals(
                1, clusterInventoryService.findSteps(cluster.id(), flow.id()).size());

        clusterInventoryService.updateStep(
                cluster.id(),
                step.id(),
                "Verband schickt los",
                StepActor.OWNER,
                StepSubject.OUTGOING,
                ItemCustody.IN_TRANSIT,
                false);
        assertEquals(
                "Verband schickt los",
                clusterInventoryService
                        .findSteps(cluster.id(), flow.id())
                        .getFirst()
                        .label());

        clusterInventoryService.renameFlow(cluster.id(), flow.id(), "Ausgabe an die Wachen");
        clusterInventoryService.archiveStep(cluster.id(), step.id());
        clusterInventoryService.archiveFlow(cluster.id(), flow.id());

        assertTrue(clusterInventoryService.findFlows(cluster.id()).isEmpty(), "a retired chain leaves the list");
        clusterInventoryService.createFlow(cluster.id(), "Ausgabe neu", MovementPurpose.ISSUE);

        clusterService.delete(cluster.id());
    }

    /** Another association's chain is not this one's to rename, retire or add a step to. */
    @Test
    void oneAssociationCannotChangeAnothersChain() {
        var cluster = freshCluster();
        var other = freshCluster();
        var flow = clusterInventoryService.createFlow(cluster.id(), "Ausgabe", MovementPurpose.ISSUE);

        var renamed = assertThrows(
                RefusalResponse.class, () -> clusterInventoryService.renameFlow(other.id(), flow.id(), "Fremd"));
        assertEquals(ClusterRefusal.CLUSTER_INVENTORY_FLOW_NOT_HERE, renamed.refusal());
        assertThrows(RefusalResponse.class, () -> clusterInventoryService.archiveFlow(other.id(), flow.id()));
        assertThrows(
                RefusalResponse.class,
                () -> clusterInventoryService.addStep(
                        other.id(),
                        flow.id(),
                        "Fremd",
                        StepActor.OWNER,
                        StepSubject.OUTGOING,
                        ItemCustody.IN_TRANSIT,
                        false));
        assertThrows(RefusalResponse.class, () -> clusterInventoryService.findSteps(other.id(), flow.id()));

        clusterService.delete(other.id());
        clusterService.delete(cluster.id());
    }

    /**
     * The association's gear walks the association's chains, wherever it is and whoever raised the
     * movement. Until the gear said which body owned it, nothing ever reached this: a station
     * recording a piece as somebody else's could not say whose, so every such piece looked like one
     * belonging to a body outside Ember and the association's settings screen governed nothing.
     */
    @Test
    void theAssociationsChainsGovernItsOwnGear() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var issueFlow = clusterInventoryService.createFlow(cluster.id(), "Verbandsausgabe", MovementPurpose.ISSUE);
        var exchangeFlow = clusterInventoryService.createFlow(cluster.id(), "Verbandstausch", MovementPurpose.EXCHANGE);

        assertNotEquals(
                issueFlow.id(),
                movementFlowService.resolveFlow(
                        station.id(),
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id(),
                        MovementPurpose.ISSUE,
                        MovementParty.STORE),
                "an owner that cannot answer sets no terms");

        clusterInventoryService.setUsesInventory(cluster.id(), true);
        assertEquals(
                issueFlow.id(),
                movementFlowService.resolveFlow(
                        station.id(),
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id(),
                        MovementPurpose.ISSUE,
                        MovementParty.STORE),
                "what the association sends out walks the chain the association wrote");
        assertEquals(
                exchangeFlow.id(),
                movementFlowService.resolveFlow(
                        station.id(),
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id(),
                        MovementPurpose.EXCHANGE,
                        MovementParty.MEMBER),
                "and so does an exchange of its gear, whoever raised it");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void gearWithNoClusterBehindItFallsThroughToTheStation() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        clusterInventoryService.createFlow(cluster.id(), "Verbandstausch", MovementPurpose.EXCHANGE);
        clusterInventoryService.setUsesInventory(cluster.id(), true);

        int flow = movementFlowService.resolveFlow(
                station.id(), null, ItemOwner.STATION, cluster.id(), MovementPurpose.EXCHANGE, MovementParty.MEMBER);
        assertNotEquals(
                clusterInventoryService.findFlows(cluster.id()).getFirst().id(),
                flow,
                "station-owned gear is the station's business whatever the cluster keeps");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void theQueueHoldsWhatIsStandingOnAStepOnlyTheClusterCanAnswer() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);
        int memberId = memberAt(station);
        assertTrue(clusterInventoryService.findQueue(cluster.id()).isEmpty(), "nothing waits before anything starts");

        var flow = clusterInventoryService.createFlow(cluster.id(), "Rückgabe", MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Wache schickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(), "Verband nimmt an", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        clusterInventoryService.setUsesInventory(cluster.id(), true);

        itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                null,
                itemId,
                inventoryRepo.findItemById(itemId).orElseThrow().inventoryId(),
                null,
                null,
                "Zurück damit",
                new ItemMovementService.Actor(memberId, true),
                null);

        var queue = clusterInventoryService.findQueue(cluster.id());
        assertEquals(1, queue.size(), "the movement stopped on the cluster's own step");
        assertEquals("Verband nimmt an", queue.getFirst().stepLabel());
        assertEquals(station.name(), queue.getFirst().stationName());
        assertEquals("Helm", queue.getFirst().itemName());

        assertTrue(
                clusterInventoryService.findQueue(freshCluster().id()).isEmpty(), "another cluster sees nothing of it");
    }

    @Test
    void gearAStationAlreadyKeptForTheBodyAboveItFindsItsOwnerOnJoining() {
        int n = NAMES.incrementAndGet();
        var standalone = stationRepo.create("Wache ohne Verband " + n);
        var inventory = inventoryRepo.create(standalone.id(), "Einsatzkleidung " + n, InventoryType.EXTERNAL, false);
        int adopted = inventoryRepo
                .createItem(inventory.id(), "ADOPT-" + n, "Helm", null, null, ItemOwner.CLUSTER, null)
                .id();
        int itsOwn = inventoryRepo
                .createItem(inventory.id(), "OWN-" + n, "Funkgerät", null, null, ItemOwner.STATION, null)
                .id();

        var cluster = freshCluster();
        clusterService.joinStation(cluster.id(), standalone.id());

        assertEquals(
                cluster.id(),
                inventoryRepo.findItemById(adopted).orElseThrow().ownerClusterId(),
                "The body the gear already belonged to can now be pointed at");
        assertNull(
                inventoryRepo.findItemById(itsOwn).orElseThrow().ownerClusterId(),
                "The station's own gear is nobody else's");
        assertEquals(
                ItemOwner.STATION,
                inventoryRepo.findItemById(itsOwn).orElseThrow().ownerKind(),
                "Joining a cluster does not hand it anything");
    }

    @Test
    void gearCannotBeRecordedAsBelongingToSomebodyElsesAssociation() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var stranger = freshCluster();
        int n = NAMES.incrementAndGet();
        var inventory = inventoryRepo.create(station.id(), "Einsatzkleidung " + n, InventoryType.EXTERNAL, false);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> inventoryService.createItem(
                        inventory.id(), "STRANGE-" + n, "Helm", null, null, ItemOwner.CLUSTER, stranger.id()),
                "A station answers to one body, so naming another one is a mistake rather than a choice");
        assertEquals(InventoryRefusal.INVENTORY_GEAR_OF_ANOTHER_ASSOCIATION, refused.refusal());

        assertNotNull(
                inventoryService.createItem(
                        inventory.id(), "MINE-" + n, "Helm", null, null, ItemOwner.CLUSTER, cluster.id()),
                "its own body is fine");
        assertNotNull(
                inventoryService.createItem(inventory.id(), "OFF-" + n, "Helm", null, null, ItemOwner.CLUSTER, null),
                "and so is an owner that does not run here at all");
    }

    /** A member at the station, so a movement has somebody to have been started by. */
    private int memberAt(Station station) {
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clustergear" + n + "@test.com", "Ger", "Aet" + n);
        return stationMemberRepo.create(station.id(), account.id()).id();
    }
}
