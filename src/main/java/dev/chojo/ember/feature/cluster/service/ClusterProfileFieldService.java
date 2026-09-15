/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.feature.cluster.entity.AssignedClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileFieldAssignment;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The questions a cluster asks of the people at its stations.
 *
 * <p>Two kinds of field a cluster may not declare, and both are about something the cluster cannot see:
 *
 * <ul>
 *   <li>a group-scoped field names a station-local group in its settings, and the cluster has no view of
 *       those,
 *   <li>a date-of-birth field is the one field a station may declare at most once, and a cluster one would
 *       collide with the station's own.
 * </ul>
 */
@Singleton
public class ClusterProfileFieldService {
    private static final Logger log = LoggerFactory.getLogger(ClusterProfileFieldService.class);

    private final ClusterProfileFieldRepository fieldRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterStationGroupRepository stationGroupRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final ProfileFieldChangeRepository changeRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterProfileFieldService(
            ClusterProfileFieldRepository fieldRepository,
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            ProfileFieldChangeRepository changeRepository,
            DomainEventBus eventBus) {
        this.fieldRepository = fieldRepository;
        this.clusterRepository = clusterRepository;
        this.stationGroupRepository = stationGroupRepository;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.changeRepository = changeRepository;
        this.eventBus = eventBus;
    }

    public List<ClusterProfileField> findByCluster(int clusterId) {
        return fieldRepository.findByCluster(clusterId);
    }

    /**
     * The cluster's questions that reach one station, as put to one kind of member.
     *
     * @param stationId the station
     * @param role      which kind of member they are put to
     * @return the fields, empty when the station answers to no cluster
     */
    public List<AssignedClusterProfileField> findForStation(int stationId, ProfileFieldScope role) {
        return fieldRepository.findForStation(stationId, role);
    }

    /**
     * Writes a question down. Who is asked it is a separate act, as it is for a station's own.
     *
     * <p>A question nobody has been put to reaches nobody until an audience is named, which the screen
     * says out loud rather than guessing at one.
     */
    public ClusterProfileField create(
            int clusterId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {
        requireCluster(clusterId);
        requireUsable(name, fieldType, config);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, null, name.trim(), stationGroupId);
        ClusterProfileField field = fieldRepository.create(
                clusterId,
                name.trim(),
                fieldType,
                config,
                required,
                readonly,
                width,
                stationReadonly,
                keepOnArchive,
                stationGroupId);
        log.info("Cluster {} added field '{}'", clusterId, field.name());
        return field;
    }

    /** Which kinds of member one of the cluster's questions is asked of. */
    public List<ClusterProfileFieldAssignment> findAssignments(int fieldId) {
        return fieldRepository.findAssignments(fieldId);
    }

    /** Which kinds of member every one of the cluster's questions is asked of. */
    public List<ClusterProfileFieldAssignment> findAssignmentsByCluster(int clusterId) {
        return fieldRepository.findAssignmentsByCluster(clusterId);
    }

    /**
     * Asks another kind of member one of the cluster's existing questions, or changes how it is asked.
     *
     * @param role             who is to be asked
     * @param position         where it sits on their form
     * @param widthOverride    how much of a row it takes here, null to follow the definition
     * @param readonlyOverride whether only the member management writes it here, null to follow the definition
     * @param requiredOverride whether they must answer, null to follow the definition
     */
    public void assignToRole(
            int clusterId,
            int fieldId,
            ProfileFieldScope role,
            int position,
            String widthOverride,
            Boolean readonlyOverride,
            Boolean requiredOverride) {
        requireField(clusterId, fieldId);
        fieldRepository.assignToRole(fieldId, role, position, widthOverride, readonlyOverride, requiredOverride);
        log.info("Cluster {} asks {} field {}", clusterId, role, fieldId);
    }

    /** Stops asking a kind of member one of the cluster's questions. */
    public void unassignRole(int clusterId, int fieldId, ProfileFieldScope role) {
        requireField(clusterId, fieldId);
        fieldRepository.unassignRole(fieldId, role);
        log.info("Cluster {} no longer asks {} field {}", clusterId, role, fieldId);
    }

    public void update(
            int clusterId,
            int fieldId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {
        requireField(clusterId, fieldId);
        requireUsable(name, fieldType, config);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, fieldId, name.trim(), stationGroupId);
        fieldRepository.update(
                fieldId,
                name.trim(),
                fieldType,
                config,
                required,
                readonly,
                width,
                stationReadonly,
                keepOnArchive,
                stationGroupId);
        log.info("Cluster {} changed field {} to '{}' ({})", clusterId, fieldId, name.trim(), fieldType);
    }

    public void delete(int clusterId, int fieldId) {
        requireField(clusterId, fieldId);
        fieldRepository.delete(fieldId);
        log.info("Cluster {} withdrew field {}", clusterId, fieldId);
    }

    /**
     * What one member answered to the cluster's questions.
     *
     * @param clusterId the cluster asking
     * @param memberId  the member
     * @return field id to answer
     */
    public Map<Integer, String> findValues(int clusterId, int memberId) {
        requireMemberOfCluster(clusterId, memberId);
        Map<Integer, String> values = new HashMap<>();
        for (var value : fieldRepository.findValues(memberId)) {
            values.put(value.fieldId(), value.value());
        }
        return values;
    }

