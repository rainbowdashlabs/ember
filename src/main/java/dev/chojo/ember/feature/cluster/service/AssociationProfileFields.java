/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.ProfileFieldOwner;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An association as the owner of the profile questions it asks at its stations.
 *
 * <p>Its questions are put to kinds of member only (a member group belongs to one station, which the
 * association cannot name), reach the stations of a group of stations or all of them, and their answers
 * live in {@code cluster_profile_field_value}. Where the association itself writes, the member is told,
 * because the person who changed their profile is not at their station.
 */
@Singleton
public class AssociationProfileFields implements ProfileFieldOwner {
    private static final DefinitionRefusals REFUSALS = new DefinitionRefusals(
            ClusterRefusal.CLUSTER_PROFILE_FIELD_NEEDS_A_NAME,
            ClusterRefusal.CLUSTER_PROFILE_FIELD_TYPE_NOT_OFFERED,
            ClusterRefusal.CLUSTER_PROFILE_DEFAULT_NOT_ACCEPTED,
            ClusterRefusal.CLUSTER_EXPIRY_SETTINGS_OUT_OF_RANGE,
            ClusterRefusal.CLUSTER_PROFILE_GENDER_ALREADY_ASKED,
            ClusterRefusal.CLUSTER_PROFILE_GENDER_NOT_FROM_CHOICE,
            ClusterRefusal.CLUSTER_PROFILE_PRONOUN_TOO_LONG);

    private final ClusterProfileFieldRepository fieldRepository;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final DomainEventBus eventBus;

    @Inject
    public AssociationProfileFields(
            ClusterProfileFieldRepository fieldRepository,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            DomainEventBus eventBus) {
        this.fieldRepository = fieldRepository;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.eventBus = eventBus;
    }

    @Override
    public FieldOrigin origin() {
        return FieldOrigin.CLUSTER;
    }

    @Override
    public List<ProfileFieldService.MergedField> putTo(int stationId, ProfileFieldScope role) {
        return fieldRepository.findForStation(stationId, role).stream()
                .map(assigned -> new ProfileFieldService.MergedField(
                        assigned.field().id(),
                        assigned.field().name(),
                        assigned.field().fieldType(),
                        assigned.field().config(),
                        assigned.required(),
                        assigned.assignment().position(),
                        assigned.width(),
                        assigned.readonly(),
                        role,
                        FieldOrigin.CLUSTER,
                        assigned.field().stationReadonly()))
                .toList();
    }

    @Override
    public List<ProfileFieldService.MergedField> putToGroupsOf(StationMember member) {
        return List.of();
    }

    @Override
    public Optional<OwnedProfileField> askedAt(int stationId, int fieldId) {
        if (!fieldRepository.findIdsReachingStation(stationId).contains(fieldId)) return Optional.empty();
        return fieldRepository.findById(fieldId).map(AssociationProfileFields::owned);
    }

    private static OwnedProfileField owned(ClusterProfileField field) {
        return new OwnedProfileField(
                FieldOrigin.CLUSTER,
                field.id(),
                field.clusterId(),
                field.name(),
                field.fieldType(),
                field.config(),
                field.required());
    }

    @Override
    public Refusal notAskedHere() {
        return MemberRefusal.PROFILE_ASSOCIATION_FIELD_NOT_HERE_ON_ANSWER;
    }

    @Override
    public List<ProfileFieldValue> answersOf(int memberId) {
        return fieldRepository.findValues(memberId);
    }

    @Override
    public List<ProfileFieldValue> answersOf(Collection<Integer> memberIds) {
        return fieldRepository.findValuesOf(memberIds);
    }

    @Override
    public List<ProfileFieldValue> answersTo(int fieldId) {
        return fieldRepository.findValuesOfField(fieldId);
    }

    @Override
    public void keep(int memberId, int fieldId, @Nullable JsonNode answer) {
        if (answer == null) {
            fieldRepository.deleteValue(memberId, fieldId);
        } else {
            fieldRepository.setValue(memberId, fieldId, answer);
        }
    }

    @Override
    public void clearOnArchive(int memberId) {
        fieldRepository.deleteNonKeptValues(memberId);
    }

    @Override
    public List<OwnedProfileField> ofType(FieldType type) {
        return fieldRepository.findAllByType(type).stream()
                .map(AssociationProfileFields::owned)
                .toList();
    }

    /**
     * Tells the member when the association wrote, whatever the questions were. The station's member
     * management hears through the station, which hears of every change.
     */
    @Override
    public void changed(StationMember member, @Nullable Integer author, ProfileWriter writer, List<String> fieldNames) {
        if (!writer.owningAssociation()) return;
        String associationName = stationRepository
                .findById(member.stationId())
                .map(Station::clusterId)
                .flatMap(clusterRepository::findById)
                .map(Cluster::name)
                .orElse("");
        eventBus.publish(new ClusterFieldValueChanged(
                member.stationId(), member.id(), associationName, String.join(", ", fieldNames)));
    }

    @Override
    public Set<FieldType> offeredTypes() {
        return FieldTypes.ASSOCIATION;
    }

    @Override
    public Set<String> namesTaken(int ownerId) {
        return fieldRepository.findByCluster(ownerId).stream()
                .map(ClusterProfileField::name)
                .collect(Collectors.toSet());
    }

    @Override
    public DefinitionRefusals definitionRefusals() {
        return REFUSALS;
    }
}
