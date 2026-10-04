/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.AssignedProfileField;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A station as the owner of its own profile questions.
 *
 * <p>Its questions are put to kinds of member and to its own groups, their answers live in
 * {@code profile_field_value}, and a change is told to the station's member management.
 */
@Singleton
public class StationProfileFields implements ProfileFieldOwner {
    private static final DefinitionRefusals REFUSALS = new DefinitionRefusals(
            MemberRefusal.PROFILE_FIELD_DETAILS_MISSING_ON_CREATE,
            MemberRefusal.PROFILE_FIELD_TYPE_NOT_OFFERED,
            MemberRefusal.PROFILE_DEFAULT_NOT_ACCEPTED,
            MemberRefusal.EXPIRY_SETTINGS_OUT_OF_RANGE,
            MemberRefusal.PROFILE_GENDER_ALREADY_ASKED,
            MemberRefusal.PROFILE_GENDER_NOT_FROM_CHOICE,
            MemberRefusal.PROFILE_PRONOUN_TOO_LONG);

    private final ProfileFieldRepository fieldRepository;
    private final MemberGroupRepository groupRepository;
    private final AccountRepository accountRepository;
    private final Notifier notifier;

    @Inject
    public StationProfileFields(
            ProfileFieldRepository fieldRepository,
            MemberGroupRepository groupRepository,
            AccountRepository accountRepository,
            Notifier notifier) {
        this.fieldRepository = fieldRepository;
        this.groupRepository = groupRepository;
        this.accountRepository = accountRepository;
        this.notifier = notifier;
    }

    @Override
    public FieldOrigin origin() {
        return FieldOrigin.STATION;
    }

    @Override
    public List<ProfileFieldService.MergedField> putTo(int stationId, ProfileFieldScope role) {
        return fieldRepository.findByStationAndScope(stationId, role).stream()
                .map(StationProfileFields::merged)
                .toList();
    }

    /**
     * The group-scoped fields that reach this member, which are the ones asked of a group they are in. A
     * field of that scope naming no group is asked of nobody: it is half-configured rather than universal,
     * and the configuration screen lists it separately for exactly that reason.
     */
    @Override
    public List<ProfileFieldService.MergedField> putToGroupsOf(StationMember member) {
        var groupIds = groupRepository.findGroupsForMember(member.id()).stream()
                .map(MemberGroup::id)
                .toList();
        if (groupIds.isEmpty()) return List.of();
        return fieldRepository.findByStationAndGroups(member.stationId(), groupIds).stream()
                .map(StationProfileFields::merged)
                .toList();
    }

    private static ProfileFieldService.MergedField merged(AssignedProfileField assigned) {
        var assignment = assigned.assignment();
        return new ProfileFieldService.MergedField(
                assigned.field().id(),
                assigned.field().name(),
                assigned.field().fieldType(),
                assigned.field().config(),
                assigned.required(),
                assignment.position(),
                assigned.width(),
                assigned.readonly(),
                assignment.role(),
                FieldOrigin.STATION,
                false);
    }

    @Override
    public Optional<OwnedProfileField> askedAt(int stationId, int fieldId) {
        return fieldRepository
                .findById(fieldId)
                .filter(field -> field.stationId() == stationId)
                .map(StationProfileFields::owned);
    }

    private static OwnedProfileField owned(ProfileField field) {
        return new OwnedProfileField(
                FieldOrigin.STATION,
                field.id(),
                field.stationId(),
                field.name(),
                field.fieldType(),
                field.config(),
                field.required());
    }

    @Override
    public Refusal notAskedHere() {
        return MemberRefusal.PROFILE_FIELD_NOT_HERE_ON_ANSWER;
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
        fieldRepository.deleteNonArchivedValues(memberId);
    }

    @Override
    public List<OwnedProfileField> ofType(FieldType type) {
        return fieldRepository.findAllByType(type).stream()
                .map(StationProfileFields::owned)
                .toList();
    }

    /**
     * Tells the station's member management, apart from whoever made the change, whatever owner asked the
     * questions: a change the association made lands on the station's member as well.
     */
    @Override
    public void changed(StationMember member, @Nullable Integer author, ProfileWriter writer, List<String> fieldNames) {
        Integer accountId = member.accountId();
        var account =
                accountId == null ? null : accountRepository.findById(accountId).orElse(null);
        String memberName = account != null ? NameParts.of(account).called() : "?";
        var data = NotificationData.of(
                new NotificationParams.ProfileFieldChanged(memberName, String.join(", ", fieldNames)),
                new NotificationData.NotificationLink("members-detail", Map.of("id", member.id())));
        notifier.notify(
                StationAudience.holders(member.stationId(), StationPermission.MEMBER_MANAGER)
                        .except(author),
                NotificationType.PROFILE_FIELD_CHANGED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }

    @Override
    public Set<FieldType> offeredTypes() {
        return FieldTypes.PROFILE;
    }

    @Override
    public Set<String> namesTaken(int ownerId) {
        return fieldRepository.findByStation(ownerId).stream()
                .map(ProfileField::name)
                .collect(Collectors.toSet());
    }

    @Override
    public DefinitionRefusals definitionRefusals() {
        return REFUSALS;
    }
}
