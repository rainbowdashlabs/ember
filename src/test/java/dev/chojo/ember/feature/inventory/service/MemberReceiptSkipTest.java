/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemMovementLog;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chain that does not wait for the member's receipt confirms it for them the moment a movement reaches it,
 * in the member's name and marked as automatic, and tells nobody to confirm it. A chain without the setting
 * still waits.
 */
class MemberReceiptSkipTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int flowId;
    private static ItemMovementService.Actor team;

    private final List<MovementAdvanced> advanced = new ArrayList<>();
    private ItemMovementService movements;

    @BeforeAll
    static void setupStation() {
        station = stationRepo.create("Quittungswache");
        account = accountRepo.create("receipt@test.com", "Quit", "Tung");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Jacken", InventoryType.MIXED, false)
                .id();
        flowId = movementFlowService.resolveFlow(
                station.id(), inventoryId, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER);
        team = new ItemMovementService.Actor(member.id(), true);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void listen() {
        DomainEventHandler<MovementAdvanced> recorder = new DomainEventHandler<>() {
            @Override
            public Class<MovementAdvanced> eventType() {
                return MovementAdvanced.class;
            }

            @Override
            public void handle(MovementAdvanced event) {
                advanced.add(event);
            }
        };
        movements = new ItemMovementService(
                itemMovementRepo,
                movementFlowService,
                inventoryRepo,
                itemCustodyService,
                clusterRepo,
                itemMovementItemRepo,
                movementTargeting,
                new DomainEventBus(Set.of(recorder)));
    }

    @AfterEach
    void waitAgain() {
        movementFlowService.setSkipMemberReceipt(flowId, false);
    }

    private int item() {
        return inventoryRepo
                .createItem(inventoryId, "Q-" + CODES.incrementAndGet(), "Jacke", null, null, ItemOwner.STATION, null)
                .id();
    }

    private ItemMovement exchange(int heldItemId) {
        itemCustodyService.assignToMember(heldItemId, member.id(), "Quit Tung");
        return movements.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Quit Tung",
                heldItemId,
                inventoryId,
                null,
                null,
                "zu klein",
                team,
                null);
    }

    /** Walks every step that is the station's, which leaves the movement wherever the member comes next. */
    private ItemMovement walkTheStationSteps(ItemMovement movement, int replacementId) {
        int guard = 10;
        while (guard-- > 0 && movement.state() == MovementState.OPEN && currentActor(movement) == StepActor.STATION) {
            movement = movements.acknowledge(movement.id(), movement.currentStepId(), team, "", replacementId);
        }
        return movement;
    }

    private StepActor currentActor(ItemMovement movement) {
        return movements.stepsOf(movement).stream()
                .filter(step -> movement.currentStepId() != null && step.id() == movement.currentStepId())
                .map(step -> step.actor())
                .findFirst()
                .orElse(null);
    }

    private ItemMovementLog lastLog(ItemMovement movement) {
        return movements.findLogs(movement.id()).stream()
                .max((a, b) -> Integer.compare(a.id(), b.id()))
                .orElseThrow();
    }

    @Test
    void theReceiptConfirmsItselfForTheMemberWhenTheChainSkipsIt() {
        assertTrue(movementFlowService.setSkipMemberReceipt(flowId, true));
        int replacement = item();

        ItemMovement movement = walkTheStationSteps(exchange(item()), replacement);

        assertEquals(MovementState.DONE, movement.state(), "nothing is left waiting on the member");
        assertEquals(
                ItemCustody.WITH_MEMBER,
                inventoryRepo.findItemById(replacement).orElseThrow().custody());
        ItemMovementLog receipt = lastLog(movement);
        assertEquals(AckKind.AUTO_CONFIRMED, receipt.ackKind());
        assertEquals(member.id(), receipt.changedBy(), "recorded for the member who received it");
        assertEquals("Erhalten", receipt.stepLabel());
        assertNull(
                advanced.getLast().nextActor(),
                "the hand-over is announced as the end of the chain, never as a step for the member to confirm");
        assertTrue(advanced.stream().noneMatch(event -> event.nextActor() == StepActor.MEMBER));
    }

    @Test
    void aChainThatDoesNotSkipItStillWaitsForTheMember() {
        ItemMovement movement = walkTheStationSteps(exchange(item()), item());

        assertEquals(MovementState.OPEN, movement.state());
        assertEquals(StepActor.MEMBER, currentActor(movement));
        assertEquals(StepActor.MEMBER, advanced.getLast().nextActor(), "the member is asked to confirm");
        assertTrue(
                movements.findLogs(movement.id()).stream().noneMatch(log -> log.ackKind() == AckKind.AUTO_CONFIRMED));
    }

    @Test
    void switchingItOnLeavesAMovementAlreadyWaitingAlone() {
        ItemMovement movement = walkTheStationSteps(exchange(item()), item());

        movementFlowService.setSkipMemberReceipt(flowId, true);

        ItemMovement after = movements.findById(movement.id()).orElseThrow();
        assertEquals(MovementState.OPEN, after.state());
        assertEquals(movement.currentStepId(), after.currentStepId());
    }

    @Test
    void aMovementPutOntoTheReceiptConfirmsItTooWhenTheChainSkipsIt() {
        ItemMovement movement = walkTheStationSteps(exchange(item()), item());
        movementFlowService.setSkipMemberReceipt(flowId, true);

        movements.rechain(movement.id(), movements.stepsOf(movement).size() - 1, member.id());

        ItemMovement after = movements.findById(movement.id()).orElseThrow();
        assertEquals(MovementState.DONE, after.state());
        assertEquals(AckKind.AUTO_CONFIRMED, lastLog(after).ackKind());
    }

    @Test
    void theOpeningRequestIsNeverTakenForAReceipt() {
        movementFlowService.setSkipMemberReceipt(flowId, true);
        ItemMovement movement = exchange(item());

        movements.rechain(movement.id(), 0, member.id());

        ItemMovement after = movements.findById(movement.id()).orElseThrow();
        assertEquals(MovementState.OPEN, after.state());
        assertEquals(StepActor.MEMBER, currentActor(after), "it stands on the request it was put back on");
    }
}
