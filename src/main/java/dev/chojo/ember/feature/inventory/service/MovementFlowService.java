/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowBinding;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import dev.chojo.ember.feature.inventory.repository.MovementFlowRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Flows, their steps, and the bindings that decide which flow a movement walks.
 *
 * <p>There is no list of hardcoded step kinds anywhere in here. A step is a party and a resulting
 * custody, and every preset below is an ordinary row a station may edit, duplicate or throw away.
 */
@Singleton
public class MovementFlowService {
    private static final Logger log = LoggerFactory.getLogger(MovementFlowService.class);

    /**
     * The presets a station starts with. Kept next to the seeding that writes them rather than in
     * the migration alone, because a station created after that migration needs them too.
     *
     * <p>None of them has a step belonging to the body above the station, and that is the point. A
     * station whose umbrella organisation does not use Ember knows two things about the parcel it
     * posted: that it sent it, and that something came back. Asking it to tick "arrived at the
     * owner" and "replacement dispatched" would be asking it to invent both, and a record of an
     * invention is worse than no record. What it posted and never saw again settles with its owner
     * when the movement ends, which is the last thing the station honestly knows.
     *
     * <p>Owner steps are not gone from the model: a body that runs on this instance can answer for
     * itself, and its own flow is where those steps belong. A station that wants to track the
     * owner's leg anyway can add the steps to its own flow and they will read as asserted.
     */
    private static final List<Preset> PRESETS = List.of(
            new Preset(
                    "Tausch (Eigentum der Wache)",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.STATION,
                    List.of(
                            new PresetStep(
                                    "Tausch angekündigt",
                                    StepActor.MEMBER,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER,
                                    false),
                            new PresetStep(
                                    "Altes Teil zurückgenommen",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_OWNER,
                                    false),
                            new PresetStep(
                                    "Ersatz ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER,
                                    true))),
            new Preset(
                    "Tausch (Eigentum des Trägers)",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.CLUSTER,
                    List.of(
                            new PresetStep(
                                    "Tausch angekündigt",
                                    StepActor.MEMBER,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER,
                                    false),
                            new PresetStep(
                                    "Altes Teil zurückgenommen",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION,
                                    false),
                            new PresetStep(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT,
                                    false),
                            new PresetStep(
                                    "Ersatz erhalten",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION,
                                    true),
                            new PresetStep(
                                    "Ersatz ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER,
                                    false))),
            new Preset(
                    "Rückgabe an den Träger",
                    MovementPurpose.RETURN,
                    ItemOwner.CLUSTER,
                    List.of(
                            new PresetStep(
                                    "Rückgabe angekündigt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION,
                                    false),
                            new PresetStep(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT,
                                    false))),
            new Preset(
                    "Ausgabe durch den Träger",
                    MovementPurpose.ISSUE,
                    ItemOwner.CLUSTER,
                    List.of(new PresetStep(
                            "Vom Träger erhalten",
                            StepActor.STATION,
                            StepSubject.INCOMING,
                            ItemCustody.AT_STATION,
                            true))));

    private final MovementFlowRepository flowRepository;
    private final ItemMovementRepository movementRepository;

    @Inject
    public MovementFlowService(MovementFlowRepository flowRepository, ItemMovementRepository movementRepository) {
        this.flowRepository = flowRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * Gives a station the presets and their bindings if it has none. A station that has edited its
     * flows is left alone, including one that deleted the lot on purpose.
     *
     * @param stationId the station
     */
    public void ensurePresets(int stationId) {
        if (flowRepository.hasAnyFlow(stationId)) return;
        for (Preset preset : PRESETS) {
            MovementFlow flow = flowRepository.createFlow(stationId, preset.name(), preset.purpose());
            int position = 0;
            for (PresetStep step : preset.steps()) {
                flowRepository.createStep(
                        flow.id(),
                        position++,
                        step.label(),
                        step.actor(),
                        step.subject(),
                        step.custodyAfter(),
                        step.picksItem());
            }
            flowRepository.bind(stationId, null, preset.ownerKind(), preset.purpose(), flow.id());
        }
        log.info("Seeded {} movement flow presets for station {}", PRESETS.size(), stationId);
    }

    public List<MovementFlow> findFlows(int stationId) {
        ensurePresets(stationId);
        return flowRepository.findFlowsByStation(stationId);
    }

    public Optional<MovementFlow> findFlow(int flowId) {
        return flowRepository.findFlowById(flowId);
    }

    public List<MovementFlowStep> findActiveSteps(int flowId) {
        return flowRepository.findActiveSteps(flowId);
    }

    public List<MovementFlowStep> findAllSteps(int flowId) {
        return flowRepository.findAllSteps(flowId);
    }

    public List<MovementFlowBinding> findBindings(int stationId) {
        ensurePresets(stationId);
        return flowRepository.findBindings(stationId);
    }

    /**
     * Which flow a movement walks, resolved once at creation and pinned on it.
     *
     * <p>The station's own binding is the answer in every case but one: gear owned by a body that
     * runs on this instance <em>and</em> keeps its inventory here walks that body's flow, because an
     * owner that is present sets its own terms. Both halves of that condition matter. A body that is
     * on the instance but does not use the inventory module has nobody to acknowledge anything, so
     * its stations behave exactly as if there were no body above them at all, and fall through to
     * the station flows below, which carry no owner steps for precisely that reason.
     *
     * <p>Neither half can be true yet: nothing on this instance can own gear, so every movement
     * falls through today. When that changes, this is the only method that has to learn about it.
     *
     * @param stationId   the station running the movement
     * @param inventoryId the inventory the movement is about
     * @param ownerKind   who owns the item
     * @param purpose     what the movement is for
     * @return the flow to walk
     * @throws BadRequestResponse when the station has no flow bound for that pair
     */
    public int resolveFlow(int stationId, Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose) {
        ensurePresets(stationId);
        return flowRepository
                .findBoundFlow(stationId, inventoryId, ownerKind, purpose)
                .orElseThrow(() -> new BadRequestResponse(
                        "No flow is bound for %s gear and %s at this station".formatted(ownerKind, purpose)));
    }

    public MovementFlow createFlow(int stationId, String name, MovementPurpose purpose) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A flow needs a name");
        // Before this one, so that writing a flow of your own is not what stops the presets arriving
        ensurePresets(stationId);
        MovementFlow flow = flowRepository.createFlow(stationId, name, purpose);
        log.info("Created movement flow {} ('{}', {}) for station {}", flow.id(), name, purpose, stationId);
        return flow;
    }

