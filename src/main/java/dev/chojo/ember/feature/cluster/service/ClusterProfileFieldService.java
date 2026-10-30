/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.AssignedClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileFieldAssignment;
import dev.chojo.ember.feature.cluster.repository.ClusterFieldSetRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterStationGroupRepository;
import dev.chojo.ember.feature.members.entity.FieldDraft;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.service.FieldDependencies;
import dev.chojo.ember.feature.members.service.FieldSets;
import dev.chojo.ember.feature.members.service.GenderFields;
import dev.chojo.ember.feature.members.service.ProfileFieldCore;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final GenderFields genders;
    private final ClusterFieldSetRepository sets;

    @Inject
    public ClusterProfileFieldService(
            ClusterProfileFieldRepository fieldRepository,
            ClusterRepository clusterRepository,
            ClusterStationGroupRepository stationGroupRepository,
            ProfileFieldCore core,
            GenderFields genders,
            ClusterFieldSetRepository sets) {
        this.fieldRepository = fieldRepository;
        this.clusterRepository = clusterRepository;
        this.stationGroupRepository = stationGroupRepository;
        this.core = core;
        this.genders = genders;
        this.sets = sets;
    }

    /** One of the association's questions, refused where it is not the association's. */
    public ClusterProfileField field(int clusterId, int fieldId) {
        return requireField(clusterId, fieldId);
    }

    /** The association's bundles of questions, by name. */
    public List<FieldSet> findSets(int clusterId) {
        return sets.findByOwner(clusterId);
    }

    /**
     * Writes a new, empty bundle down.
     *
     * @throws RefusalResponse {@link MemberRefusal#PROFILE_FIELD_SET_NEEDS_A_NAME} for a blank name
     */
    public FieldSet createSet(int clusterId, @Nullable String name, boolean nameAsSection) {
        requireCluster(clusterId);
        FieldSet set = sets.create(clusterId, requireSetName(name), nameAsSection);
        log.info("Cluster {} bundled questions as set {} '{}'", clusterId, set.id(), set.name());
        return set;
    }

    /** Renames a bundle, or changes whether its name stands above its questions. */
    public void updateSet(int clusterId, int setId, @Nullable String name, boolean nameAsSection) {
        requireSet(clusterId, setId);
        sets.update(setId, requireSetName(name), nameAsSection);
    }

    /** Dissolves a bundle. Its questions stay, on their own, where they stand. */
    public void dissolveSet(int clusterId, int setId) {
        requireSet(clusterId, setId);
        sets.delete(setId);
        log.info("Cluster {} dissolved set {}", clusterId, setId);
    }

    /** Puts a bundle's questions in a new order, which every form follows. */
    public void orderSet(int clusterId, int setId, List<Integer> fieldIds) {
        requireSet(clusterId, setId);
        sets.applyOrder(setId, fieldIds);
        keepSetsTogether(clusterId);
    }

    /**
     * Refuses a bundle that is not the association's, before anything else of a question is written.
     *
     * @param setId the bundle, or {@code null} for none, which is always allowed
     */
    public void requireSetAllowed(int clusterId, @Nullable Integer setId) {
        if (setId != null) requireSet(clusterId, setId);
    }

    /**
     * Puts a question into a bundle, at its end, or takes it out. A question joining a bundle is put to
     * everybody the bundle's first question is put to, so the bundle stays one thing on every form.
     *
     * @param setId the bundle, or {@code null} to stand on its own
     */
    public void joinSet(int clusterId, int fieldId, @Nullable Integer setId) {
        ClusterProfileField field = requireField(clusterId, fieldId);
        if (Objects.equals(field.fieldSetId(), setId)) return;
        requireSetAllowed(clusterId, setId);
        List<Integer> others = setId == null ? List.of() : sets.members(setId);
        sets.place(fieldId, setId);
        if (!others.isEmpty()) {
            for (ClusterProfileFieldAssignment asked : fieldRepository.findAssignments(others.getFirst())) {
                askIfNotYet(fieldId, asked.role(), asked.position());
            }
        }
        keepSetsTogether(clusterId);
        log.info("Cluster {} field {} now in set {}", clusterId, fieldId, setId);
    }

    private FieldSet requireSet(int clusterId, int setId) {
        return sets.findById(setId)
                .filter(set -> set.ownerId() == clusterId)
                .orElseThrow(MemberRefusal.PROFILE_FIELD_SET_NOT_HERE::raise);
    }

    private static String requireSetName(@Nullable String name) {
        if (name == null || name.isBlank()) throw MemberRefusal.PROFILE_FIELD_SET_NEEDS_A_NAME.raise();
        return name.strip();
    }

    /** The other questions of the bundle a question is in, or none. */
    private List<Integer> siblingsOf(ClusterProfileField field) {
        Integer setId = field.fieldSetId();
        if (setId == null) return List.of();
        return sets.members(setId).stream().filter(id -> id != field.id()).toList();
    }

    private void askIfNotYet(int fieldId, ProfileFieldScope role, int position) {
        boolean asked = fieldRepository.findAssignments(fieldId).stream().anyMatch(a -> a.role() == role);
        if (!asked) fieldRepository.assignToRole(fieldId, role, position, null, null, null);
    }

    /** Puts every form of the association back into an order that keeps each bundle together. */
    private void keepSetsTogether(int clusterId) {
        var placements = sets.placements(clusterId);
        Map<ProfileFieldScope, List<ClusterProfileFieldAssignment>> forms =
                fieldRepository.findAssignmentsByCluster(clusterId).stream()
                        .collect(Collectors.groupingBy(ClusterProfileFieldAssignment::role));
        forms.forEach((role, form) -> fieldRepository.applyOrder(
                clusterId,
                role,
                FieldSets.keepTogether(
                        form.stream()
                                .sorted(Comparator.comparingInt(ClusterProfileFieldAssignment::position))
                                .map(ClusterProfileFieldAssignment::fieldId)
                                .toList(),
                        placements)));
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
        var stored = config.withPronounsOfItsAnswers();
        String chosen = checkedName(clusterId, name, fieldType, stored);
        genders.requireAllowed(
                new Owner.Association(clusterId), new FieldDraft(chosen, fieldType, stored, false), null, 0);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, null, chosen, stationGroupId);
        ClusterProfileField field = fieldRepository.create(
                clusterId,
                chosen,
                fieldType,
                stored,
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
        ClusterProfileField field = requireField(clusterId, fieldId);
        fieldRepository.assignToRole(fieldId, role, position, widthOverride, readonlyOverride, requiredOverride);
        List<Integer> siblings = siblingsOf(field);
        if (!siblings.isEmpty()) {
            for (Integer sibling : siblings) askIfNotYet(sibling, role, position);
            keepSetsTogether(clusterId);
        }
        log.info("Cluster {} asks {} field {}", clusterId, role, fieldId);
    }

    /** Stops asking a kind of member one of the cluster's questions, and the rest of its bundle with it. */
    public void unassignRole(int clusterId, int fieldId, ProfileFieldScope role) {
        ClusterProfileField field = requireField(clusterId, fieldId);
        fieldRepository.unassignRole(fieldId, role);
        for (Integer sibling : siblingsOf(field)) fieldRepository.unassignRole(sibling, role);
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
        FieldDependencies.requireNotStillUsed(fieldId, fieldType, dependencyNodes(clusterId));
        boolean unnamedSpacer = fieldType == FieldType.SPACER && (name == null || name.isBlank());
        String kept = unnamedSpacer ? existing.name() : name;
        var stored = config.withPronounsOfItsAnswers();
        String chosen = checkedName(clusterId, kept, fieldType, stored);
        genders.requireAllowed(
                new Owner.Association(clusterId),
                new FieldDraft(chosen, fieldType, stored, false),
                existing.fieldType(),
                fieldId);
        requireOwnGroup(clusterId, stationGroupId);
        requireReachesNobodyTwice(clusterId, fieldId, chosen, stationGroupId);
        fieldRepository.update(
                fieldId,
                chosen,
                fieldType,
                stored,
                required,
                readonly,
                width,
                stationReadonly,
                keepOnArchive,
                stationGroupId);
        log.info("Cluster {} changed field {} to '{}' ({})", clusterId, fieldId, chosen, fieldType);
    }

    /**
     * Makes a question depend on a yes/no question of the same association, or no longer depend on any.
     *
     * @param clusterId  the association
     * @param fieldId    the question
     * @param controller the yes/no question, or {@code null} for none
     * @throws RefusalResponse the refusals of {@link FieldDependencies#requireAllowed}
     */
    public void dependOn(int clusterId, int fieldId, @Nullable Integer controller) {
        ClusterProfileField field = requireField(clusterId, fieldId);
        if (Objects.equals(field.shownIfFieldId(), controller)) return;
        requireDependencyAllowed(clusterId, fieldId, controller);
        fieldRepository.setShownIf(fieldId, controller);
        log.info("Cluster {} field {} now depends on {}", clusterId, fieldId, controller);
    }

    /**
     * Refuses a dependency before anything else of the question is written, so a refused one leaves the
     * question as it was.
     *
     * @param clusterId  the association
     * @param fieldId    the question, or 0 for one about to be created
     * @param controller the yes/no question, or {@code null} for none
     * @throws RefusalResponse the refusals of {@link FieldDependencies#requireAllowed}
     */
    public void requireDependencyAllowed(int clusterId, int fieldId, @Nullable Integer controller) {
        FieldDependencies.requireAllowed(fieldId, controller, dependencyNodes(clusterId));
    }

    private List<FieldDependencies.Node> dependencyNodes(int clusterId) {
        return fieldRepository.findByCluster(clusterId).stream()
                .map(field ->
                        new FieldDependencies.Node(field.id(), field.name(), field.fieldType(), field.shownIfFieldId()))
                .toList();
    }

    /**
     * Withdraws a question and its answers.
     *
     * @throws RefusalResponse {@link dev.chojo.ember.api.refusal.MemberRefusal#PROFILE_DEPENDENCY_STILL_USED}
     *                         while others depend on it
     */
    public void delete(int clusterId, int fieldId) {
        requireField(clusterId, fieldId);
        FieldDependencies.requireNotStillUsed(fieldId, null, dependencyNodes(clusterId));
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
        int moved = fieldRepository.applyOrder(
                clusterId, role, FieldSets.keepTogether(fieldIds, sets.placements(clusterId)));
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
