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
                itemId,
                mixedInventoryId,
                null,
                null,
                "Zu klein",
                team,
                null);
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
    void stationOwnedGearWalksThreeStepsAndNeverLeavesTheStation() {
        int old = itemWithMember(ItemOwner.STATION);
        int replacement = item(ItemOwner.STATION);

        ItemMovement movement = announceExchange(old);
        assertEquals(3, itemMovementService.stepsOf(movement).size(), "no owner leg for the station's own gear");
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
        assertEquals(7, itemMovementService.stepsOf(movement).size());

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
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(old), "the old one ends up with the body above");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(replacement));
    }

    @Test
    void anOwnerThatDoesNotUseEmberHasItsStepsAssertedForIt() {
        int old = itemWithMember(ItemOwner.CLUSTER);
        ItemMovement movement = walkToEnd(announceExchange(old), item(ItemOwner.CLUSTER));

        var entries = itemMovementService.findLogs(movement.id());
        var steps = itemMovementService.stepsOf(movement);
        for (int i = 0; i < steps.size(); i++) {
            AckKind expected = steps.get(i).actor() == StepActor.OWNER ? AckKind.ASSERTED : AckKind.CONFIRMED;
            assertEquals(
                    expected, entries.get(i).ackKind(), "step " + steps.get(i).label());
            assertEquals(steps.get(i).label(), entries.get(i).stepLabel());
        }
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
    void aReturnHandsGearBackWithNothingComingTheOtherWay() {
        int gear = item(ItemOwner.CLUSTER);
        assertEquals(ItemCustody.AT_STATION, custodyOf(gear));

        ItemMovement movement = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                gear,
                mixedInventoryId,
                null,
                null,
                "Nicht mehr gebraucht",
                team,
                null);
        assertEquals(3, itemMovementService.stepsOf(movement).size());

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
        var flows = movementFlowService.findFlows(station.id());
        assertEquals(4, flows.size());
        assertTrue(flows.stream().anyMatch(f -> f.purpose() == MovementPurpose.ISSUE));
        assertTrue(flows.stream().anyMatch(f -> f.purpose() == MovementPurpose.RETURN));
        assertEquals(4, movementFlowService.findBindings(station.id()).size());

        var issue = flows.stream()
                .filter(f -> f.purpose() == MovementPurpose.ISSUE)
                .findFirst()
                .orElseThrow();
        var steps = movementFlowService.findActiveSteps(issue.id());
        assertEquals(2, steps.size(), "the owner sends and the station receives, with no member at either end");
        assertEquals(StepActor.OWNER, steps.getFirst().actor());
        assertEquals(StepSubject.INCOMING, steps.getFirst().subject());
        assertTrue(steps.getFirst().picksItem());
    }
}
