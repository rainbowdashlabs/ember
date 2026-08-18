/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationMailConfig;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Derives the completion state of every station-setup-wizard step from the underlying tables, and
 * marks the wizard finished once all required steps are satisfied.
 *
 * <p>Step completion is intentionally <em>derived</em> rather than stored, so an administrator can
 * undo a setting later (e.g. delete the only member group) and the dashboard checklist reappears
 * with the now-missing step. The single persisted flag is {@code station.setup_completed_at} -
 * once set, the wizard route stops auto-redirecting administrators on login.
 */
@Singleton
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);

    /** Identifier of the address-and-geolocation required step. */
    public static final String STEP_ADDRESS = "address";
    /** Identifier of the modules required step. Always considered complete; defaults are sensible. */
    public static final String STEP_MODULES = "modules";
    /**
     * Identifier of the member-type permission-matrix required step. Always considered complete
     * once the admin has the wizard open; an empty matrix is a valid choice because
     * {@link dev.chojo.ember.api.auth.StationUserType} carries its own enum-level defaults
     * (e.g. {@code MANAGER → STATION_ADMINISTRATOR}) that apply regardless of station overrides.
     */
    public static final String STEP_MEMBER_TYPES = "memberTypes";
    /** Identifier of the optional member-groups step. */
    public static final String STEP_GROUPS = "groups";
    /** Identifier of the optional station-mail-relay step. */
    public static final String STEP_MAIL = "mail";
    /** Identifier of the optional branding step. */
    public static final String STEP_BRANDING = "branding";
    /** Identifier of the optional first-event step. Gated on the EVENTS module. */
    public static final String STEP_FIRST_EVENT = "firstEvent";
    /** Identifier of the optional knowledge-base-seed step. Gated on the KNOWLEDGE_BASE module. */
    public static final String STEP_KB_SEED = "kbSeed";
    /** Identifier of the optional federation-visibility step. */
    public static final String STEP_FEDERATION = "federation";
    /** Identifier of the optional invites step. */
    public static final String STEP_INVITES = "invites";

    private static final List<String> REQUIRED_STEP_IDS = List.of(STEP_ADDRESS, STEP_MODULES, STEP_MEMBER_TYPES);
    private static final List<String> OPTIONAL_STEP_IDS = List.of(
            STEP_GROUPS, STEP_MAIL, STEP_BRANDING, STEP_FIRST_EVENT, STEP_KB_SEED, STEP_FEDERATION, STEP_INVITES);

    private final StationRepository stationRepository;
    private final StationMailConfigRepository mailConfigRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final EventRepository eventRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final StationMemberRepository stationMemberRepository;
    private final StationService stationService;

    @Inject
    public SetupService(
            StationRepository stationRepository,
            StationMailConfigRepository mailConfigRepository,
            MemberGroupRepository memberGroupRepository,
            EventRepository eventRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            StationMemberRepository stationMemberRepository,
            StationService stationService) {
        this.stationRepository = stationRepository;
        this.mailConfigRepository = mailConfigRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.eventRepository = eventRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.stationService = stationService;
    }

    /**
     * Computes the current state of the setup wizard for the given station. The returned record is
     * a snapshot - callers should re-fetch after any mutation that might affect completeness.
     *
     * @param stationId the station whose status is requested
     * @return the derived status snapshot
     * @throws NoSuchElementException if the station does not exist
     */
    public SetupStatus getStatus(int stationId) {
        var station = stationRepository.findById(stationId).orElseThrow();
        var disabledModules = stationService.findDisabledModules(stationId);
        var required = buildRequiredSteps(stationId, station);
        var optional = buildOptionalSteps(stationId, station, disabledModules);
        return new SetupStatus(station.setupCompletedAt(), required, optional);
    }

    /**
     * Returns the ids of the required steps that are not yet complete for the given station. Empty
     * means the wizard can be finished.
     */
    public List<String> findMissingRequiredSteps(int stationId) {
        return getStatus(stationId).requiredSteps().stream()
                .filter(s -> !s.complete())
                .map(StepState::id)
                .toList();
    }

    /**
     * Marks the wizard complete on the given station, provided every required step is satisfied.
     * Idempotent: a second call after completion is a no-op.
     *
     * @param stationId the station to mark complete
     * @return {@link CompletionResult#COMPLETED} on the first successful call,
     *         {@link CompletionResult#ALREADY_COMPLETE} if the wizard was already finished, or
     *         {@link CompletionResult#MISSING_REQUIRED_STEPS} when a required step is still open
     */
    public CompletionResult complete(int stationId) {
        var status = getStatus(stationId);
        if (status.completedAt() != null) {
            return CompletionResult.ALREADY_COMPLETE;
        }
        boolean hasMissing = status.requiredSteps().stream().anyMatch(s -> !s.complete());
        if (hasMissing) {
            return CompletionResult.MISSING_REQUIRED_STEPS;
        }
        boolean changed = stationRepository.markSetupComplete(stationId);
        if (changed) {
            log.info("Station setup wizard completed: station={}", stationId);
            return CompletionResult.COMPLETED;
        }
        return CompletionResult.ALREADY_COMPLETE;
    }

    private List<StepState> buildRequiredSteps(int stationId, Station station) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put(STEP_ADDRESS, isAddressComplete(station));
        map.put(STEP_MODULES, true);
        map.put(STEP_MEMBER_TYPES, true);
        return REQUIRED_STEP_IDS.stream()
                .map(id -> new StepState(id, map.get(id), true))
                .toList();
    }

    private List<StepState> buildOptionalSteps(int stationId, Station station, Set<StationModule> disabledModules) {
        boolean eventsEnabled = !disabledModules.contains(StationModule.EVENTS);
        boolean kbEnabled = !disabledModules.contains(StationModule.KNOWLEDGE_BASE);

        Map<String, StepState> map = new LinkedHashMap<>();
        map.put(STEP_GROUPS, new StepState(STEP_GROUPS, memberGroupRepository.existsForStation(stationId), true));
        map.put(STEP_MAIL, new StepState(STEP_MAIL, isMailComplete(stationId), true));
        map.put(STEP_BRANDING, new StepState(STEP_BRANDING, isBrandingComplete(stationId, station), true));
        map.put(
                STEP_FIRST_EVENT,
                new StepState(
                        STEP_FIRST_EVENT, eventsEnabled && eventRepository.existsForStation(stationId), eventsEnabled));
        map.put(
                STEP_KB_SEED,
                new StepState(
                        STEP_KB_SEED, kbEnabled && knowledgeBaseRepository.existsForStation(stationId), kbEnabled));
        map.put(STEP_FEDERATION, new StepState(STEP_FEDERATION, isFederationComplete(station), true));
        map.put(
                STEP_INVITES,
                new StepState(
                        STEP_INVITES,
                        stationMemberRepository.findByStation(stationId).size() > 1,
                        true));
        return OPTIONAL_STEP_IDS.stream().map(map::get).toList();
    }

    private static boolean isAddressComplete(Station station) {
        return notBlank(station.addressLine())
                && notBlank(station.postalCode())
                && notBlank(station.city())
                && notBlank(station.country())
                && station.latitude() != null
                && station.longitude() != null;
    }

    private boolean isMailComplete(int stationId) {
        return mailConfigRepository
                .findByStation(stationId)
                .map(StationMailConfig::isConfigured)
                .orElse(false);
    }

    private boolean isBrandingComplete(int stationId, Station station) {
        if (notBlank(station.customThemeColors())) return true;
        return stationRepository.findLogo(stationId).isPresent();
    }

    private static boolean isFederationComplete(Station station) {
        return notBlank(station.discoveryDescription());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Outcome of {@link #complete(int)}.
     */
    public enum CompletionResult {
        /** The wizard was marked complete by this call. */
        COMPLETED,
        /** The wizard was already complete before this call. */
        ALREADY_COMPLETE,
        /** One or more required steps remain incomplete; the wizard cannot be finished yet. */
        MISSING_REQUIRED_STEPS
    }

    /**
     * Per-step state in the wizard.
     *
     * @param id         stable identifier matching one of the {@code STEP_*} constants
     * @param complete   whether the step's underlying data is currently filled in
     * @param applicable whether the step is shown at all - module-gated optional steps may be
     *                   hidden when their module is disabled, in which case {@code complete} is
     *                   also {@code false}
     */
    public record StepState(String id, boolean complete, boolean applicable) {}

    /**
     * Snapshot of the wizard's status for a station.
     *
     * @param completedAt   timestamp at which the wizard was marked complete, or {@code null}
     * @param requiredSteps required step states, in display order
     * @param optionalSteps optional step states, in display order
     */
    public record SetupStatus(Instant completedAt, List<StepState> requiredSteps, List<StepState> optionalSteps) {}
}
