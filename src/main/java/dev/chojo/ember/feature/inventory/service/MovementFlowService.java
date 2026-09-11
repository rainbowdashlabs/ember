/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.FlowProblem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowBinding;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository.OpenMovementOnFlow;
import dev.chojo.ember.feature.inventory.repository.MovementFlowRepository;
import dev.chojo.ember.util.sql.Transactions;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
     * <p>Every chain opens with a request at the place that wants something and closes with a
     * receipt at the place the gear ends up. The receipt is the only confirmation that counts:
     * whoever ends up holding it says so, rather than somebody saying it for them. Two steps is
     * therefore the shortest a chain can be, and {@link MovementFlowValidation} keeps it that way
     * for the ones a station writes itself.
     *
     * <p>Where a chain names the body above the station, that step belongs to it. A body that does
     * not run on this instance cannot press anything, so the station walks those steps in its place
     * and the record says asserted rather than confirmed. That is the same chain either way, which
     * is what makes the two cases comparable afterwards.
     *
     * <p>A piece that arrives is named where it sets out rather than where it lands: the step that
     * takes a replacement off the shelf or puts it in the post is the one that says which piece it
     * is. Naming it on arrival would leave the steps in between about a piece nobody had chosen yet.
     */
    private static final List<Preset> PRESETS = List.of(
            new Preset(
                    "Ausgabe durch den Träger ins Lager",
                    MovementPurpose.ISSUE,
                    ItemOwner.CLUSTER,
                    MovementParty.STORE,
                    List.of(
                            PresetStep.of("Bestellt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION))),
            new Preset(
                    "Ausgabe durch den Träger an ein Mitglied",
                    MovementPurpose.ISSUE,
                    ItemOwner.CLUSTER,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of("Bestellt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "An der Wache angekommen",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An das Mitglied ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))),
            new Preset(
                    "Ausgabe eigener Ausrüstung an ein Mitglied",
                    MovementPurpose.ISSUE,
                    ItemOwner.STATION,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of(
                                    "Angefordert", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Ausgegeben", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))),
            new Preset(
                    "Rückgabe an den Träger aus dem Lager",
                    MovementPurpose.RETURN,
                    ItemOwner.CLUSTER,
                    MovementParty.STORE,
                    List.of(
                            PresetStep.of(
                                    "Rückgabe angekündigt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER))),
            new Preset(
                    "Rückgabe an den Träger vom Mitglied",
                    MovementPurpose.RETURN,
                    ItemOwner.CLUSTER,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of(
                                    "Rückgabe angefordert",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Bei der Wache abgegeben",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER))),
            new Preset(
                    "Rückgabe an die Wache vom Mitglied",
                    MovementPurpose.RETURN,
                    ItemOwner.STATION,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of(
                                    "Rückgabe angefordert",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.WITH_OWNER))),
            new Preset(
                    "Tausch eigener Ausrüstung",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.STATION,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of(
                                    "Tausch angefordert",
                                    StepActor.MEMBER,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Altes Teil zurückgenommen",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Ersatz bereit", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "Ersatz ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))),
            new Preset(
                    "Tausch von Ausrüstung des Trägers",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.CLUSTER,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of(
                                    "Tausch angefordert",
                                    StepActor.MEMBER,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Altes Teil zurückgenommen",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Ersatz verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "An der Wache angekommen",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "Ersatz ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))),
            new Preset(
                    "Tausch von Ausrüstung des Trägers aus dem Lager",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.CLUSTER,
                    MovementParty.STORE,
                    List.of(
                            PresetStep.of(
                                    "Tausch angefordert",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An den Träger geschickt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Ersatz verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "Im Lager angekommen",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION))),
            new Preset(
                    "Anfrage an den Träger fürs Lager",
                    MovementPurpose.REQUEST,
                    ItemOwner.CLUSTER,
                    MovementParty.STORE,
                    List.of(
                            PresetStep.of("Angefragt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Zugesagt und verschickt",
                                    StepActor.OWNER,
                                    StepSubject.INCOMING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION))),
            new Preset(
                    "Anfrage an den Träger für ein Mitglied",
                    MovementPurpose.REQUEST,
                    ItemOwner.CLUSTER,
                    MovementParty.MEMBER,
                    List.of(
                            PresetStep.of("Angefragt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Zugesagt und verschickt",
                                    StepActor.OWNER,
                                    StepSubject.INCOMING,
                                    ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "An der Wache angekommen",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "An das Mitglied ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))));

    /**
     * The chains an association starts with, one per purpose and named in a word.
     *
     * <p>Written from the association's side: its store sends, its store receives, and the steps the
     * station walks are the station's. An association that wants something else edits or removes them
     * like any other chain, which is why this is a starting point and not a fixture.
     *
     * <p>An association's chain needs no binding row: a chain is found by the association and the purpose,
     * because its gear is one pool rather than several inventories with different habits.
     */
    private static final List<Preset> CLUSTER_PRESETS = List.of(
            new Preset(
                    "Ausgabe",
                    MovementPurpose.ISSUE,
                    ItemOwner.CLUSTER,
                    List.of(
                            PresetStep.of("Bestellt", StepActor.STATION, StepSubject.INCOMING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "Erhalten", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION))),
            new Preset(
                    "Rückgabe",
                    MovementPurpose.RETURN,
                    ItemOwner.CLUSTER,
                    List.of(
                            PresetStep.of(
                                    "Rückgabe angekündigt",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "Abgeschickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER))),
            new Preset(
                    "Tausch",
                    MovementPurpose.EXCHANGE,
                    ItemOwner.CLUSTER,
                    List.of(
                            PresetStep.of(
                                    "Tausch angefordert",
                                    StepActor.MEMBER,
                                    StepSubject.OUTGOING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Altes Teil zurückgenommen",
                                    StepActor.STATION,
                                    StepSubject.OUTGOING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "Abgeschickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT),
                            PresetStep.of("Erhalten", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER),
                            PresetStep.naming(
                                    "Ersatz verschickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT),
                            PresetStep.of(
                                    "Ersatz angekommen",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION),
                            PresetStep.of(
                                    "Ersatz ausgegeben",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_MEMBER),
                            PresetStep.of(
                                    "Erhalten", StepActor.MEMBER, StepSubject.INCOMING, ItemCustody.WITH_MEMBER))),
            new Preset(
                    "Anfrage",
                    MovementPurpose.REQUEST,
                    ItemOwner.CLUSTER,
                    List.of(
                            new PresetStep(
                                    "Angefragt",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.WITH_OWNER,
                                    false),
                            new PresetStep(
                                    "Zugesagt und verschickt",
                                    StepActor.OWNER,
                                    StepSubject.INCOMING,
                                    ItemCustody.IN_TRANSIT,
                                    true),
                            new PresetStep(
                                    "Erhalten",
                                    StepActor.STATION,
                                    StepSubject.INCOMING,
                                    ItemCustody.AT_STATION,
                                    false))));

    private final MovementFlowRepository flowRepository;
    private final ItemMovementRepository movementRepository;
    private final ClusterRepository clusterRepository;

    @Inject
    public MovementFlowService(
            MovementFlowRepository flowRepository,
            ItemMovementRepository movementRepository,
            ClusterRepository clusterRepository) {
        this.flowRepository = flowRepository;
        this.movementRepository = movementRepository;
        this.clusterRepository = clusterRepository;
    }

    /**
     * Gives a station the presets and their bindings if it has none. A station that has edited its
     * flows is left alone, including one that deleted the lot on purpose.
     *
     * @param stationId the station
     */
    /**
     * Lays out the chains a station starts with, and fills in the ones it is still missing.
     *
     * <p>Missing rather than absent: a station that wrote its own chain for one combination keeps it
     * and gets the presets for the combinations nothing covers yet. Checking "has any chain at all"
     * was enough while there were four of them and they all arrived together. It is not enough now
     * that there are ten, because a station seeded with the old four would never see the six that
     * came later and every movement of those kinds would answer that no chain is bound.
     *
     * @param stationId the station
     */
    public void ensurePresets(int stationId) {
        var bound = flowRepository.findBindings(stationId).stream()
                .map(binding -> combinationOf(binding.ownerKind(), binding.purpose(), binding.party()))
                .collect(Collectors.toSet());

        int added = 0;
        for (Preset preset : PRESETS) {
            if (bound.contains(combinationOf(preset.ownerKind(), preset.purpose(), preset.party()))) continue;
            if (writePreset(stationId, preset)) added++;
        }
        if (added > 0) log.info("Seeded {} movement flow preset(s) for station {}", added, stationId);
    }

    /**
     * Writes one preset and binds it, unless somebody bound that combination first.
     *
     * <p>The flows page asks for the chains and the bindings at once, and both answers seed. Each
     * request read the same combination as unbound and both wrote it, so whichever arrived second
     * ended in an error the reader could do nothing about and the page would not load at all. The
     * binding decides who won, and the chain of the request that lost is taken away again rather
     * than left behind as a second copy nothing points at.
     *
     * @return whether this call is the one that wrote it
     */
    private boolean writePreset(int stationId, Preset preset) {
        MovementFlow flow = flowRepository.createFlow(stationId, preset.name(), preset.purpose());
        writeSteps(flow.id(), preset.steps(), 0);
        if (flowRepository.bindIfAbsent(
                stationId, null, preset.ownerKind(), preset.purpose(), preset.party(), flow.id())) {
            return true;
        }
        flowRepository.deleteFlow(flow.id());
        return false;
    }

    /**
     * Writes a run of steps onto a chain, in the order they are to be walked.
     *
     * @param flowId the chain
     * @param steps  what it is to say
     * @param from   the position the first of them takes, which is not zero where the chain already
     *               holds steps that keep their places
     * @return the steps as they were written, in the same order
     */
    private List<MovementFlowStep> writeSteps(int flowId, List<PresetStep> steps, int from) {
        var written = new ArrayList<MovementFlowStep>();
        int position = from;
        for (PresetStep step : steps) {
            written.add(flowRepository.createStep(
                    flowId,
                    position++,
                    step.label(),
                    step.actor(),
                    step.subject(),
                    step.custodyAfter(),
                    step.picksItem()));
        }
        return written;
    }

    private static String combinationOf(ItemOwner ownerKind, MovementPurpose purpose, MovementParty party) {
        return "%s|%s|%s".formatted(ownerKind, purpose, party);
    }

    /**
     * What writing a chain again would come to, worked out without writing anything.
     *
     * <p>A restore takes the chain apart and puts another one in its place, so what happens to the
     * movements standing on it is decided before it happens rather than reported afterwards. The plan
     * says what the preset would write and, for every movement on the chain, where it would land and
     * whether that is the preset's answer or a question for somebody.
     *
     * <p>The same three refusals as the restore itself, and in the same order: a plan that answered
     * where the restore would refuse would be a plan for something that cannot be done.
     *
     * @param flowId the chain that would be written again
     * @return the chain the preset writes and where each open movement would land
     * @throws BadRequestResponse when the chain belongs to the body above the station, when nothing
     *                            is bound to it, or when no preset covers the combination it serves
     */
    public RestorePlan planRestore(int flowId) {
        Preset preset = presetFor(restorableFlow(flowId));
        var steps = IntStream.range(0, preset.steps().size())
                .mapToObj(index -> plannedStep(index, preset.steps().get(index)))
                .toList();
        var landings = standingOn(flowId).entrySet().stream()
                .map(group -> plannedLanding(group.getKey(), group.getValue().size(), preset.steps()))
                .toList();
        return new RestorePlan(steps, landings);
    }

    /**
     * Writes a chain again as the preset for its combination says it goes.
     *
     * <p>The seeding only writes combinations nothing has bound yet, so a station keeps the chain it
     * was given for good: an edit has no way back, and a preset that changes afterwards never reaches
     * a station that already has one. This is both of those ways round.
     *
     * <p>The binding stays exactly where it is. Only the steps are written again, which is what makes
     * this a repair of one chain rather than a chain swapped for another one: everything that points
     * at this chain still points at it afterwards.
     *
     * <p>Nothing is written until every movement standing on the chain has somewhere to stand in the
     * one that replaces it. Where the preset says on its own where a movement lands, it lands there
     * and nobody is asked. Where it does not, the chosen landings have to say so, and a movement
     * neither answered for nor chosen refuses the whole thing and says which one it was.
     *
     * @param flowId        the chain to write again
     * @param actorMemberId who asked for it, which the carried movements' log entries name
     * @param chosen        where the movements the preset does not answer for are to land, which may
     *                      be empty when it answers for all of them
     * @throws BadRequestResponse when the chain belongs to the body above the station, when nothing
     *                            is bound to it, when no preset covers the combination it serves, or
     *                            when a movement on it is left without a landing
     */
    public void restoreToPreset(int flowId, Integer actorMemberId, List<ChosenLanding> chosen) {
        Preset preset = presetFor(restorableFlow(flowId));
        var previous = flowRepository.findAllSteps(flowId);
        var carried = carriesOnto(flowId, preset.steps(), chosen);

        Transactions.run(() -> {
            var replacements = writeSteps(flowId, preset.steps(), flowRepository.nextStepPosition(flowId));
            for (Carry carry : carried) {
                MovementFlowStep landing = replacements.get(carry.landing());
                movementRepository.moveToStep(carry.movementId(), landing.id());
                movementRepository.createLog(
                        carry.movementId(),
                        landing.id(),
                        landing.label(),
                        AckKind.CORRECTED,
                        actorMemberId,
                        noteOf(carry, landing));
                log.info(
                        "Movement {} was carried from step '{}' onto step {} ('{}') of the rewritten flow {}",
                        carry.movementId(),
                        carry.from(),
                        landing.id(),
                        landing.label(),
                        flowId);
            }
            takePreviousStepsAway(previous);
            countFromTheFrontAgain(flowId, replacements);
        });
        log.info("Movement flow {} was written again from the preset '{}'", flowId, preset.name());
    }

    /**
     * The chain, once it is established that it is the station's to write again at all.
     *
     * <p>The chain of the body above the station is shown at a station and written where it belongs,
     * which is not here.
     */
    private MovementFlow restorableFlow(int flowId) {
        MovementFlow flow =
                flowRepository.findFlowById(flowId).orElseThrow(() -> new BadRequestResponse("No such flow"));
        if (flow.stationId() == null) {
            throw new BadRequestResponse(
                    "That chain belongs to the body above the station, which is not the station's to write again");
        }
        return flow;
    }

    /** What a carried movement's log says about the chain having changed underneath it. */
    private static String noteOf(Carry carry, MovementFlowStep landing) {
        if (carry.from() == null) {
            return "Ablauf auf die Vorlage zurückgesetzt, der Schritt heißt jetzt '%s'".formatted(landing.label());
        }
        return "Ablauf auf die Vorlage zurückgesetzt, aus '%s' wurde '%s'".formatted(carry.from(), landing.label());
    }

    /**
     * The preset a chain is to be written from, which is the one for the combination it serves.
     *
     * <p>Found by what the chain is bound to rather than by what it is called, because a name is the
     * station's to change and a combination is not.
     */
    private Preset presetFor(MovementFlow flow) {
        MovementFlowBinding binding = flowRepository.findBindings(flow.stationId()).stream()
                .filter(candidate -> candidate.flowId() == flow.id())
                .findFirst()
                .orElseThrow(() -> new BadRequestResponse(
                        "Nothing is bound to that chain, so there is no preset it could be written from"));
        String combination = combinationOf(binding.ownerKind(), binding.purpose(), binding.party());
        return PRESETS.stream()
                .filter(preset -> combinationOf(preset.ownerKind(), preset.purpose(), preset.party())
                        .equals(combination))
                .findFirst()
                .orElseThrow(() -> new BadRequestResponse(
                        "No preset covers %s gear, %s and %s, so that chain has none to be written from"
                                .formatted(binding.ownerKind(), binding.purpose(), binding.party())));
    }

    /**
     * Where every movement on the chain lands, worked out before anything is written.
     *
     * <p>All of them or none: a movement left without a landing is refused here, where the chain is
     * still the one it was started on.
     */
    private List<Carry> carriesOnto(int flowId, List<PresetStep> replacements, List<ChosenLanding> chosen) {
        var gathered = standingOn(flowId);
        var choices = choicesOf(gathered.keySet(), replacements, chosen);
        var carries = new ArrayList<Carry>();
        for (var group : gathered.entrySet()) {
            MovementFlowStep standing = group.getKey();
            OptionalInt certain = certainLandingFor(standing, replacements);
            Integer landing = choices.get(standing == null ? null : standing.id());
            if (landing == null && certain.isPresent()) landing = certain.getAsInt();
            if (landing == null) {
                throw new BadRequestResponse(
                        "The movements on %s have nowhere to stand in the new chain, so the restore has to be told where they land"
                                .formatted(whereItStands(standing)));
            }
            for (OpenMovementOnFlow movement : group.getValue()) {
                carries.add(new Carry(movement.id(), standing == null ? null : standing.label(), landing));
            }
        }
        return carries;
    }

    /**
     * The landings somebody chose, once it is established that each of them is one this restore can
     * carry out.
     *
     * <p>An entry naming a movement that is not on this chain, or a step the preset does not write, is
     * a choice made about something else, and a restore that quietly ignored it would put the movement
     * somewhere nobody asked for.
     */
    private static Map<Integer, Integer> choicesOf(
            Set<MovementFlowStep> standing, List<PresetStep> replacements, List<ChosenLanding> chosen) {
        var occupied = standing.stream()
                .map(step -> step == null ? null : step.id())
                .collect(Collectors.toCollection(HashSet::new));
        var choices = new LinkedHashMap<Integer, Integer>();
        for (ChosenLanding choice : chosen) {
            if (choice.stepIndex() == null) {
                throw new BadRequestResponse("Every landing names the step it is to land on");
            }
            if (!occupied.contains(choice.stepId())) {
                throw new BadRequestResponse("No movement of this chain stands on step %s, so it has no landing here"
                        .formatted(choice.stepId()));
            }
            if (choice.stepIndex() < 0 || choice.stepIndex() >= replacements.size()) {
                throw new BadRequestResponse("The preset writes %d steps, so step %s cannot land on step %d"
                        .formatted(replacements.size(), choice.stepId(), choice.stepIndex()));
            }
            if (choices.put(choice.stepId(), choice.stepIndex()) != null) {
                throw new BadRequestResponse("Step %s is given two landings, and its movements can only stand on one"
                        .formatted(choice.stepId()));
            }
        }
        return choices;
    }

    /**
     * The step of the new chain that certainly means what the old one meant.
     *
     * <p>One rule and no fallbacks: the same piece left in the same place. Where the preset says that
     * once, it is what the old step meant and the movement lands there without anybody being asked.
     * Where it says it twice, or not at all, the answer is not the chain's to give: the four rules
     * that used to work down to one always produced an answer, and an answer nobody chose is how a
     * movement ends up three steps further on than anyone noticed.
     *
     * @return where in the new chain the movement certainly lands, or empty when that is a question
     */
    private static OptionalInt certainLandingFor(MovementFlowStep standing, List<PresetStep> replacements) {
        if (standing == null) return OptionalInt.empty();
        var candidates = IntStream.range(0, replacements.size())
                .filter(index -> replacements.get(index).subject() == standing.subject()
                        && replacements.get(index).custodyAfter() == standing.custodyAfter())
                .boxed()
                .toList();
        return candidates.size() == 1 ? OptionalInt.of(candidates.getFirst()) : OptionalInt.empty();
    }

    /** The step a movement stands on, or {@code null} where the step it stood on has gone. */
    private MovementFlowStep standingStepOf(OpenMovementOnFlow movement) {
        return movement.currentStepId() == null
                ? null
                : flowRepository.findStepById(movement.currentStepId()).orElse(null);
    }

    /** Where a movement stands, said the way a refusal has to say it. */
    private static String whereItStands(MovementFlowStep standing) {
        return standing == null ? "no step of this chain" : "'%s'".formatted(standing.label());
    }

    private PlannedStep plannedStep(int index, PresetStep step) {
        return new PlannedStep(
                index, step.label(), step.actor(), step.subject(), step.custodyAfter(), step.picksItem());
    }

    /**
     * One movement as the plan shows it: what it is, where it stands, and where it would go.
     *
     * <p>A landing that is not certain is offered empty rather than filled in with the likeliest
     * candidate, so choosing it is something somebody does rather than something they fail to notice.
     */
    /**
     * The movements still walking a chain, gathered under the step each of them stands on.
     *
     * <p>Gathered rather than listed one by one, because where a movement lands does not depend on
     * the movement. It depends on the step it stands on, and every movement on one step lands on the
     * same step of the new chain. Asked movement by movement, somebody would answer the same question
     * once per movement and the answers could not differ.
     *
     * <p>The step a movement no longer has is a group of its own, under no step at all.
     */
    private Map<MovementFlowStep, List<OpenMovementOnFlow>> standingOn(int flowId) {
        var gathered = new LinkedHashMap<MovementFlowStep, List<OpenMovementOnFlow>>();
        for (OpenMovementOnFlow movement : movementRepository.findOpenOnFlow(flowId)) {
            gathered.computeIfAbsent(standingStepOf(movement), step -> new ArrayList<>())
                    .add(movement);
        }
        return gathered;
    }

    private PlannedLanding plannedLanding(MovementFlowStep standing, int movements, List<PresetStep> replacements) {
        OptionalInt certain = certainLandingFor(standing, replacements);
        return new PlannedLanding(
                standing == null ? null : standing.id(),
                standing == null ? null : standing.label(),
                movements,
                certain.isPresent() ? certain.getAsInt() : null,
                certain.isPresent());
    }

    /**
     * Takes the chain that was there away, history and all.
     *
     * <p>Every step goes: the ones movements walked, and the ones already stilled before this. What a
     * movement was walked through is written in its own log rather than read back off the chain, an
     * entry carrying the words the step had at the time and letting go of the step itself once it is
     * gone. Keeping them instead left a restored chain carrying its predecessor as a row of stilled
     * steps, and the row grew by one chain every time somebody put the chain back.
     */
    private void takePreviousStepsAway(List<MovementFlowStep> previous) {
        for (MovementFlowStep step : previous) {
            flowRepository.deleteStep(step.id());
        }
    }

    /**
     * Numbers the chain from one again.
     *
     * <p>The steps are written behind the ones that are still there, because a position belongs to
     * one step of a chain at a time and the old ones are only taken away once the movements on them
     * have somewhere else to stand. That leaves the new chain starting wherever the old one ended,
     * so a chain put back for the third time opened at step seventeen. Nothing reads the numbers but
     * a person, and a person reads them from the front.
     */
    private void countFromTheFrontAgain(int flowId, List<MovementFlowStep> steps) {
        flowRepository.applyStepOrder(
                flowId,
                steps.stream().map(MovementFlowStep::id).toList(),
                IntStream.range(0, steps.size()).boxed().toList());
    }

    public List<MovementFlow> findFlows(int stationId) {
        ensurePresets(stationId);
        return flowRepository.findFlowsByStation(stationId);
    }

    public Optional<MovementFlow> findFlow(int flowId) {
        return flowRepository.findFlowById(flowId);
    }

    public Optional<MovementFlowStep> findStep(int stepId) {
        return flowRepository.findStepById(stepId);
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
     * @param stationId   the station running the movement
     * @param inventoryId the inventory the movement is about
     * @param ownerKind   who owns the item
     * @param ownerId     the owning cluster when {@code ownerKind} is CLUSTER, otherwise {@code null}
     * @param purpose     what the movement is for
     * @return the flow to walk
     * @throws BadRequestResponse when the station has no flow bound for that pair
     */
    public int resolveFlow(
            int stationId,
            Integer inventoryId,
            ItemOwner ownerKind,
            Integer ownerId,
            MovementPurpose purpose,
            MovementParty party) {
        ensurePresets(stationId);
        if (ownerKind == ItemOwner.CLUSTER && ownerId != null) {
            Integer clusterFlow = clusterOwnedFlow(ownerId, purpose);
            if (clusterFlow != null) return clusterFlow;
        }
        return flowRepository
                .findBoundFlow(stationId, inventoryId, ownerKind, purpose, party)
                .orElseThrow(() -> new BadRequestResponse("No flow is bound for %s gear, %s and %s at this station"
                        .formatted(ownerKind, purpose, party)));
    }

    /**
     * The owner's own flow, when the owner is in a position to walk one.
     *
     * <p>Both halves of the condition are checked here and nowhere else. A cluster that is not on this
     * instance has no row to find; a cluster that is here but does not keep its gear here has nobody to
     * press its buttons, and a chain stopping on a step nobody will ever answer is worse than no chain of
     * the owner's at all.
     *
     * <p>This is what the association's settings screen is for, and until the gear said which body
     * owned it nothing ever reached this at all: a station recording a piece as somebody else's could
     * not say whose, so every such piece looked like one belonging to a body outside Ember and every
     * station walked its own chain. The station's own chains name the owner's steps too, and are what
     * a station falls back to when the body above it is not here to press them.
     *
     * @param clusterId the owning cluster
     * @param purpose   what the movement is for
     * @return the cluster's flow, or {@code null} to fall through to the station's own
     */
    private Integer clusterOwnedFlow(int clusterId, MovementPurpose purpose) {
        return clusterRepository
                .findById(clusterId)
                .filter(Cluster::usesInventory)
                .flatMap(cluster -> flowRepository.findClusterBoundFlow(cluster.id(), purpose))
                .orElse(null);
    }

    /**
     * Gives an association the ready-made chains if it has none, so nobody opens an empty screen and
     * invents five of them. An association that has written or deleted its own is left alone.
     *
     * @param clusterId the association
     */
    public void ensureClusterPresets(int clusterId) {
        if (!flowRepository.findClusterFlows(clusterId).isEmpty()) return;
        for (Preset preset : CLUSTER_PRESETS) {
            MovementFlow flow = flowRepository.createClusterFlow(clusterId, preset.name(), preset.purpose());
            writeSteps(flow.id(), preset.steps(), 0);
        }
        log.info("Seeded {} movement flow presets for cluster {}", CLUSTER_PRESETS.size(), clusterId);
    }

    /**
     * The flows a cluster keeps for its own gear.
     *
     * @param clusterId the cluster
     * @return its flows
     */
    public List<MovementFlow> findClusterFlows(int clusterId) {
        return flowRepository.findClusterFlows(clusterId);
    }

    /**
     * Creates a flow the cluster owns.
     *
     * @param clusterId the cluster
     * @param name      what it is called
     * @param purpose   what it is for
     * @return the flow
     */
    public MovementFlow createClusterFlow(int clusterId, String name, MovementPurpose purpose) {
        if (name == null || name.isBlank()) throw new FlowRefusedException(FlowProblem.Code.FLOW_NAME_REQUIRED);
        MovementFlow flow = flowRepository.createClusterFlow(clusterId, name.trim(), purpose);
        log.info("Created movement flow {} ('{}', {}) for cluster {}", flow.id(), name, purpose, clusterId);
        return flow;
    }

    public MovementFlow createFlow(int stationId, String name, MovementPurpose purpose) {
        if (name == null || name.isBlank()) throw new FlowRefusedException(FlowProblem.Code.FLOW_NAME_REQUIRED);
        // Before this one, so that writing a flow of your own is not what stops the presets arriving
        ensurePresets(stationId);
        MovementFlow flow = flowRepository.createFlow(stationId, name, purpose);
        log.info("Created movement flow {} ('{}', {}) for station {}", flow.id(), name, purpose, stationId);
        return flow;
    }

    public boolean renameFlow(int flowId, String name) {
        if (name == null || name.isBlank()) throw new FlowRefusedException(FlowProblem.Code.FLOW_NAME_REQUIRED);
        boolean renamed = flowRepository.renameFlow(flowId, name);
        if (renamed) log.info("Movement flow {} is now called '{}'", flowId, name);
        else log.warn("Rename for movement flow {} affected zero rows", flowId);
        return renamed;
    }

    public boolean archiveFlow(int flowId) {
        requireNoOpenMovement(flowId);
        boolean archived = flowRepository.archiveFlow(flowId);
        if (archived) log.info("Retired movement flow {}", flowId);
        else log.warn("Archive for movement flow {} affected zero rows", flowId);
        return archived;
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
        MovementFlowStep step = flowRepository.createStep(
                flowId, flowRepository.nextStepPosition(flowId), label, actor, subject, custodyAfter, picksItem);
        log.info(
                "Added step {} ('{}', {} on {} leaving it {}) to movement flow {}",
                step.id(),
                label,
                actor,
                subject,
                custodyAfter,
                flowId);
        return step;
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
        boolean updated = flowRepository.updateStep(stepId, label, actor, subject, custodyAfter, picksItem);
        if (updated) {
            log.info(
                    "Movement flow step {} now reads '{}' ({} on {} leaving it {}){}",
                    stepId,
                    label,
                    actor,
                    subject,
                    custodyAfter,
                    behaviourChanges ? ", which changes how the flow is walked" : "");
        } else {
            log.warn("Update for movement flow step {} affected zero rows", stepId);
        }
        return updated;
    }

    /**
     * Retires a step rather than deleting it, so the movements that already passed it still read the
     * way they were walked.
     */
    public boolean archiveStep(int stepId) {
        MovementFlowStep step =
                flowRepository.findStepById(stepId).orElseThrow(() -> new BadRequestResponse("No such step"));
        requireNoOpenMovement(step.flowId());
        if (flowRepository.isBound(step.flowId())) {
            var remaining = flowRepository.findActiveSteps(step.flowId()).stream()
                    .filter(other -> other.id() != stepId)
                    .toList();
            MovementFlowValidation.requireWalkable(purposeOf(step.flowId()), remaining);
        }
        boolean archived = flowRepository.archiveStep(stepId);
        if (archived) log.info("Retired step {} of movement flow {}", stepId, step.flowId());
        else log.warn("Archive for movement flow step {} affected zero rows", stepId);
        return archived;
    }

    /**
     * Puts the steps of a chain in the order given, in one write.
     *
     * <p>Order is the whole of what a chain says: the same four steps in another order are another
     * journey. Until now a step went to the end and stayed there, so a forgotten one in the middle
     * meant writing everything after it again.
     *
     * @param flowId  the chain
     * @param stepIds every active step of the chain, in the order they are to be walked
     * @throws BadRequestResponse when the list is not exactly the chain's active steps, or when a
     *                            movement is walking it right now
     */
    public void reorderSteps(int flowId, List<Integer> stepIds) {
        requireNoOpenMovement(flowId);
        var active = flowRepository.findActiveSteps(flowId);
        var wanted = Set.copyOf(stepIds);
        if (stepIds.size() != wanted.size() || wanted.size() != active.size()) {
            throw new FlowRefusedException(FlowProblem.Code.ORDER_MUST_NAME_EVERY_STEP);
        }
        var known = active.stream().map(MovementFlowStep::id).collect(Collectors.toSet());
        if (!known.equals(wanted)) {
            throw new FlowRefusedException(FlowProblem.Code.ORDER_MUST_NAME_EVERY_STEP);
        }

        var byId = active.stream().collect(Collectors.toMap(MovementFlowStep::id, step -> step));
        var reordered = stepIds.stream().map(byId::get).toList();
        if (flowRepository.isBound(flowId)) {
            MovementFlowValidation.requireWalkable(purposeOf(flowId), reordered);
        }
        var positions = active.stream().map(MovementFlowStep::position).sorted().toList();
        flowRepository.applyStepOrder(flowId, stepIds, positions);
        log.info("Movement flow {} is now walked in the order {}", flowId, stepIds);
    }

    /**
     * What stops this chain from being walked, for the editor to show while it is being written.
     *
     * @param flowId the chain
     * @return the problem, or empty when it can be walked
     */
    public Optional<FlowProblem> problemOf(int flowId) {
        return MovementFlowValidation.problemOf(purposeOf(flowId), flowRepository.findActiveSteps(flowId));
    }

    private MovementPurpose purposeOf(int flowId) {
        return flowRepository
                .findFlowById(flowId)
                .map(MovementFlow::purpose)
                .orElseThrow(() -> new BadRequestResponse("No such flow"));
    }

    public void bind(
            int stationId,
            Integer inventoryId,
            ItemOwner ownerKind,
            MovementPurpose purpose,
            MovementParty party,
            int flowId) {
        MovementFlow flow =
                flowRepository.findFlowById(flowId).orElseThrow(() -> new BadRequestResponse("No such flow"));
        if (flow.stationId() == null || flow.stationId() != stationId) {
            throw new BadRequestResponse("That flow belongs to somebody else");
        }
        if (flow.purpose() != purpose) {
            throw new BadRequestResponse("That flow is for %s, not %s".formatted(flow.purpose(), purpose));
        }
        MovementFlowValidation.requireWalkable(purpose, flowRepository.findActiveSteps(flowId));
        flowRepository.bind(stationId, inventoryId, ownerKind, purpose, party, flowId);
    }

    private void requireNoOpenMovement(int flowId) {
        if (movementRepository.hasOpenMovementOnFlow(flowId)) {
            throw new FlowRefusedException(FlowProblem.Code.FLOW_IN_USE);
        }
    }

    private void requireLabel(String label) {
        if (label == null || label.isBlank()) throw new FlowRefusedException(FlowProblem.Code.STEP_LABEL_REQUIRED);
    }

    private void requireStepCustody(ItemCustody custodyAfter) {
        if (!ItemMovementService.legalStepCustody(custodyAfter)) {
            throw new FlowRefusedException(new FlowProblem(FlowProblem.Code.ILLEGAL_STEP_CUSTODY, custodyAfter.name()));
        }
    }

    /**
     * At most one incoming step per flow names the replacement, because two would mean two answers
     * to which item arrived.
     */
    private void requirePicksItemFree(int flowId, StepSubject subject, Integer exceptStepId) {
        if (subject != StepSubject.INCOMING) {
            throw new FlowRefusedException(FlowProblem.Code.ONLY_ARRIVAL_NAMES_ITEM);
        }
        boolean taken = flowRepository.findAllSteps(flowId).stream()
                .filter(s -> exceptStepId == null || s.id() != exceptStepId)
                .anyMatch(s -> s.picksItem() && !s.archived());
        if (taken) throw new FlowRefusedException(FlowProblem.Code.ITEM_ALREADY_NAMED);
    }

    /**
     * One movement on its way from the chain that was there to the chain that replaces it.
     *
     * @param movementId the movement
     * @param from       the words of the step it stood on, for the entry its log gets, or {@code null}
     *                   where that step had already gone
     * @param landing    which step of the new chain it lands on, counted from the front
     */
    private record Carry(int movementId, String from, int landing) {}

    /**
     * What a restore would do, said before it is done.
     *
     * @param steps     the chain the preset writes, in the order it is walked
     * @param movements every movement standing on the chain right now, and where each would land
     */
    public record RestorePlan(List<PlannedStep> steps, List<PlannedLanding> movements) {}

    /**
     * One step of the chain the preset writes.
     *
     * @param index        where in the chain it stands, counted from the front, which is what a
     *                     chosen landing names it by
     * @param label        what it says
     * @param actor        whose turn it is on it
     * @param subject      which of the two pieces it is about
     * @param custodyAfter where that piece is once it has been walked
     * @param picksItem    whether it is the step that names the piece arriving
     */
    public record PlannedStep(
            int index,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {}

    /**
     * One step movements are standing on, and where writing the chain again would put them.
     *
     * <p>One entry per step rather than per movement. Where a movement lands follows from the step it
     * stands on and from nothing else, so every movement on one step lands on the same step of the
     * new chain and asking about them one by one would be the same question over and over.
     *
     * @param stepId         the step they stand on, or {@code null} for the ones whose step has gone
     * @param standingOn     the words that step carries, or {@code null} in the same case
     * @param movements      how many movements stand on it, so the reader knows what the answer moves
     * @param suggestedIndex where they land when the preset answers on its own, and {@code null} when
     *                       it does not, because an unsure answer offered as a filled-in one is an
     *                       answer nobody reads
     * @param certain        whether the preset answers on its own, which is what decides whether the
     *                       restore has to be told
     */
    public record PlannedLanding(
            Integer stepId, String standingOn, int movements, Integer suggestedIndex, boolean certain) {}

    /**
     * Where the movements on one step are to land, as somebody chose it.
     *
     * @param stepId    the step they stand on, which has to be one of the chain being written again
     * @param stepIndex the step of the preset they land on, counted from the front
     */
    public record ChosenLanding(Integer stepId, Integer stepIndex) {}

    private record Preset(
            String name, MovementPurpose purpose, ItemOwner ownerKind, MovementParty party, List<PresetStep> steps) {

        /** A chain of the association's own, where the other end is always its store. */
        Preset(String name, MovementPurpose purpose, ItemOwner ownerKind, List<PresetStep> steps) {
            this(name, purpose, ownerKind, MovementParty.STORE, steps);
        }
    }

    private record PresetStep(
            String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter, boolean picksItem) {

        static PresetStep of(String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter) {
            return new PresetStep(label, actor, subject, custodyAfter, false);
        }

        static PresetStep naming(String label, StepActor actor, StepSubject subject, ItemCustody custodyAfter) {
            return new PresetStep(label, actor, subject, custodyAfter, true);
        }
    }
}
