/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chains a station starts with, and the shape every chain has to keep.
 *
 * <p>Ten chains over three station kinds is more than the end-to-end stories can walk in reasonable
 * time, and the part that goes wrong quietly is not the walking but the choosing: a combination with
 * no chain bound answers that no flow is bound, at the moment somebody needed it. That question is
 * asked here, once per combination.
 */
class MovementPresetsTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static Station station;
    private static int inventoryId;

    /** Every combination a station can raise a movement for, and the chain each one has to find. */
    private static final List<Combination> COMBINATIONS = List.of(
            new Combination(MovementPurpose.ISSUE, ItemOwner.CLUSTER, MovementParty.STORE),
            new Combination(MovementPurpose.ISSUE, ItemOwner.CLUSTER, MovementParty.MEMBER),
            new Combination(MovementPurpose.ISSUE, ItemOwner.STATION, MovementParty.MEMBER),
            new Combination(MovementPurpose.RETURN, ItemOwner.CLUSTER, MovementParty.STORE),
            new Combination(MovementPurpose.RETURN, ItemOwner.CLUSTER, MovementParty.MEMBER),
            new Combination(MovementPurpose.RETURN, ItemOwner.STATION, MovementParty.MEMBER),
            new Combination(MovementPurpose.EXCHANGE, ItemOwner.STATION, MovementParty.MEMBER),
            new Combination(MovementPurpose.EXCHANGE, ItemOwner.CLUSTER, MovementParty.MEMBER),
            new Combination(MovementPurpose.REQUEST, ItemOwner.CLUSTER, MovementParty.STORE),
            new Combination(MovementPurpose.REQUEST, ItemOwner.CLUSTER, MovementParty.MEMBER));

    private record Combination(MovementPurpose purpose, ItemOwner ownerKind, MovementParty party) {}

    @BeforeAll
    static void setup() {
        station = stationRepo.create("PresetStation");
        inventoryId = inventoryRepo
                .create(station.id(), "Einsatzkleidung", InventoryType.MIXED, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void everyCombinationFindsAChain() {
        for (Combination combination : COMBINATIONS) {
            int flowId = assertDoesNotThrow(
                    () -> movementFlowService.resolveFlow(
                            station.id(),
                            inventoryId,
                            combination.ownerKind(),
                            null,
                            combination.purpose(),
                            combination.party()),
                    "%s of %s gear to the %s has no chain"
                            .formatted(combination.purpose(), combination.ownerKind(), combination.party()));
            assertTrue(flowId > 0);
        }
    }

    @Test
    void noTwoCombinationsShareAChain() {
        var seen = new HashMap<Integer, Combination>();
        for (Combination combination : COMBINATIONS) {
            int flowId = movementFlowService.resolveFlow(
                    station.id(),
                    inventoryId,
                    combination.ownerKind(),
                    null,
                    combination.purpose(),
                    combination.party());
            Combination clash = seen.put(flowId, combination);
            assertNull(clash, "%s and %s resolve to the same chain".formatted(clash, combination));
        }
    }

    /**
     * The shape the concept fixes: a request at the front, a receipt at the back, and the receipt
     * belongs to whoever ends up holding the gear.
     */
    @Test
    void everyPresetOpensWithARequestAndClosesWithAReceipt() {
        for (Combination combination : COMBINATIONS) {
            int flowId = movementFlowService.resolveFlow(
                    station.id(),
                    inventoryId,
                    combination.ownerKind(),
                    null,
                    combination.purpose(),
                    combination.party());
            var steps = movementFlowService.findActiveSteps(flowId);

            assertTrue(steps.size() >= 2, "%s is shorter than two steps".formatted(combination));
            assertNotEquals(
                    ItemCustody.IN_TRANSIT,
                    steps.getLast().custodyAfter(),
                    "%s ends with the gear still in the post".formatted(combination));

            StepActor receiver = steps.getLast().actor();
            if (combination.party() == MovementParty.MEMBER && combination.purpose() != MovementPurpose.RETURN) {
                assertEquals(
                        StepActor.MEMBER,
                        receiver,
                        "%s ends at a member, so the member confirms".formatted(combination));
            }
            assertTrue(
                    movementFlowService.problemOf(flowId).isEmpty(),
                    "%s cannot be walked: %s"
                            .formatted(
                                    combination,
                                    movementFlowService.problemOf(flowId).orElse("")));
        }
    }

    /** A chain that receives something has to say which piece arrived, or it stops on that step. */
    @Test
    void everyChainThatReceivesNamesTheArrivingPiece() {
        for (Combination combination : COMBINATIONS) {
            if (combination.purpose() == MovementPurpose.RETURN) continue;
            int flowId = movementFlowService.resolveFlow(
                    station.id(),
                    inventoryId,
                    combination.ownerKind(),
                    null,
                    combination.purpose(),
                    combination.party());
            var naming = movementFlowService.findActiveSteps(flowId).stream()
                    .filter(MovementFlowStep::picksItem)
                    .toList();
            assertEquals(1, naming.size(), "%s names the arriving piece exactly once".formatted(combination));
            assertEquals(StepSubject.INCOMING, naming.getFirst().subject());
        }
    }

    /** A station that wrote its own chain keeps it, and is given only what it has no chain for. */
    @Test
    void seedingAgainLeavesWhatTheStationWroteAlone() {
        var own = stationRepo.create("PresetOwnStation" + NAMES.incrementAndGet());
        try {
            int before = movementFlowService.resolveFlow(
                    own.id(), null, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER);

            var mine = movementFlowService.createFlow(own.id(), "Eigener Tausch", MovementPurpose.EXCHANGE);
            movementFlowService.addStep(
                    mine.id(), "Angefordert", StepActor.MEMBER, StepSubject.OUTGOING, ItemCustody.WITH_MEMBER, false);
            movementFlowService.addStep(
                    mine.id(), "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER, true);
            movementFlowService.bind(
                    own.id(), null, ItemOwner.STATION, MovementPurpose.EXCHANGE, MovementParty.MEMBER, mine.id());

            movementFlowService.ensurePresets(own.id());

            assertEquals(
                    mine.id(),
                    movementFlowService.resolveFlow(
                            own.id(), null, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER),
                    "the chain the station wrote stays bound");
            assertNotEquals(before, mine.id());
        } finally {
            stationRepo.delete(own.id());
        }
    }

    @Test
    void aChainWithOneStepIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Zu kurz " + NAMES.incrementAndGet(), MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Nur einer", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(), null, ItemOwner.STATION, MovementPurpose.RETURN, MovementParty.STORE, flow.id()),
                "a chain of one step asks for gear and never says it arrived");
    }

    @Test
    void aChainEndingInThePostIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Unterwegs " + NAMES.incrementAndGet(), MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Angefordert", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false);
        movementFlowService.addStep(
                flow.id(), "Abgeschickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(), null, ItemOwner.STATION, MovementPurpose.RETURN, MovementParty.STORE, flow.id()),
                "a chain that ends in the post never says where the gear is");
    }

    @Test
    void aChainThatReceivesWithoutNamingThePieceIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Ohne Teil " + NAMES.incrementAndGet(), MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(), "Bestellt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER, false);
        movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, false);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(), null, ItemOwner.STATION, MovementPurpose.ISSUE, MovementParty.STORE, flow.id()),
                "something arrives and nothing says which piece it was");
    }

    @Test
    void anExchangeWithoutBothDirectionsIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Halber Tausch " + NAMES.incrementAndGet(), MovementPurpose.EXCHANGE);
        movementFlowService.addStep(
                flow.id(), "Angefordert", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_OWNER, false);
        movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER, true);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(),
                        null,
                        ItemOwner.STATION,
                        MovementPurpose.EXCHANGE,
                        MovementParty.MEMBER,
                        flow.id()),
                "an exchange has a piece going as well as one coming");
    }

    /** Order is the whole of what a chain says, so it can be changed without rewriting the steps. */
    @Test
    void stepsArePutInAnotherOrder() {
        var flow = movementFlowService.createFlow(
                station.id(), "Umsortiert " + NAMES.incrementAndGet(), MovementPurpose.RETURN);
        var first = movementFlowService.addStep(
                flow.id(), "Angefordert", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_MEMBER, false);
        var second = movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);

        movementFlowService.reorderSteps(flow.id(), List.of(second.id(), first.id()));

        var steps = movementFlowService.findActiveSteps(flow.id());
        assertEquals(second.id(), steps.getFirst().id());
        assertEquals(first.id(), steps.getLast().id());

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.reorderSteps(flow.id(), List.of(second.id())),
                "naming an order means naming every step");
    }

    /** Which piece arrived is said once, and the second step trying to say it is refused as it is written. */
    @Test
    void aSecondStepNamingTheArrivingPieceIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Doppelt benannt " + NAMES.incrementAndGet(), MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(), "Erstes", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, true);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.addStep(
                        flow.id(), "Zweites", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, true),
                "only one step says which piece arrived");
    }

    /** A retired step that would leave too short a chain behind is refused rather than silently taken. */
    @Test
    void retiringAStepThatWouldStrandTheChainIsRefused() {
        var flow = movementFlowService.createFlow(
                station.id(), "Knapp " + NAMES.incrementAndGet(), MovementPurpose.RETURN);
        var first = movementFlowService.addStep(
                flow.id(), "Angefordert", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_MEMBER, false);
        movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.STATION, MovementPurpose.RETURN, MovementParty.STORE, flow.id());

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.archiveStep(first.id()),
                "one step left is not a chain anybody can walk");
    }

    /** While a chain is being written it says what is still missing, rather than only refusing later. */
    @Test
    void aChainUnderConstructionSaysWhatIsMissing() {
        var flow = movementFlowService.createFlow(
                station.id(), "Im Bau " + NAMES.incrementAndGet(), MovementPurpose.RETURN);
        assertTrue(movementFlowService.problemOf(flow.id()).isPresent(), "an empty chain cannot be walked");

        movementFlowService.addStep(
                flow.id(), "Angefordert", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_MEMBER, false);
        movementFlowService.addStep(
                flow.id(), "Erhalten", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);

        assertTrue(movementFlowService.problemOf(flow.id()).isEmpty(), "and now it can");
    }
}