    public boolean renameFlow(int flowId, String name) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A flow needs a name");
        return flowRepository.renameFlow(flowId, name);
    }

    public boolean archiveFlow(int flowId) {
        requireNoOpenMovement(flowId);
        return flowRepository.archiveFlow(flowId);
    }

    /**
     * Adds a step at the end of a flow.
     *
     * @throws BadRequestResponse when a movement is still walking the flow, or when the step would
     *                            be a second one naming the replacement
     */
    public MovementFlowStep addStep(
            int flowId,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {
        requireNoOpenMovement(flowId);
        requireLabel(label);
        requireStepCustody(custodyAfter);
        if (picksItem) requirePicksItemFree(flowId, subject, null);
        return flowRepository.createStep(
                flowId, flowRepository.nextStepPosition(flowId), label, actor, subject, custodyAfter, picksItem);
    }

    /**
     * Changes what a step says. Renaming alone is always allowed, because the behaviour hangs off
     * the custody and the subject rather than the words.
     */
    public boolean updateStep(
            int stepId,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {
        MovementFlowStep step =
                flowRepository.findStepById(stepId).orElseThrow(() -> new BadRequestResponse("No such step"));
        requireLabel(label);
        requireStepCustody(custodyAfter);
        boolean behaviourChanges = step.actor() != actor
                || step.subject() != subject
                || step.custodyAfter() != custodyAfter
                || step.picksItem() != picksItem;
        if (behaviourChanges) requireNoOpenMovement(step.flowId());
        if (picksItem) requirePicksItemFree(step.flowId(), subject, stepId);
        return flowRepository.updateStep(stepId, label, actor, subject, custodyAfter, picksItem);
    }

    /**
     * Retires a step rather than deleting it, so the movements that already passed it still read the
     * way they were walked.
     */
    public boolean archiveStep(int stepId) {
        MovementFlowStep step =
                flowRepository.findStepById(stepId).orElseThrow(() -> new BadRequestResponse("No such step"));
        requireNoOpenMovement(step.flowId());
        return flowRepository.archiveStep(stepId);
    }

    public void bind(int stationId, Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, int flowId) {
        MovementFlow flow =
                flowRepository.findFlowById(flowId).orElseThrow(() -> new BadRequestResponse("No such flow"));
        if (flow.stationId() == null || flow.stationId() != stationId) {
            throw new BadRequestResponse("That flow belongs to somebody else");
        }
        if (flow.purpose() != purpose) {
            throw new BadRequestResponse("That flow is for %s, not %s".formatted(flow.purpose(), purpose));
        }
        flowRepository.bind(stationId, inventoryId, ownerKind, purpose, flowId);
    }

    private void requireNoOpenMovement(int flowId) {
        if (movementRepository.hasOpenMovementOnFlow(flowId)) {
            throw new BadRequestResponse("A movement is still walking this flow, so its steps cannot change");
        }
    }

    private void requireLabel(String label) {
        if (label == null || label.isBlank()) throw new BadRequestResponse("A step needs a label");
    }

    private void requireStepCustody(ItemCustody custodyAfter) {
        if (!ItemMovementService.legalStepCustody(custodyAfter)) {
            throw new BadRequestResponse("A step cannot leave an item %s".formatted(custodyAfter));
        }
    }

    /**
     * At most one incoming step per flow names the replacement, because two would mean two answers
     * to which item arrived.
     */
    private void requirePicksItemFree(int flowId, StepSubject subject, Integer exceptStepId) {
        if (subject != StepSubject.INCOMING) {
            throw new BadRequestResponse("Only a step about the arriving item can name it");
        }
        boolean taken = flowRepository.findAllSteps(flowId).stream()
                .filter(s -> exceptStepId == null || s.id() != exceptStepId)
                .anyMatch(s -> s.picksItem() && !s.archived());
        if (taken) throw new BadRequestResponse("Another step of this flow already names the replacement");
    }

    private record Preset(String name, MovementPurpose purpose, ItemOwner ownerKind, List<PresetStep> steps) {}

    private record PresetStep(
            String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter, boolean picksItem) {}
}
