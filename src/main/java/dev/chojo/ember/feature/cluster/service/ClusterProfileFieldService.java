/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final ProfileFieldChangeRepository changeRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterProfileFieldService(
            ClusterProfileFieldRepository fieldRepository,
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            ProfileFieldChangeRepository changeRepository,
            DomainEventBus eventBus) {
        this.fieldRepository = fieldRepository;
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.changeRepository = changeRepository;
        this.eventBus = eventBus;
    }

    public List<ClusterProfileField> findByCluster(int clusterId) {
        return fieldRepository.findByCluster(clusterId);
    }

    /**
     * The cluster's questions that reach one station, in one scope.
     *
     * @param stationId the station
     * @param scope     which kind of member they apply to
     * @return the fields, empty when the station answers to no cluster
     */
    public List<ClusterProfileField> findForStation(int stationId, ProfileFieldScope scope) {
        return fieldRepository.findForStation(stationId, scope);
    }

    public ClusterProfileField create(
            int clusterId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope,
            boolean stationReadonly,
            boolean keepOnArchive) {
        requireCluster(clusterId);
        requireUsable(name, fieldType, scope, config);
        ClusterProfileField field = fieldRepository.create(
                clusterId, name.trim(), fieldType, config, position, scope, stationReadonly, keepOnArchive);
        log.info("Cluster {} added field '{}'", clusterId, field.name());
        return field;
    }

    public void update(
            int clusterId,
            int fieldId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope,
            boolean stationReadonly,
            boolean keepOnArchive) {
        requireField(clusterId, fieldId);
        requireUsable(name, fieldType, scope, config);
        fieldRepository.update(
                fieldId, name.trim(), fieldType, config, position, scope, stationReadonly, keepOnArchive);
    }

    public void delete(int clusterId, int fieldId) {
        requireField(clusterId, fieldId);
        fieldRepository.delete(fieldId);
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

        for (var entry : values.entrySet()) {
            ClusterProfileField field = requireField(clusterId, entry.getKey());
            String oldValue = before.getOrDefault(field.id(), "null");
            String newValue = entry.getValue() != null ? entry.getValue() : "null";
            if (Objects.equals(oldValue, newValue)) continue;

            fieldRepository.setValue(memberId, field.id(), entry.getValue());
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
    private static void requireUsable(
            String name, ProfileFieldType fieldType, ProfileFieldScope scope, ProfileFieldConfig config) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A field needs a name");
        if (scope == ProfileFieldScope.GROUP) {
            throw new BadRequestResponse(
                    "A group-scoped field names a group of one station, which a cluster cannot see");
        }
        if (fieldType == ProfileFieldType.BIRTH_DATE) {
            throw new BadRequestResponse(
                    "A station declares its own date of birth field, and a second one would collide with it");
        }
        if (config != null && config.groupId() != null) {
            throw new BadRequestResponse("A cluster field cannot name a station's group");
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
