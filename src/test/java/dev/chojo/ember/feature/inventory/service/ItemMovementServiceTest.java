/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ItemMovementServiceTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int mixedInventoryId;
    private static ItemMovementService.Actor team;
    private static ItemMovementService.Actor kid;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("MovementStation");
        account = accountRepo.create("movement@test.com", "Move", "Ment");
        member = stationMemberRepo.create(station.id(), account.id());
        mixedInventoryId = inventoryRepo
                .create(station.id(), "Handschuhe", InventoryType.MIXED, false)
                .id();
        team = new ItemMovementService.Actor(member.id(), true);
        kid = new ItemMovementService.Actor(member.id(), false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int item(ItemOwner owner) {
        return inventoryRepo
                .createItem(mixedInventoryId, "M-" + CODES.incrementAndGet(), "Glove", null, null, owner, null)
                .id();
    }

    private int itemWithMember(ItemOwner owner) {
        int id = item(owner);
        itemCustodyService.assignToMember(id, member.id(), "Move Ment");
        return id;
    }

    private ItemCustody custodyOf(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow().custody();
    }

    private ItemMovement announceExchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Move Ment",
                itemId,
                mixedInventoryId,
                null,
                null,
                "Zu klein",
                team,
                null);
    }

    /** The step a movement is standing on. */
    private MovementFlowStep stepStoodOn(ItemMovement movement) {
        int stepId = movement.currentStepId();
        return itemMovementService.stepsOf(movement).stream()
                .filter(step -> step.id() == stepId)
                .findFirst()
                .orElseThrow();
    }

    /** Whether the step a movement is standing on is the one that says which piece arrived. */
    private boolean namesTheArrival(ItemMovement movement) {
        int stepId = movement.currentStepId();
        return itemMovementService.stepsOf(movement).stream()
                .filter(step -> step.id() == stepId)
                .anyMatch(MovementFlowStep::picksItem);
    }

    /** Acknowledges whatever step the movement stands on until it closes or runs out of patience. */
    private ItemMovement walkToEnd(ItemMovement movement, Integer replacementId) {
        int guard = 10;
        while (guard-- > 0 && movement.state() == MovementState.OPEN && movement.currentStepId() != null) {
            movement =
                    itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", replacementId);
        }
        return movement;
    }

    @Test
    void stationOwnedGearWalksFourStepsAndNeverLeavesTheStation() {
        int old = itemWithMember(ItemOwner.STATION);
        int replacement = item(ItemOwner.STATION);

        ItemMovement movement = announceExchange(old);
        assertEquals(
                4,
                itemMovementService.stepsOf(movement).size(),
                "no owner leg for the station's own gear, and the member confirms they have the replacement");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(old), "announcing changes nothing about who has it");

        movement = walkToEnd(movement, replacement);

        assertEquals(MovementState.DONE, movement.state());
        assertNull(movement.currentStepId());
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(old), "the old one is back in the station's own store");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(replacement));
        assertEquals(replacement, movement.incomingItemId());
    }

    @Test
    void ownerOwnedGearWalksTheWholeChainIncludingThePost() {
        int old = itemWithMember(ItemOwner.CLUSTER);
        int replacement = item(ItemOwner.CLUSTER);

        ItemMovement movement = announceExchange(old);
        assertEquals(
                8,
                itemMovementService.stepsOf(movement).size(),
                "the owner's two steps are in the chain, walked by the station where the owner is not here");

        // Step 2 takes it back to the station, which does not own it
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        assertEquals(ItemCustody.AT_STATION, custodyOf(old));

        // Step 3 puts it in the post, where it is in neither store's free stock
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(old));
        assertEquals(
                movement.id(), inventoryRepo.findItemById(old).orElseThrow().custodyMovementId());
        assertFalse(inventoryRepo.findUnassignedItems(mixedInventoryId).stream().anyMatch(i -> i.id() == old));

        // It still belongs to the station's list, because the station is one end of the movement
        assertTrue(inventoryRepo.findItemsByStation(station.id()).stream().anyMatch(i -> i.id() == old));

        movement = walkToEnd(movement, replacement);
        assertEquals(MovementState.DONE, movement.state());
        assertTrue(
                inventoryRepo.findItemById(old).isEmpty(),
                "posted to a body Ember cannot see, so the row goes with it rather than sitting there forever");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(replacement));
    }

    /**
     * The chain names the owner's steps, and a station with nobody above it on Ember walks them in its
     * place. What it saw itself reads as confirmed, what it walked for somebody else as asserted, and
     * the difference survives being read later.
     */
    @Test
    void whatTheStationSawItselfIsToldApartFromWhatItWalkedForTheOwner() {
        int old = itemWithMember(ItemOwner.CLUSTER);
        ItemMovement movement = walkToEnd(announceExchange(old), item(ItemOwner.CLUSTER));

        var entries = itemMovementService.findLogs(movement.id());
        var steps = itemMovementService.stepsOf(movement);
        assertTrue(
                steps.stream().anyMatch(step -> step.actor() == StepActor.OWNER),
                "the owner's part of the journey is in the chain");
        for (int i = 0; i < steps.size(); i++) {
            AckKind expected = steps.get(i).actor() == StepActor.OWNER ? AckKind.ASSERTED : AckKind.CONFIRMED;
            assertEquals(
                    expected, entries.get(i).ackKind(), "step " + steps.get(i).label());
            assertEquals(steps.get(i).label(), entries.get(i).stepLabel());
        }
    }

    /**
     * A station that does want the owner's leg on the record adds those steps to its own flow. They
     * read as asserted, because the station is standing in for somebody who cannot answer.
     */
    @Test
    void aStationThatAddsTheOwnersLegAnywayHasItRecordedAsStandingIn() {
        var flow = movementFlowService.createFlow(station.id(), "Mit Trägerbein", MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Verschickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(),
                "Beim Träger angekommen",
                StepActor.OWNER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_OWNER,
                false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.CLUSTER, MovementPurpose.RETURN, MovementParty.STORE, flow.id());

        int gear = item(ItemOwner.CLUSTER);
        ItemMovement movement = walkToEnd(
                itemMovementService.create(
                        station.id(),
                        MovementPurpose.RETURN,
                        null,
                        null,
                        gear,
                        mixedInventoryId,
                        null,
                        null,
                        "Nicht mehr gebraucht",
                        team,
                        null),
                null);

        var entries = itemMovementService.findLogs(movement.id());
        assertEquals(AckKind.CONFIRMED, entries.get(0).ackKind());
        assertEquals(AckKind.ASSERTED, entries.get(1).ackKind(), "the station cannot confirm what it did not see");
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(gear));
    }

    @Test
    void namingABodyAboveTheStationDoesNotMakeItsStepsItsOwnAnswer() {
        // The owning body is named, but nobody from its side can press anything, so the station is
        // still standing in and the record has to say so
        var flow = movementFlowService.createFlow(station.id(), "Benanntes Trägerbein", MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(),
                "Vom Träger verschickt",
                StepActor.OWNER,
                StepSubject.INCOMING,
                ItemCustody.IN_TRANSIT,
                true);
        movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.CLUSTER, MovementPurpose.ISSUE, MovementParty.STORE, flow.id());

        // A real cluster, because the item's owning cluster is a foreign key now
        var home = stationRepo.create("Träger " + CODES.incrementAndGet());
        int clusterId = clusterRepo.create("Kreisverband", null, home.id()).id();
        int itemId = inventoryRepo
                .createItem(
                        mixedInventoryId,
                        "M-" + CODES.incrementAndGet(),
                        "Glove",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        clusterId)
                .id();
        assertEquals(clusterId, inventoryRepo.findItemById(itemId).orElseThrow().ownerClusterId());

        ItemMovement movement = itemMovementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                null,
                null,
                null,
                mixedInventoryId,
                null,
                null,
                "Nachschub",
                team,
                itemId);

        assertEquals(
                AckKind.ASSERTED,
                itemMovementService.findLogs(movement.id()).getFirst().ackKind(),
                "naming the body above does not make the station's click its answer");
    }

    /**
     * An issue that already names what is being sent is what a cluster starting one looks like, and the
     * station is told what is coming by name rather than by a step it has to go and read.
     */
    @Test
    void anIssueOfClusterGearNamesWhatIsOnItsWay() {
        var flow = movementFlowService.createFlow(station.id(), "Träger schickt", MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(), "Träger verschickt", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(), "Wache nimmt an", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.CLUSTER, MovementPurpose.ISSUE, MovementParty.STORE, flow.id());

        var home = stationRepo.create("Träger " + CODES.incrementAndGet());
        int clusterId = clusterRepo.create("Kreisverband", null, home.id()).id();
        int gear = inventoryRepo
                .createItem(
                        mixedInventoryId,
                        "M-" + CODES.incrementAndGet(),
                        "Jacke",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        clusterId)
                .id();

        // Started by somebody answering for the cluster, because the first step is the cluster's: gear
        // it has not sent yet is not something the station can say has been sent
        var cluster = new ItemMovementService.Actor(team.memberId(), true, true);
        ItemMovement movement = itemMovementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                null,
                null,
                gear,
                mixedInventoryId,
                null,
                null,
                "Nachschub",
                cluster,
                null);

        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(gear), "it is on its way, at neither end");

        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);

        assertEquals(MovementState.DONE, movement.state());
        assertEquals(ItemCustody.AT_STATION, custodyOf(gear), "and it has arrived");
    }

    /**
     * A cluster on this instance answers for itself, so the station waiting is the point of the step
     * rather than an obstacle to work around. The station standing in is for an owner that cannot answer
     * at all, which is the case just above this one.
     */
    @Test
    void aStationCannotAnswerForAClusterThatCanAnswerForItself() {
        var flow = movementFlowService.createFlow(station.id(), "Trägerbein mit Träger", MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Wache schickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(), "Träger nimmt an", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.CLUSTER, MovementPurpose.RETURN, MovementParty.STORE, flow.id());

        var home = stationRepo.create("Träger Antwort " + CODES.incrementAndGet());
        int clusterId =
                clusterRepo.create("Kreisverband Antwort", null, home.id()).id();
        clusterRepo.setUsesInventory(clusterId, true);
        int gear = inventoryRepo
                .createItem(
                        mixedInventoryId,
                        "M-" + CODES.incrementAndGet(),
                        "Jacke",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        clusterId)
                .id();

        ItemMovement movement = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                null,
                gear,
                mixedInventoryId,
                null,
                null,
                "Zurück",
                team,
                null);

        int ownerStep = movement.currentStepId();
        assertThrows(
                ForbiddenResponse.class,
                () -> itemMovementService.acknowledge(movement.id(), ownerStep, team, "", null),
                "the station cannot say the cluster has taken it");

        var forTheCluster = new ItemMovementService.Actor(team.memberId(), false, true);
        ItemMovement done = itemMovementService.acknowledge(movement.id(), ownerStep, forTheCluster, "", null);

        assertEquals(MovementState.DONE, done.state());
        assertEquals(
                AckKind.CONFIRMED,
                itemMovementService.findLogs(done.id()).getLast().ackKind(),
                "the owner answered for itself");
    }

    @Test
    void anOwnerAnsweringForItselfIsRecordedAsConfirming() {
        // Nobody carries owner rights today. The day the body above the station has people who can
        // press its own steps, they arrive here as this actor and the same chain reads as confirmed
        var owner = new ItemMovementService.Actor(member.id(), true, true);
        int old = itemWithMember(ItemOwner.CLUSTER);

        ItemMovement movement = announceExchange(old);
        int guard = 10;
        while (guard-- > 0 && movement.state() == MovementState.OPEN && movement.currentStepId() != null) {
            movement = itemMovementService.acknowledge(
                    movement.id(), movement.currentStepId(), owner, "", item(ItemOwner.CLUSTER));
        }

        var entries = itemMovementService.findLogs(movement.id());
        assertTrue(entries.stream().allMatch(e -> e.ackKind() == AckKind.CONFIRMED));
    }

    @Test
    void aDeclinedMovementPutsTheItemBackWithTheMember() {
        int old = itemWithMember(ItemOwner.CLUSTER);
        ItemMovement movement = announceExchange(old);
        // Take it back and put it in the post, so it is well away from the member
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(old));

        movement = itemMovementService.decline(movement.id(), team, "Kein Ersatz auf Lager");

        assertEquals(MovementState.DECLINED, movement.state());
        assertNull(movement.currentStepId());
        assertEquals("Kein Ersatz auf Lager", movement.closeReason());
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(old), "the member has their own item again");
    }

    @Test
    void aMemberKeepsSeeingTheirGearWhileItIsBeingExchanged() {
        int old = itemWithMember(ItemOwner.CLUSTER);
        int replacement = item(ItemOwner.CLUSTER);
        ItemMovement movement = announceExchange(old);

        // Taken back, then put in the post: the member holds nothing at all any more
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        assertFalse(
                inventoryRepo.findItemsByMember(member.id()).stream().anyMatch(i -> i.id() == old),
                "it is nobody's while it is in the post");
        assertEquals(ItemCustody.IN_TRANSIT, custodyOf(old));

        // Their own list still shows it, saying which step it is standing on
        var entry = inventoryRepo.findMemberEntries(member.id()).stream()
                .filter(e -> e.item().id() == old)
                .findFirst()
                .orElseThrow(() -> new AssertionError("gear on the way should stay on the member's list"));
        assertEquals(movement.id(), entry.movementId());
        assertFalse(entry.movementIncoming());
        assertNotNull(entry.movementStep());
        assertEquals(ItemCustody.IN_TRANSIT, entry.item().custody());

        while (!namesTheArrival(movement)) {
            movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", null);
        }
        movement = itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", replacement);
        var incoming = inventoryRepo.findMemberEntries(member.id()).stream()
                .filter(e -> e.item().id() == replacement)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the replacement should be visible on its way"));
        assertTrue(incoming.movementIncoming());

        walkToEnd(movement, replacement);
    }

    @Test
    void aReturnHandsGearBackWithNothingComingTheOtherWay() {
        int gear = item(ItemOwner.CLUSTER);
        assertEquals(ItemCustody.AT_STATION, custodyOf(gear));

        ItemMovement movement = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                null,
                gear,
                mixedInventoryId,
                null,
                null,
                "Nicht mehr gebraucht",
                team,
                null);
        assertEquals(2, itemMovementService.stepsOf(movement).size(), "announced, then posted; nothing invented");

        movement = walkToEnd(movement, null);

        assertEquals(MovementState.DONE, movement.state());
        assertNull(movement.incomingItemId(), "nothing arrives to replace it");
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(gear));
        assertFalse(
                inventoryRepo.findItemsByStation(station.id()).stream().anyMatch(i -> i.id() == gear),
                "gear handed back is no longer the station's to see");
    }

    @Test
    void aStepNobodyAnswersCanBeForcedAndSaysSoAfterwards() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement movement = announceExchange(old);
        // Roll back to the member's step by starting a fresh one nobody has answered
        int other = itemWithMember(ItemOwner.STATION);
        ItemMovement fresh = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Move Ment",
                other,
                mixedInventoryId,
                null,
                null,
                "Zu klein",
                team,
                null);

        // The station's own steps are refused: it can simply acknowledge those
        var refused = assertThrows(
                BadRequestResponse.class,
                () -> itemMovementService.force(fresh.id(), fresh.currentStepId(), team, "Keine Antwort", null));
        assertTrue(refused.getMessage().contains("station's own"));

        itemMovementService.decline(fresh.id(), team, "Aufgeräumt");
        itemMovementService.decline(movement.id(), team, "Aufgeräumt");
    }

    @Test
    void forcingNeedsANote() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement movement = announceExchange(old);
        assertThrows(
                BadRequestResponse.class,
                () -> itemMovementService.force(movement.id(), movement.currentStepId(), team, "  ", null));
        itemMovementService.decline(movement.id(), team, "Aufgeräumt");
    }

    @Test
    void aStepBelongingToTheStationIsNotTheMembersToTake() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement movement = announceExchange(old);

        // The movement now stands on a station step, and a plain member may not take it
        assertThrows(
                ForbiddenResponse.class,
                () -> itemMovementService.acknowledge(movement.id(), movement.currentStepId(), kid, "", null));

        itemMovementService.decline(movement.id(), team, "Aufgeräumt");
    }

    @Test
    void aClosedMovementGoesNoFurther() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement movement = walkToEnd(announceExchange(old), item(ItemOwner.STATION));
        assertEquals(MovementState.DONE, movement.state());

        assertThrows(BadRequestResponse.class, () -> itemMovementService.acknowledge(movement.id(), 1, team, "", null));
        assertThrows(BadRequestResponse.class, () -> itemMovementService.decline(movement.id(), team, "zu spät"));
    }

    @Test
    void aStepOtherThanTheCurrentOneIsRefused() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement movement = announceExchange(old);
        var steps = itemMovementService.stepsOf(movement);
        int last = steps.getLast().id();

        assertThrows(
                BadRequestResponse.class, () -> itemMovementService.acknowledge(movement.id(), last, team, "", null));

        itemMovementService.decline(movement.id(), team, "Aufgeräumt");
    }

    @Test
    void theStepThatNamesTheReplacementInsistsOnOne() {
        int old = itemWithMember(ItemOwner.STATION);
        ItemMovement announced = announceExchange(old);
        ItemMovement takenBack =
                itemMovementService.acknowledge(announced.id(), announced.currentStepId(), team, "", null);

        int standing = takenBack.currentStepId();
        assertThrows(
                BadRequestResponse.class,
                () -> itemMovementService.acknowledge(takenBack.id(), standing, team, "", null));

        itemMovementService.decline(takenBack.id(), team, "Aufgeräumt");
    }

    @Test
    void aFlowKeepsItsPresetsAndTheBindingsThatPointAtThem() {
        // Its own station, because the tests above bind flows of their own to the shared one
        var pristine = stationRepo.create("MovementPresetStation");
        var flows = movementFlowService.findFlows(pristine.id());
        assertEquals(10, flows.size(), "one chain per combination of purpose, owner and other end");
        assertTrue(flows.stream().anyMatch(f -> f.purpose() == MovementPurpose.ISSUE));
        assertTrue(flows.stream().anyMatch(f -> f.purpose() == MovementPurpose.RETURN));
        assertTrue(flows.stream().anyMatch(f -> f.purpose() == MovementPurpose.REQUEST));
        assertEquals(10, movementFlowService.findBindings(pristine.id()).size());

        var issue = flows.stream()
                .filter(f -> f.purpose() == MovementPurpose.ISSUE)
                .findFirst()
                .orElseThrow();
        var steps = movementFlowService.findActiveSteps(issue.id());
        assertTrue(steps.size() >= 3, "ordered, sent and received, at the very least");
        assertEquals(StepActor.STATION, steps.getFirst().actor(), "the station is the one that orders");
        assertEquals(StepSubject.INCOMING, steps.getFirst().subject());
        assertEquals("Erhalten", steps.getLast().label(), "and every chain ends with somebody saying they have it");
        assertEquals(
                1,
                steps.stream().filter(MovementFlowStep::picksItem).count(),
                "exactly one step says which piece arrived");

        stationRepo.delete(pristine.id());
    }

    /**
     * Asking a member for everything raises one chain per piece, and each piece takes the chain that
     * fits it: what the station owns goes back to its shelf, what the body above it owns into the post.
     * One movement for the lot would have to end in two places.
     *
     * <p>On a member of its own, because the shared one is wearing whatever the tests above left there.
     */
    @Test
    void everythingAMemberHoldsIsAskedForOnItsOwnChain() {
        var account = accountRepo.create("sammel" + CODES.incrementAndGet() + "@test.com", "Sam", "Mel");
        var holder = stationMemberRepo.create(station.id(), account.id());
        int own = item(ItemOwner.STATION);
        int foreign = item(ItemOwner.CLUSTER);
        itemCustodyService.assignToMember(own, holder.id(), "Sam Mel");
        itemCustodyService.assignToMember(foreign, holder.id(), "Sam Mel");

        var started = itemMovementService.requestEverythingBack(station.id(), holder.id(), "Sam Mel", team);

        assertEquals(2, started.size(), "one chain per piece");
        assertTrue(
                started.stream().allMatch(movement -> movement.purpose() == MovementPurpose.RETURN),
                "and every one of them is a return");
        var carried = started.stream().map(ItemMovement::outgoingItemId).toList();
        assertTrue(carried.contains(own) && carried.contains(foreign));
        assertNotEquals(
                started.get(0).flowId(),
                started.get(1).flowId(),
                "the station's own goes back to the shelf and the owner's into the post");

        accountRepo.delete(account.id());
    }

    /** Nothing held means nothing asked for, rather than an empty chain standing about. */
    @Test
    void askingAMemberWhoHoldsNothingStartsNothing() {
        var lonely = accountRepo.create("leer" + CODES.incrementAndGet() + "@test.com", "Leer", "Hand");
        var without = stationMemberRepo.create(station.id(), lonely.id());

        assertTrue(itemMovementService
                .requestEverythingBack(station.id(), without.id(), "Leer Hand", team)
                .isEmpty());

        accountRepo.delete(lonely.id());
    }

    /**
     * Who names the piece that arrives, asked of the movement rather than guessed at the screen.
     */
    @Test
    void aMovementSaysWhetherItsOwnerCanAnswerHere() {
        int foreign = itemWithMember(ItemOwner.CLUSTER);
        ItemMovement outside = announceExchange(foreign);
        assertFalse(itemMovementService.ownerAnswersHere(outside), "no body above this station on Ember");
        assertEquals(ItemOwner.CLUSTER, itemMovementService.ownerOf(outside));

        int mine = itemWithMember(ItemOwner.STATION);
        assertEquals(ItemOwner.STATION, itemMovementService.ownerOf(announceExchange(mine)));
    }

    /**
     * The station raises a movement in a member's name, and that is the only step of theirs it may
     * press. The receipt at the end is the member's own word, and forcing is what covers a member who
     * never gives it.
     *
     * <p>On a station of its own, because the tests above bind chains of their own to the shared one.
     */
    @Test
    void theStationOpensAChainForAMemberButDoesNotConfirmReceiptForThem() {
        var own = stationRepo.create("EigeneKette" + CODES.incrementAndGet());
        var account = accountRepo.create("kette" + CODES.incrementAndGet() + "@test.com", "Ket", "Te");
        var holder = stationMemberRepo.create(own.id(), account.id());
        int inventoryId = inventoryRepo
                .create(own.id(), "Kleiderkammer", InventoryType.MIXED, false)
                .id();
        int old = inventoryRepo
                .createItem(inventoryId, "K-" + CODES.incrementAndGet(), "Jacke", null, null, ItemOwner.STATION, null)
                .id();
        int replacement = inventoryRepo
                .createItem(inventoryId, "K-" + CODES.incrementAndGet(), "Jacke", null, null, ItemOwner.STATION, null)
                .id();
        itemCustodyService.assignToMember(old, holder.id(), "Ket Te");
        var mine = new ItemMovementService.Actor(holder.id(), true);
        var anotherStationsHand = new ItemMovementService.Actor(0, true);
        ItemMovement movement = itemMovementService.create(
                own.id(),
                MovementPurpose.EXCHANGE,
                holder.id(),
                "Ket Te",
                old,
                inventoryId,
                null,
                null,
                "Zu klein",
                mine,
                null);

        int guard = 6;
        while (guard-- > 0 && movement.state() == MovementState.OPEN) {
            MovementFlowStep current = stepStoodOn(movement);
            if (!itemMovementService.mayAct(movement, current, anotherStationsHand)) break;
            movement =
                    itemMovementService.acknowledge(movement.id(), current.id(), anotherStationsHand, "", replacement);
        }

        assertEquals(MovementState.OPEN, movement.state(), "the chain waits for the member");
        MovementFlowStep waiting = stepStoodOn(movement);
        int movementId = movement.id();
        int waitingId = waiting.id();
        assertEquals(StepActor.MEMBER, waiting.actor());
        var acting = anotherStationsHand;
        assertThrows(
                ForbiddenResponse.class,
                () -> itemMovementService.acknowledge(movementId, waitingId, acting, "", null),
                "the station cannot say for somebody else that they have it");

        var forced = itemMovementService.force(movementId, waitingId, acting, "An der Wache übergeben", null);
        assertEquals(MovementState.DONE, forced.state());

        stationRepo.delete(own.id());
        accountRepo.delete(account.id());
    }
}
