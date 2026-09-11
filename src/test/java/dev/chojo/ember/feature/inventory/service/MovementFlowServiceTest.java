/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.FlowProblem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
        assertEquals(11, movementFlowService.findFlows(seeded.id()).size(), "one chain per combination");

        movementFlowService.createFlow(seeded.id(), "Eigener Ablauf", MovementPurpose.RETURN);
        movementFlowService.ensurePresets(seeded.id());
        assertEquals(12, movementFlowService.findFlows(seeded.id()).size(), "seeding does not run twice");

        stationRepo.delete(seeded.id());
    }

    /**
     * A chain that was edited is given the preset back, and everything that pointed at it still does.
     *
     * <p>The binding is the part that matters here. A chain written again is the same chain, so the
     * combination it serves finds it afterwards exactly as it did before.
     */
    @Test
    void anEditedChainIsWrittenAgainFromThePreset() {
        var own = stationRepo.create("FlowRestoreStation" + NAMES.incrementAndGet());
        try {
            int flowId = movementFlowService.resolveFlow(
                    own.id(), null, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER);
            var preset = labelsOf(flowId);

            var steps = movementFlowService.findActiveSteps(flowId);
            MovementFlowStep opening = steps.getFirst();
            assertTrue(movementFlowService.updateStep(
                    opening.id(),
                    "Ganz anders benannt",
                    opening.actor(),
                    opening.subject(),
                    opening.custodyAfter(),
                    opening.picksItem()));
            assertTrue(movementFlowService.archiveStep(steps.get(3).id()));
            assertNotEquals(preset, labelsOf(flowId), "the chain now says something else");

            movementFlowService.restoreToPreset(flowId, member.id(), List.of());

            assertEquals(preset, labelsOf(flowId), "the preset's steps are back, in the preset's order");
            assertEquals(
                    flowId,
                    movementFlowService.resolveFlow(
                            own.id(), null, ItemOwner.STATION, null, MovementPurpose.EXCHANGE, MovementParty.MEMBER),
                    "and the binding still points at the same chain");
            assertTrue(movementFlowService.problemOf(flowId).isEmpty(), "which can be walked again");
        } finally {
            stationRepo.delete(own.id());
        }
    }

    private static List<String> labelsOf(int flowId) {
        return movementFlowService.findActiveSteps(flowId).stream()
                .map(MovementFlowStep::label)
                .toList();
    }

    /** The chain of the body above the station is shown there and written elsewhere. */
    @Test
    void aChainTheBodyAboveOwnsIsNotTheStationsToWriteAgain() {
        var home = stationRepo.create("FlowClusterHome" + NAMES.incrementAndGet());
        var cluster = clusterRepo.create("Kreisverband Vorlage" + NAMES.incrementAndGet(), null, home.id());
        var theirs = movementFlowService.createClusterFlow(cluster.id(), "Tausch", MovementPurpose.EXCHANGE);

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.restoreToPreset(theirs.id(), member.id(), List.of()),
                "that chain belongs to the body above the station");
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.planRestore(theirs.id()),
                "and a plan for writing it again is refused in the same breath");
    }

    /** A chain nothing covers says so rather than being written from a preset that means something else. */
    @Test
    void aChainWithNoPresetBehindItIsNotWrittenAgain() {
        MovementFlow mine = freshFlow(MovementPurpose.ISSUE);
        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.restoreToPreset(mine.id(), member.id(), List.of()),
                "nothing is bound to it, so there is no combination to look a preset up by");

        movementFlowService.addStep(
                mine.id(), "Angefragt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER, true);
        movementFlowService.addStep(
                mine.id(), "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, false);
        movementFlowService.bind(
                station.id(), null, ItemOwner.STATION, MovementPurpose.ISSUE, MovementParty.STORE, mine.id());

        assertThrows(
                BadRequestResponse.class,
                () -> movementFlowService.restoreToPreset(mine.id(), member.id(), List.of()),
                "no preset covers the station's own gear going to its own store");
    }

    /**
     * A movement standing on a step the rewrite takes away is carried onto the step that means what
     * its own meant, and its log says where it came from.
     */
    @Test
    void aMovementOnTheChainIsCarriedOntoTheStepThatMeansTheSame() {
        Carrier carrier = carrierStation();
        var movement = carrier.movement();
        try {
            MovementFlowStep stood =
                    movementFlowService.findStep(movement.currentStepId()).orElseThrow();
            assertTrue(movementFlowService.updateStep(
                    stood.id(),
                    "Ganz anders benannt",
                    stood.actor(),
                    stood.subject(),
                    stood.custodyAfter(),
                    stood.picksItem()));

            movementFlowService.restoreToPreset(
                    movement.flowId(), carrier.member().id(), List.of());

            var carried = itemMovementRepo.findById(movement.id()).orElseThrow();
            MovementFlowStep landing =
                    movementFlowService.findStep(carried.currentStepId()).orElseThrow();
            assertNotEquals(stood.id(), landing.id(), "the step it stood on is gone");
            assertEquals(stood.subject(), landing.subject());
            assertEquals(
                    stood.custodyAfter(),
                    landing.custodyAfter(),
                    "and it lands where the same piece ends up in the same place");
            assertEquals("Altes Teil zurückgenommen", landing.label());

            var last = itemMovementService.findLogs(movement.id()).getLast();
            assertEquals(
                    AckKind.CORRECTED, last.ackKind(), "the chain changed underneath, which reads as a correction");
            assertEquals(landing.id(), last.stepId());
            assertTrue(last.note().contains("Ganz anders benannt"), "and the entry says where it came from");
        } finally {
            takeAway(carrier);
        }
    }

    /**
     * A plan on a chain nobody has touched answers for everything standing on it, so the restore is
     * still one press.
     */
    @Test
    void aPlanOnAnUntouchedChainAnswersForEveryMovement() {
        Carrier carrier = carrierStation();
        try {
            int flowId = carrier.movement().flowId();
            MovementFlowStep stood = movementFlowService
                    .findStep(carrier.movement().currentStepId())
                    .orElseThrow();

            var plan = movementFlowService.planRestore(flowId);

            assertEquals(
                    movementFlowService.findActiveSteps(flowId).size(),
                    plan.steps().size(),
                    "the plan says the whole chain the preset would write");
            assertEquals(
                    List.of(0, 1, 2, 3, 4),
                    plan.steps().stream()
                            .map(MovementFlowService.PlannedStep::index)
                            .toList(),
                    "each step counted from the front, which is how a landing names one");
            assertEquals(1, plan.movements().size());

            var landing = plan.movements().getFirst();
            assertEquals(stood.id(), landing.stepId(), "a landing is asked about the step, not the movement");
            assertEquals(1, landing.movements(), "and says how many movements the answer moves");
            assertTrue(landing.certain(), "the preset says once what that step said");
            assertEquals(stood.label(), landing.standingOn());
            assertEquals(
                    stood.label(), plan.steps().get(landing.suggestedIndex()).label());
        } finally {
            takeAway(carrier);
        }
    }

    /**
     * A step the preset says twice is a question, not a guess: the plan leaves the landing empty and
     * a restore that was told nothing refuses rather than picking one of the two.
     */
    @Test
    void aStepThePresetSaysTwiceIsAskedRatherThanGuessed() {
        Carrier carrier = carrierStation();
        try {
            int flowId = carrier.movement().flowId();
            var steps = movementFlowService.findActiveSteps(flowId);
            MovementFlowStep saidTwice = steps.get(3);
            assertEquals(steps.get(4).subject(), saidTwice.subject(), "the chain ends on two steps that say the same");
            assertEquals(steps.get(4).custodyAfter(), saidTwice.custodyAfter());
            itemMovementRepo.moveToStep(carrier.movement().id(), saidTwice.id());
            var written = stepIdsOf(flowId);

            var landing = movementFlowService.planRestore(flowId).movements().getFirst();
            assertFalse(landing.certain());
            assertNull(landing.suggestedIndex(), "an unsure answer is offered empty rather than filled in");
            assertEquals(saidTwice.label(), landing.standingOn());

            var refusal = assertThrows(
                    BadRequestResponse.class,
                    () -> movementFlowService.restoreToPreset(
                            flowId, carrier.member().id(), List.of()));
            assertTrue(
                    refusal.getMessage().contains(saidTwice.label()),
                    "and the refusal says which step stopped it, since that is what the answer is about");
            assertEquals(written, stepIdsOf(flowId), "nothing was written");
            assertEquals(
                    saidTwice.id(),
                    itemMovementRepo
                            .findById(carrier.movement().id())
                            .orElseThrow()
                            .currentStepId(),
                    "and the movement stands where it stood");
        } finally {
            takeAway(carrier);
        }
    }

    /** Nothing to ask about, nothing to say: a chain the preset answers for is written again as it is. */
    @Test
    void aRestoreNobodyHasToBeAskedAboutIsToldNothing() {
        Carrier carrier = carrierStation();
        try {
            int flowId = carrier.movement().flowId();
            var preset = labelsOf(flowId);

            movementFlowService.restoreToPreset(flowId, carrier.member().id(), List.of());

            assertEquals(preset, labelsOf(flowId));
            var carried = itemMovementRepo.findById(carrier.movement().id()).orElseThrow();
            assertNotEquals(
                    carrier.movement().currentStepId(), carried.currentStepId(), "the chain it walks is a new one");
            assertTrue(
                    movementFlowService
                            .findStep(carrier.movement().currentStepId())
                            .isEmpty(),
                    "and the step it walked has gone");
        } finally {
            takeAway(carrier);
        }
    }

    /** Somebody who says where a movement lands is obeyed, even where the preset had an answer of its own. */
    @Test
    void aChosenLandingWinsOverTheOneThePresetWouldGive() {
        Carrier carrier = carrierStation();
        try {
            int flowId = carrier.movement().flowId();
            String opening =
                    movementFlowService.findActiveSteps(flowId).getFirst().label();
            Integer stood = itemMovementRepo
                    .findById(carrier.movement().id())
                    .orElseThrow()
                    .currentStepId();

            movementFlowService.restoreToPreset(
                    flowId, carrier.member().id(), List.of(new MovementFlowService.ChosenLanding(stood, 0)));

            var carried = itemMovementRepo.findById(carrier.movement().id()).orElseThrow();
            assertEquals(
                    opening,
                    movementFlowService
                            .findStep(carried.currentStepId())
                            .orElseThrow()
                            .label(),
                    "it stands where it was told to, not where the preset would have put it");
        } finally {
            takeAway(carrier);
        }
    }

    /** A landing on a step the preset does not write, or for a movement standing elsewhere, is refused. */
    @Test
    void aLandingTheChainCannotCarryOutIsRefused() {
        Carrier carrier = carrierStation();
        Carrier elsewhere = carrierStation();
        try {
            int flowId = carrier.movement().flowId();
            var written = stepIdsOf(flowId);
            Integer stood = itemMovementRepo
                    .findById(carrier.movement().id())
                    .orElseThrow()
                    .currentStepId();
            Integer otherChain = itemMovementRepo
                    .findById(elsewhere.movement().id())
                    .orElseThrow()
                    .currentStepId();

            assertThrows(
                    BadRequestResponse.class,
                    () -> movementFlowService.restoreToPreset(
                            flowId, carrier.member().id(), List.of(new MovementFlowService.ChosenLanding(stood, 9))),
                    "the preset writes five steps, so there is no ninth to land on");
            assertThrows(
                    BadRequestResponse.class,
                    () -> movementFlowService.restoreToPreset(
                            flowId,
                            carrier.member().id(),
                            List.of(new MovementFlowService.ChosenLanding(otherChain, 0))),
                    "and that step belongs to another chain altogether");
            assertEquals(written, stepIdsOf(flowId), "nothing was written either time");
        } finally {
            takeAway(elsewhere);
            takeAway(carrier);
        }
    }

    /**
     * A station of its own with one exchange under way on the chain it was seeded with, which is what
     * a chain written again has to carry across.
     */
    private static Carrier carrierStation() {
        int number = NAMES.incrementAndGet();
        var own = stationRepo.create("FlowCarryStation" + number);
        var carrier = accountRepo.create("carry" + number + "@test.com", "Car", "Ry");
        var holder = stationMemberRepo.create(own.id(), carrier.id());
        int inventory = inventoryRepo
                .create(own.id(), "Jacken", InventoryType.MIXED, false)
                .id();
        int gear = inventoryRepo
                .createItem(inventory, "R-" + number, "Jacke", null, null, ItemOwner.STATION, null)
                .id();
        itemCustodyService.assignToMember(gear, holder.id(), "Car Ry");
        var movement = itemMovementService.create(
                own.id(),
                MovementPurpose.EXCHANGE,
                holder.id(),
                "Car Ry",
                gear,
                inventory,
                null,
                null,
                "Zu klein",
                new ItemMovementService.Actor(holder.id(), true),
                null);
        return new Carrier(own, carrier, holder, movement);
    }

    private static void takeAway(Carrier carrier) {
        stationRepo.delete(carrier.station().id());
        accountRepo.delete(carrier.account().id());
    }

    private static List<Integer> stepIdsOf(int flowId) {
        return movementFlowService.findActiveSteps(flowId).stream()
                .map(MovementFlowStep::id)
                .toList();
    }

    /** A station standing on its own, with the one movement the tests carry across. */
    private record Carrier(Station station, Account account, StationMember member, ItemMovement movement) {}

    /**
     * A chain put back reads as one chain counted from the front, however often it has been put back.
     *
     * <p>The steps are written behind the ones still standing and the old ones taken away afterwards,
     * so without numbering the chain again it opened where the last one ended. Stilled steps went the
     * same way: kept for a history that carries its own words, they gathered one chain per restore.
     */
    @Test
    void aChainPutBackTwiceIsStillOneChainCountedFromTheFront() {
        var seeded = stationRepo.create("FlowRenumberStation" + NAMES.incrementAndGet());
        try {
            int flowId = movementFlowService.findFlows(seeded.id()).getFirst().id();
            movementFlowService.archiveStep(
                    movementFlowService.findActiveSteps(flowId).getLast().id());

            movementFlowService.restoreToPreset(flowId, member.id(), List.of());
            movementFlowService.restoreToPreset(flowId, member.id(), List.of());

            var written = movementFlowService.findAllSteps(flowId);
            assertEquals(
                    movementFlowService.findActiveSteps(flowId).size(),
                    written.size(),
                    "nothing stilled is left over, however often it was put back");
            assertEquals(
                    IntStream.range(0, written.size()).boxed().toList(),
                    written.stream().map(MovementFlowStep::position).toList(),
                    "and it is numbered from the front rather than from wherever the last chain ended");
        } finally {
            stationRepo.delete(seeded.id());
        }
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
