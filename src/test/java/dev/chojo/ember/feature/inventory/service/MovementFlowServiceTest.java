/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.FlowProblem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MovementFlowServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("FlowStation");
        account = accountRepo.create("flow@test.com", "Flow", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Handschuhe", InventoryType.MIXED, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private MovementFlow freshFlow(MovementPurpose purpose) {
        return movementFlowService.createFlow(station.id(), "Ablauf " + NAMES.incrementAndGet(), purpose);
    }

    @Test
    void aFlowIsNamedRenamedAndRetiredRatherThanDeleted() {
        MovementFlow flow = freshFlow(MovementPurpose.RETURN);
        assertEquals(station.id(), flow.stationId());
        assertFalse(flow.archived());

        assertTrue(movementFlowService.renameFlow(flow.id(), "Rückgabe, kurz"));
        assertEquals(
                "Rückgabe, kurz",
                movementFlowService.findFlow(flow.id()).orElseThrow().name());

        assertTrue(movementFlowService.archiveFlow(flow.id()));
        assertTrue(movementFlowService.findFlow(flow.id()).orElseThrow().archived());
        assertTrue(
                movementFlowService.findFlows(station.id()).stream().anyMatch(f -> f.id() == flow.id()),
                "a retired flow is still listed, because movements walked it");
    }

    /** A refusal names the rule in the way, so the reader is told in their own words. */
    private static void refusedWith(FlowProblem.Code code, Executable call) {
        assertEquals(
                code, assertThrows(FlowRefusedException.class, call).problem().code());
    }

    @Test
    void aFlowAndItsStepsNeedNames() {
        refusedWith(
                FlowProblem.Code.FLOW_NAME_REQUIRED,
                () -> movementFlowService.createFlow(station.id(), " ", MovementPurpose.RETURN));

        MovementFlow flow = freshFlow(MovementPurpose.RETURN);
        refusedWith(FlowProblem.Code.FLOW_NAME_REQUIRED, () -> movementFlowService.renameFlow(flow.id(), ""));
        refusedWith(
                FlowProblem.Code.STEP_LABEL_REQUIRED,
                () -> movementFlowService.addStep(
                        flow.id(), "", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false));
    }

    @Test
    void stepsLandAtTheEndAndCanBeEditedAndRetired() {
        MovementFlow flow = freshFlow(MovementPurpose.RETURN);
        var first = movementFlowService.addStep(
                flow.id(), "Angekündigt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false);
        var second = movementFlowService.addStep(
                flow.id(), "Verschickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        assertEquals(0, first.position());
        assertEquals(1, second.position());

        assertTrue(movementFlowService.updateStep(
                second.id(),
                "An den Träger geschickt",
                StepActor.STATION,
                StepSubject.OUTGOING,
                ItemCustody.IN_TRANSIT,
                false));
        assertEquals(
                "An den Träger geschickt",
                movementFlowService.findActiveSteps(flow.id()).get(1).label());

        assertTrue(movementFlowService.archiveStep(second.id()));
        assertEquals(1, movementFlowService.findActiveSteps(flow.id()).size());
        assertEquals(2, movementFlowService.findAllSteps(flow.id()).size(), "the retired step still renders");

        // A step added afterwards does not reuse the retired step's place
        var third = movementFlowService.addStep(
                flow.id(), "Eingetroffen", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        assertEquals(2, third.position());
    }

    @Test
    void aStepCannotLeaveAnItemSomewhereAMovementDoesNotPutIt() {
        MovementFlow flow = freshFlow(MovementPurpose.RETURN);
        refusedWith(
                FlowProblem.Code.ILLEGAL_STEP_CUSTODY,
                () -> movementFlowService.addStep(
                        flow.id(), "Verloren", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.LOST, false));
        refusedWith(
                FlowProblem.Code.ILLEGAL_STEP_CUSTODY,
                () -> movementFlowService.addStep(
                        flow.id(),
                        "Verliehen",
                        StepActor.STATION,
                        StepSubject.OUTGOING,
                        ItemCustody.WITH_PARTNER,
                        false));
    }

    @Test
    void onlyOneStepOfAFlowNamesTheReplacement() {
        MovementFlow flow = freshFlow(MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(), "Verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT, true);

        refusedWith(
                FlowProblem.Code.ITEM_ALREADY_NAMED,
                () -> movementFlowService.addStep(
                        flow.id(), "Nochmal", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, true));

        refusedWith(
                FlowProblem.Code.ONLY_ARRIVAL_NAMES_ITEM,
                () -> movementFlowService.addStep(
                        flow.id(),
                        "Falsch herum",
                        StepActor.STATION,
                        StepSubject.OUTGOING,
                        ItemCustody.AT_STATION,
                        true));
    }

    @Test
    void aStepInUseCannotChangeItsBehaviourButCanStillBeRenamed() {
        int itemId = inventoryRepo
                .createItem(inventoryId, "F-" + NAMES.incrementAndGet(), "Glove", null, null, ItemOwner.CLUSTER, null)
                .id();
        var movement = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                null,
                itemId,
                inventoryId,
                null,
                null,
                "Nicht mehr gebraucht",
                new ItemMovementService.Actor(member.id(), true),
                null);
        int flowId = movement.flowId();
        var step = movementFlowService.findActiveSteps(flowId).getLast();

        // Renaming is always allowed: the behaviour hangs off the custody, never off the words
        assertTrue(movementFlowService.updateStep(
                step.id(),
                "Beim Träger angekommen",
                step.actor(),
                step.subject(),
                step.custodyAfter(),
                step.picksItem()));

        refusedWith(
                FlowProblem.Code.FLOW_IN_USE,
                () -> movementFlowService.updateStep(
                        step.id(),
                        "Beim Träger angekommen",
                        StepActor.MEMBER,
                        step.subject(),
                        step.custodyAfter(),
                        step.picksItem()));
        refusedWith(FlowProblem.Code.FLOW_IN_USE, () -> movementFlowService.archiveStep(step.id()));
        refusedWith(FlowProblem.Code.FLOW_IN_USE, () -> movementFlowService.archiveFlow(flowId));
        refusedWith(
                FlowProblem.Code.FLOW_IN_USE,
                () -> movementFlowService.addStep(
                        flowId, "Noch einer", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false));

        itemMovementService.decline(movement.id(), new ItemMovementService.Actor(member.id(), true), "Aufgeräumt");
    }

    @Test
    void aBindingForOneInventoryBeatsTheStationWideOne() {
        MovementFlow special = freshFlow(MovementPurpose.EXCHANGE);
        movementFlowService.addStep(
                special.id(),
                "Sofort angefordert",
                StepActor.MEMBER,
                StepSubject.OUTGOING,
                ItemCustody.WITH_MEMBER,
                false);
        movementFlowService.addStep(
                special.id(),
                "Sofort getauscht",
                StepActor.STATION,
                StepSubject.INCOMING,
                ItemCustody.WITH_MEMBER,
                true);

        int stationWide = movementFlowService.resolveFlow(
                station.id(), inventoryId, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER);
        movementFlowService.bind(
                station.id(),
                inventoryId,
                ItemOwner.STATION,
                MovementPurpose.EXCHANGE,
                MovementParty.MEMBER,
                special.id());

        assertEquals(
                special.id(),
                movementFlowService.resolveFlow(
                        station.id(),
                        inventoryId,
                        ItemOwner.STATION,
                        null,
                        MovementPurpose.EXCHANGE,
                        MovementParty.MEMBER));
        assertEquals(
                stationWide,
                movementFlowService.resolveFlow(
                        station.id(), null, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER),
                "the station-wide binding is untouched by one made for a single inventory");
        assertNotEquals(
                special.id(),
                movementFlowService.resolveFlow(
                        station.id(),
                        inventoryId,
                        ItemOwner.CLUSTER,
                        null,
                        MovementPurpose.EXCHANGE,
                        MovementParty.MEMBER),
                "and it changes only the owner it was bound for");

        // Put it back so the other tests find the presets where they left them
        movementFlowService.bind(
                station.id(),
                inventoryId,
                ItemOwner.STATION,
                MovementPurpose.EXCHANGE,
                MovementParty.MEMBER,
                stationWide);
    }

    @Test
    void aFlowIsOnlyBoundToTheStationAndPurposeItBelongsTo() {
        MovementFlow returnFlow = freshFlow(MovementPurpose.RETURN);
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(),
                        null,
                        ItemOwner.CLUSTER,
                        MovementPurpose.EXCHANGE,
                        MovementParty.MEMBER,
                        returnFlow.id()));
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        station.id(), null, ItemOwner.CLUSTER, MovementPurpose.RETURN, MovementParty.MEMBER, 999_999));

        var other = stationRepo.create("FlowOtherStation");
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.bind(
                        other.id(),
                        null,
                        ItemOwner.CLUSTER,
                        MovementPurpose.RETURN,
                        MovementParty.MEMBER,
                        returnFlow.id()));
        stationRepo.delete(other.id());
    }

    /**
     * A combination nothing covers says so rather than guessing at a chain that means something else.
     * Issuing the station's own gear onto the station's own shelf is not a movement between parties,
     * so no preset covers it and none should be invented.
     */
    @Test
    void aStationWithNothingBoundForAPairSaysSoRatherThanGuessing() {
        var bare = stationRepo.create("FlowBareStation");
        movementFlowService.ensurePresets(bare.id());
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.resolveFlow(
                        bare.id(), null, ItemOwner.STATION, null, MovementPurpose.ISSUE, MovementParty.STORE));
        stationRepo.delete(bare.id());
    }

    @Test
    void presetsAreSeededOnceAndNotAgainOverAnEditedFlowSet() {
        var seeded = stationRepo.create("FlowSeedStation");
        assertEquals(10, movementFlowService.findFlows(seeded.id()).size(), "one chain per combination");

        movementFlowService.createFlow(seeded.id(), "Eigener Ablauf", MovementPurpose.RETURN);
        movementFlowService.ensurePresets(seeded.id());
        assertEquals(11, movementFlowService.findFlows(seeded.id()).size(), "seeding does not run twice");

        stationRepo.delete(seeded.id());
    }

    @Test
    void editingAStepThatIsNotThereIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.updateStep(
                        999_999, "X", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.AT_STATION, false));
        assertThrows(BadRequestResponse.class, () -> movementFlowService.archiveStep(999_999));
    }
}