    /**
     * Writes answers to the cluster's questions, recording each change in the same history a station field's
     * change goes to.
     *
     * <p>One history rather than two, so a member's profile reads as one story: what changed, when, and by
     * whom, whoever asked the question.
     *
     * <p>Only questions that reach the member's station may be answered. Without that a manager could fill
     * in an answer to a question the station is never shown.
     *
     * @param clusterId the cluster asking
     * @param memberId  the member answering
     * @param values    field id to answer
     * @param changedBy the station member making the change, for the record
     */
    public void setValues(int clusterId, int memberId, Map<Integer, String> values, int changedBy) {
        Cluster cluster = requireCluster(clusterId);
        requireMemberOfCluster(clusterId, memberId);

        Map<Integer, String> before = findValues(clusterId, memberId);
        List<String> changed = new ArrayList<>();

        int stationId = stationOf(memberId);
        Map<ProfileFieldScope, Set<Integer>> reaching = new HashMap<>();

        Set<Integer> reachingHere = fieldRepository.findIdsReachingStation(stationId);
        for (var entry : values.entrySet()) {
            ClusterProfileField field = requireField(clusterId, entry.getKey());
            if (!reachingHere.contains(field.id())) {
                throw new BadRequestResponse("That question is not asked of this member's station");
            }
            String oldValue = before.getOrDefault(field.id(), "null");
            String newValue = entry.getValue() != null ? entry.getValue() : "null";
            if (Objects.equals(oldValue, newValue)) continue;

            fieldRepository.setValue(memberId, field.id(), Json.document(entry.getValue()));
            changeRepository.createForClusterField(
                    field.id(),
                    memberId,
                    oldValue,
                    newValue,
                    changedBy,
                    field.config().notifyOnChange());
            changed.add(field.name());
        }

        if (!changed.isEmpty()) {
            log.info("Cluster {} changed {} field(s) of member {}", clusterId, changed.size(), memberId);
            eventBus.publish(new ClusterFieldValueChanged(
                    stationOf(memberId), memberId, cluster.name(), String.join(", ", changed)));
        }
    }

    /**
     * Clears the cluster's answers for everybody at one station, which is what a release does.
     *
     * @param stationId the station being released
     * @return how many answers were cleared
     */
    public int clearValuesOfStation(int stationId) {
        int cleared = fieldRepository.deleteValuesOfStation(stationId);
        if (cleared > 0) log.info("Cleared {} cluster field answer(s) at released station {}", cleared, stationId);
        return cleared;
    }

    /**
     * Refuses the two kinds of field a cluster cannot meaningfully ask for.
     */
    /**
     * A question may only be pointed at a group of the association's own.
     */
    private void requireOwnGroup(int clusterId, Integer stationGroupId) {
        if (stationGroupId == null) return;
        boolean own = stationGroupRepository
                .findById(stationGroupId)
                .filter(group -> group.clusterId() == clusterId)
                .isPresent();
        if (!own) throw new BadRequestResponse("That group of stations belongs to another association");
    }

    /**
     * Two questions of one name may never land on the same profile.
     *
     * <p>The database catches the exact duplicate. The interesting case is not exact: a question asked of
     * everybody and one of the same name asked of a group would both reach the stations in that group, and a
     * member there would be asked twice with two places to answer. So the check is what each of the two
     * actually reaches, and whether those two sets meet.
     *
     * @param clusterId the association
     * @param fieldId   the question being edited, or {@code null} when it is being created
     * @param name      what it is called
     * @param scope     which kind of member it applies to
     * @param groupId   the group it is pointed at, or {@code null} for every station
     */
    private void requireReachesNobodyTwice(int clusterId, Integer fieldId, String name, Integer groupId) {
        Set<Integer> reached = new HashSet<>(stationGroupRepository.findStationIdsReachedBy(clusterId, groupId));
        if (reached.isEmpty()) return;

        for (ClusterProfileField other : fieldRepository.findByCluster(clusterId)) {
            if (fieldId != null && other.id() == fieldId) continue;
            // A question is written once, so two of the same name reaching one station collide
            // whoever each is put to: they are the same question asked twice.
            if (!other.name().equalsIgnoreCase(name)) continue;

            for (int stationId : stationGroupRepository.findStationIdsReachedBy(clusterId, other.stationGroupId())) {
                if (reached.contains(stationId)) {
                    throw new BadRequestResponse(
                            "A question called '%s' already reaches a station this one would reach as well"
                                    .formatted(name));
                }
            }
        }
    }

    /**
     * Puts one audience's form in the given order, in one write.
     *
     * @param clusterId the cluster whose questions these are
     * @param role      the audience whose form is being ordered
     * @param fieldIds  the questions in the order they should stand
     */
    public void reorder(int clusterId, ProfileFieldScope role, List<Integer> fieldIds) {
        requireCluster(clusterId);
        int moved = fieldRepository.applyOrder(clusterId, role, fieldIds);
        log.info("Cluster questions reordered: cluster={}, role={}, fields={}", clusterId, role, moved);
    }

    private static void requireUsable(String name, ProfileFieldType fieldType, ProfileFieldConfig config) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A field needs a name");
        if (fieldType == ProfileFieldType.BIRTH_DATE) {
            throw new BadRequestResponse(
                    "A station declares its own date of birth field, and a second one would collide with it");
        }
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    private ClusterProfileField requireField(int clusterId, int fieldId) {
        ClusterProfileField field =
                fieldRepository.findById(fieldId).orElseThrow(() -> new NotFoundResponse("No such field"));
        if (field.clusterId() != clusterId) throw new NotFoundResponse("No such field");
        return field;
    }

    /**
     * The member, checked to belong to a station of this cluster, so one cluster cannot write into another's
     * people.
     */
    private void requireMemberOfCluster(int clusterId, int memberId) {
        Station station = stationRepository
                .findById(stationOf(memberId))
                .orElseThrow(() -> new NotFoundResponse("No such member"));
        if (station.clusterId() == null || station.clusterId() != clusterId) {
            throw new NotFoundResponse("No such member");
        }
    }

    private int stationOf(int memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new NotFoundResponse("No such member"))
                .stationId();
    }
}
