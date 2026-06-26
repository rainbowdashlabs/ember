/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.PagedChanges;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for profile field management including field definitions, member values,
 * change tracking with manager attribution, and profile completeness validation.
 */
@Singleton
public class ProfileFieldService {
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldService.class);
    private static final Duration MERGE_WINDOW = Duration.ofMinutes(5);

    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldChangeRepository changeRepository;
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public ProfileFieldService(
            ProfileFieldRepository profileFieldRepository,
            ProfileFieldChangeRepository changeRepository,
            NotificationService notificationService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.profileFieldRepository = profileFieldRepository;
        this.changeRepository = changeRepository;
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    // -- Field Definitions --

    private static ProfileFieldScope scopeForUserType(StationUserType userType) {
        if (userType == null) return ProfileFieldScope.MEMBER;
        return switch (userType) {
            case TRIAL, MEMBER -> ProfileFieldScope.MEMBER;
            case GUARDIAN -> ProfileFieldScope.GUARDIAN;
            case TEAM -> ProfileFieldScope.TEAM;
            case MANAGER -> ProfileFieldScope.MANAGER;
        };
    }

    public List<ProfileField> findByStation(int stationId) {
        return profileFieldRepository.findByStation(stationId);
    }

    public List<ProfileField> findByStationAndScope(int stationId, ProfileFieldScope scope) {
        return profileFieldRepository.findByStationAndScope(stationId, scope);
    }

    public List<ProfileField> findApplicableFields(int memberId) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return List.of();
        var scope = scopeForUserType(member.userType());
        if (scope == null) return List.of();
        return profileFieldRepository.findByStationAndScope(member.stationId(), scope);
    }

    public Optional<ProfileField> findById(int id) {
        return profileFieldRepository.findById(id);
    }

    public ProfileField create(
            int stationId,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope) {
        var field = profileFieldRepository.create(stationId, name, fieldType, config, position, scope);
        log.info(
                "Profile field created: id={}, station={}, name='{}', type={}, scope={}",
                field.id(),
                stationId,
                name,
                fieldType,
                scope);
        return field;
    }

    public Optional<ProfileField> update(
            int id,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            boolean keepOnArchive) {
        if (profileFieldRepository.update(id, name, fieldType, config, position, keepOnArchive)) {
            log.info("Profile field updated: id={}, name='{}', type={}", id, name, fieldType);
            return profileFieldRepository.findById(id);
        }
        log.warn("Profile field update affected no rows: id={}", id);
        return Optional.empty();
    }

    public boolean delete(int id) {
        boolean deleted = profileFieldRepository.delete(id);
        if (deleted) {
            log.info("Profile field deleted: id={}", id);
        } else {
            log.warn("Profile field delete affected no rows: id={}", id);
        }
        return deleted;
    }

    /**
     * Check if a member has all required profile fields filled.
     */
    public boolean isProfileComplete(int memberId, int stationId, List<String> roleNames) {
        var allFields = profileFieldRepository.findByStation(stationId);
        var values = profileFieldRepository.findValues(memberId);
        var valueMap = values.stream().collect(Collectors.toMap(ProfileFieldValue::fieldId, ProfileFieldValue::value));

        // Determine applicable scopes from roles
        var scopes = new ArrayList<ProfileFieldScope>();
        if (roleNames.contains("MEMBER")) scopes.add(ProfileFieldScope.MEMBER);
        if (roleNames.contains("GUARDIAN")) scopes.add(ProfileFieldScope.GUARDIAN);
        if (roleNames.stream()
                .anyMatch(r -> r.equals("TEAM")
                        || r.equals("MANAGER")
                        || r.equals("ADMIN")
                        || r.equals("ATTENDANCE_MANAGER")
                        || r.equals("INVENTORY_MANAGER")
                        || r.equals("EVENT_MANAGER")
                        || r.equals("MEMBER_MANAGER")
                        || r.equals("NEWS_MANAGER"))) {
            scopes.add(ProfileFieldScope.TEAM);
        }
        if (roleNames.contains("MANAGER") || roleNames.contains("ADMIN")) {
            scopes.add(ProfileFieldScope.MANAGER);
        }

        for (var field : allFields) {
            if (field.scope() == ProfileFieldScope.GROUP) continue;
            if (!scopes.contains(field.scope())) continue;
            var config = field.config();
            if (!config.required()) continue;
            if (config.readonly()) continue;
            String val = valueMap.get(field.id());
            if (val == null || val.isBlank() || "\"\"".equals(val)) return false;
        }
        return true;
    }

    // -- Field Values --

    public List<ProfileFieldValue> findValues(int memberId) {
        return profileFieldRepository.findValues(memberId);
    }

    public List<ProfileFieldValue> setValues(int memberId, List<FieldValueEntry> entries, int changedBy) {
        Map<Integer, String> oldValues = profileFieldRepository.findValues(memberId).stream()
                .collect(Collectors.toMap(ProfileFieldValue::fieldId, v -> v.value() != null ? v.value() : "null"));

        List<String> changedFieldNames = new ArrayList<>();
        for (var entry : entries) {
            String oldValue = oldValues.getOrDefault(entry.fieldId(), "null");
            String newValue = entry.value() != null ? entry.value() : "null";

            profileFieldRepository.setValue(memberId, entry.fieldId(), entry.value());

            if (!Objects.equals(oldValue, newValue)) {
                recordChange(entry.fieldId(), memberId, oldValue, newValue, changedBy);
                profileFieldRepository.findById(entry.fieldId()).ifPresent(f -> changedFieldNames.add(f.name()));
            }
        }

        if (!changedFieldNames.isEmpty()) {
            notifyManagersOfChange(memberId, changedBy, changedFieldNames);
            log.info(
                    "Profile fields updated: member={}, changedBy={}, fields={}",
                    memberId,
                    changedBy,
                    changedFieldNames);
        }

        return profileFieldRepository.findValues(memberId);
    }

    public boolean deleteValue(int memberId, int fieldId) {
        boolean deleted = profileFieldRepository.deleteValue(memberId, fieldId);
        if (deleted) {
            log.info("Profile field value deleted: member={}, field={}", memberId, fieldId);
        } else {
            log.warn("Profile field value delete affected no rows: member={}, field={}", memberId, fieldId);
        }
        return deleted;
    }

    public List<ProfileFieldChangeRepository.MemberChangeSummary> findUnacknowledgedSummary(
            int stationId, int acknowledgedBy) {
        return changeRepository.findUnacknowledgedSummary(stationId, acknowledgedBy);
    }

    public List<ProfileFieldChange> findChanges(int memberId) {
        var changes = changeRepository.findByMember(memberId);
        if (changes.isEmpty()) return changes;

        var allAcks = changeRepository.findAcknowledgementsForMember(memberId);
        Map<Integer, List<ProfileFieldChangeAcknowledgement>> acksByChange =
                allAcks.stream().collect(Collectors.groupingBy(ProfileFieldChangeAcknowledgement::changeId));

        return changes.stream()
                .map(c -> new ProfileFieldChange(
                        c.id(),
                        c.fieldId(),
                        c.memberId(),
                        c.oldValue(),
                        c.newValue(),
                        c.changedBy(),
                        c.changedAt(),
                        c.requiresAcknowledgement(),
                        c.changedByName(),
                        c.fieldName(),
                        acksByChange.getOrDefault(c.id(), List.of()),
                        null))
                .toList();
    }

    public PagedChanges findChangesByStation(int stationId, int limit, int offset) {
        var changes = changeRepository.findByStation(stationId, limit, offset);
        int total = changeRepository.countByStation(stationId);
        if (changes.isEmpty()) return new PagedChanges(changes, total);

        var allAcks = new ArrayList<ProfileFieldChangeAcknowledgement>();
        for (var change : changes) {
            allAcks.addAll(changeRepository.findAcknowledgements(change.id()));
        }
        Map<Integer, List<ProfileFieldChangeAcknowledgement>> acksByChange =
                allAcks.stream().collect(Collectors.groupingBy(ProfileFieldChangeAcknowledgement::changeId));

        // Resolve member names
        var enriched = changes.stream()
                .map(c -> {
                    String memberName = stationMemberRepository
                            .findById(c.memberId())
                            .flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    return new ProfileFieldChange(
                            c.id(),
                            c.fieldId(),
                            c.memberId(),
                            c.oldValue(),
                            c.newValue(),
                            c.changedBy(),
                            c.changedAt(),
                            c.requiresAcknowledgement(),
                            c.changedByName(),
                            c.fieldName(),
                            acksByChange.getOrDefault(c.id(), List.of()),
                            memberName);
                })
                .toList();
        return new PagedChanges(enriched, total);
    }

    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, String comment) {
        var ack = changeRepository.acknowledge(changeId, acknowledgedBy, comment);
        log.info("Profile field change acknowledged: change={}, by={}", changeId, acknowledgedBy);
        return ack;
    }

    // -- Change History --

    public List<ProfileFieldChangeAcknowledgement> acknowledgeAll(int memberId, int acknowledgedBy, String comment) {
        var unacknowledgedIds = changeRepository.findUnacknowledgedChangeIds(memberId, acknowledgedBy);
        var result = new ArrayList<ProfileFieldChangeAcknowledgement>();
        for (int changeId : unacknowledgedIds) {
            result.add(changeRepository.acknowledge(changeId, acknowledgedBy, comment));
        }
        log.info(
                "Profile field changes acknowledged in bulk: member={}, by={}, count={}",
                memberId,
                acknowledgedBy,
                result.size());
        return result;
    }

    private void notifyManagersOfChange(int memberId, int changedBy, List<String> fieldNames) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return;

        var account = accountRepository.findById(member.accountId()).orElse(null);
        String memberName = account != null ? account.fullName() : "?";
        String fieldList = String.join(", ", fieldNames);

        var data = NotificationData.of(
                new NotificationParams.ProfileFieldChanged(memberName, fieldList),
                new NotificationData.NotificationLink("members-detail", Map.of("id", memberId)));

        var memberMgmtIds =
                stationMemberRepository
                        .findMembersWithPermission(member.stationId(), StationPermission.MEMBER_MANAGER)
                        .stream()
                        .map(StationMember::id)
                        .toList();

        notificationService.notifyMembersIfAbsent(
                memberMgmtIds, NotificationType.PROFILE_FIELD_CHANGED, data, changedBy);
    }

    /**
     * Record a change for any field value modification.
     * Merges with recent changes from the same person within the 5-minute window.
     * The notify flag is set based on the field's notifyOnChange config.
     */
    private void recordChange(int fieldId, int memberId, String oldValue, String newValue, int changedBy) {
        var field = profileFieldRepository.findById(fieldId).orElse(null);
        if (field == null) return;

        boolean requiresAcknowledgement = field.config().notifyOnChange();

        Instant cutoff = Instant.now().minus(MERGE_WINDOW);
        var recent = changeRepository.findRecentChange(fieldId, memberId, changedBy, cutoff);

        if (recent.isPresent()) {
            changeRepository.updateChangeNewValue(recent.get().id(), newValue);
        } else {
            changeRepository.create(fieldId, memberId, oldValue, newValue, changedBy, requiresAcknowledgement);
        }
    }
}
