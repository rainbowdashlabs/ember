/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.AssignedClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileFieldAssignment;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.members.entity.FieldDraft;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.service.ProfileFieldCore;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
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
    private final ProfileFieldCore core;

    @Inject
    public ClusterProfileFieldService(
            ClusterProfileFieldRepository fieldRepository,
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository,
            ProfileFieldCore core) {
        this.fieldRepository = fieldRepository;
        this.clusterRepository = clusterRepository;
        this.stationGroupRepository = stationGroupRepository;
        this.core = core;
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
            @Nullable String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            @Nullable Integer stationGroupId) {
        requireCluster(clusterId);
        String chosen = checkedName(clusterId, name, fieldType, config);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, null, chosen, stationGroupId);
        ClusterProfileField field = fieldRepository.create(
                clusterId,
                chosen,
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
     * @throws RefusalResponse {@link ClusterRefusal#CLUSTER_FIELD_AUDIENCE_NOT_ASKED} for trial members
     */
    public void assignToRole(
            int clusterId,
            int fieldId,
            ProfileFieldScope role,
            int position,
            @Nullable String widthOverride,
            @Nullable Boolean readonlyOverride,
            @Nullable Boolean requiredOverride) {
        requireAsked(role);
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

    /**
     * Changes a question. A spacer sent without a name keeps the one it has.
     */
    public void update(
            int clusterId,
            int fieldId,
            @Nullable String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            @Nullable Integer stationGroupId) {
        ClusterProfileField existing = requireField(clusterId, fieldId);
        boolean unnamedSpacer = fieldType == FieldType.SPACER && (name == null || name.isBlank());
        String kept = unnamedSpacer ? existing.name() : name;
        String chosen = checkedName(clusterId, kept, fieldType, config);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, fieldId, chosen, stationGroupId);
        fieldRepository.update(
                fieldId,
                chosen,
                fieldType,
                config,
                required,
                readonly,
                width,
                stationReadonly,
                keepOnArchive,
                stationGroupId);
        log.info("Cluster {} changed field {} to '{}' ({})", clusterId, fieldId, chosen, fieldType);
    }

    public void delete(int clusterId, int fieldId) {
        requireField(clusterId, fieldId);
        fieldRepository.delete(fieldId);
        log.info("Cluster {} withdrew field {}", clusterId, fieldId);
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
     * A question may only be pointed at a group of the association's own.
     */
    private void requireOwnGroup(int clusterId, @Nullable Integer stationGroupId) {
        if (stationGroupId == null) return;
        boolean own = stationGroupRepository
                .findById(stationGroupId)
                .filter(group -> group.clusterId() == clusterId)
                .isPresent();
        if (!own) throw ClusterRefusal.CLUSTER_PROFILE_FIELD_GROUP_NOT_OWN.raise();
    }

    /**
     * Two questions of one name may never land on the same profile.
     *
     * <p>The database catches the exact duplicate. The interesting case is not exact: a question asked of
     * everybody and one of the same name asked of a group would both reach the stations in that group, and a
     * member there would be asked twice with two places to answer. So the check is what each of the two
     * actually reaches, and whether those two sets meet. The scope plays no part: two of one name reaching
     * one station are the same question asked twice, whoever each is put to.
     *
     * @param clusterId the association
     * @param fieldId   the question being edited, or {@code null} when it is being created
     * @param name      what it is called
     * @param scope     which kind of member it applies to
     * @param groupId   the group it is pointed at, or {@code null} for every station
     */
    private void requireReachesNobodyTwice(
            int clusterId, @Nullable Integer fieldId, String name, @Nullable Integer groupId) {
        Set<Integer> reached = new HashSet<>(stationGroupRepository.findStationIdsReachedBy(clusterId, groupId));
        if (reached.isEmpty()) return;

        for (ClusterProfileField other : fieldRepository.findByCluster(clusterId)) {
            if (fieldId != null && other.id() == fieldId) continue;
            if (!other.name().equalsIgnoreCase(name)) continue;

            for (int stationId : stationGroupRepository.findStationIdsReachedBy(clusterId, other.stationGroupId())) {
                if (reached.contains(stationId)) {
                    throw ClusterRefusal.CLUSTER_PROFILE_FIELD_NAME_REACHES_TWICE.raise(name);
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

    /**
     * Checks a question the association is about to write down, the way every owner's is checked, and says
     * what to file it under. An unnamed spacer is numbered among the association's own questions.
     */
    private String checkedName(int clusterId, @Nullable String name, FieldType fieldType, ProfileFieldConfig config) {
        return core.checkedName(new Owner.Association(clusterId), new FieldDraft(name, fieldType, config, false));
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository
                .findById(clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_PROFILE_FIELD_CLUSTER_GONE::raise);
    }

    private ClusterProfileField requireField(int clusterId, int fieldId) {
        ClusterProfileField field =
                fieldRepository.findById(fieldId).orElseThrow(ClusterRefusal.CLUSTER_PROFILE_FIELD_NOT_HERE::raise);
        if (field.clusterId() != clusterId) throw ClusterRefusal.CLUSTER_PROFILE_FIELD_NOT_HERE.raise();
        return field;
    }

    /**
     * Refuses trial members as an audience. An association asks the people who belong to its stations; a
     * trial member does not yet, and the screen never offered them.
     */
    private static void requireAsked(ProfileFieldScope role) {
        if (role == ProfileFieldScope.TRIAL) throw ClusterRefusal.CLUSTER_FIELD_AUDIENCE_NOT_ASKED.raise();
    }
}
