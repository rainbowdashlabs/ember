/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterProfileFieldRepository;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
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
import io.javalin.http.BadRequestResponse;
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
    private final ClusterProfileFieldRepository clusterFieldRepository;

    @Inject
    public ProfileFieldService(
            ProfileFieldRepository profileFieldRepository,
            ProfileFieldChangeRepository changeRepository,
            NotificationService notificationService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            ClusterProfileFieldRepository clusterFieldRepository) {
        this.profileFieldRepository = profileFieldRepository;
        this.changeRepository = changeRepository;
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.clusterFieldRepository = clusterFieldRepository;
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

    public List<MergedField> findApplicableFields(int memberId) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return List.of();
        var scope = scopeForUserType(member.userType());
        if (scope == null) return List.of();
        return findMergedFields(member.stationId(), scope);
    }

    /**
     * The fields a station's profile shows in one scope: its own, and the ones its cluster asks for.
     *
     * <p>Unioned rather than returned as two lists, so the profile lays out as one form. Each entry carries
     * where it came from, because that decides two things the reader has to see: whether the station may
     * write the answer, and who to blame for the question.
     *
     * @param stationId the station
     * @param scope     which kind of member the fields apply to
     * @return the station's own fields first, then the cluster's
     */
    public List<MergedField> findMergedFields(int stationId, ProfileFieldScope scope) {
        List<MergedField> merged = new ArrayList<>();
        for (ProfileField field : findByStationAndScope(stationId, scope)) {
            merged.add(new MergedField(
                    field.id(),
                    field.name(),
                    field.fieldType(),
                    field.config(),
                    field.position(),
                    field.scope(),
                    FieldOrigin.STATION,
                    false));
        }
        for (var field : clusterFieldRepository.findForStation(stationId, scope)) {
            merged.add(new MergedField(
                    field.id(),
                    field.name(),
                    field.fieldType(),
                    field.config(),
                    field.position(),
                    field.scope(),
                    FieldOrigin.CLUSTER,
                    field.stationReadonly()));
        }
        return merged;
    }

    /**
     * @param origin          who asked
     * @param readonlyAtStation whether the people at the station may read the answer but not write it, which
     *                          only a cluster field can be
     */
    public record MergedField(
            int id,
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            int position,
            ProfileFieldScope scope,
            FieldOrigin origin,
            boolean readonlyAtStation) {}

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
        requireSingleBirthDate(stationId, fieldType, 0);
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
        var existing = profileFieldRepository.findById(id);
        if (existing.isEmpty()) {
            log.warn("Profile field update affected no rows: id={}", id);
            return Optional.empty();
        }
        requireSingleBirthDate(existing.get().stationId(), fieldType, id);
        if (profileFieldRepository.update(id, name, fieldType, config, position, keepOnArchive)) {
            log.info("Profile field updated: id={}, name='{}', type={}", id, name, fieldType);
            return profileFieldRepository.findById(id);
        }
        log.warn("Profile field update affected no rows: id={}", id);
        return Optional.empty();
    }

    /**
     * Rejects a second birth date field in the same station.
     *
     * @param stationId  the station the field belongs to
     * @param fieldType  the type the field is about to carry
     * @param excludedId the field being updated, so it does not clash with itself; 0 when creating
     * @throws BadRequestResponse if another field of the station already is the birth date
     */
    private void requireSingleBirthDate(int stationId, ProfileFieldType fieldType, int excludedId) {
        if (fieldType != ProfileFieldType.BIRTH_DATE) return;
        profileFieldRepository
                .findByStationAndType(stationId, ProfileFieldType.BIRTH_DATE)
                .filter(existing -> existing.id() != excludedId)
                .ifPresent(existing -> {
                    throw new BadRequestResponse(
                            "A birth date field already exists in this station: " + existing.name());
                });
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
            if (!field.fieldType().holdsValue()) continue;
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

    public List<MergedValue> findValues(int memberId) {
        List<MergedValue> values = new ArrayList<>();
        for (var value : profileFieldRepository.findValues(memberId)) {
            values.add(new MergedValue(value.fieldId(), value.value(), FieldOrigin.STATION));
        }
        for (var value : clusterFieldRepository.findValues(memberId)) {
            values.add(new MergedValue(value.fieldId(), value.value(), FieldOrigin.CLUSTER));
        }
        return values;
    }

    /**
     * An answer, and which table its question lives in.
     *
     * <p>The two id spaces are separate, so a bare field id says nothing on its own: the profile screen
     * carries the origin back with every answer it saves, and this is the shape it reads them in.
     *
     * @param fieldId the field the answer belongs to, in its own table
     * @param value   the answer
     * @param origin  who asked
     */
    public record MergedValue(int fieldId, String value, FieldOrigin origin) {}

    public List<MergedValue> setValues(int memberId, List<FieldValueEntry> entries, int changedBy) {
        Map<Integer, String> oldStation = profileFieldRepository.findValues(memberId).stream()
                .collect(Collectors.toMap(ProfileFieldValue::fieldId, v -> v.value() != null ? v.value() : "null"));
        Map<Integer, String> oldCluster = clusterFieldRepository.findValues(memberId).stream()
                .collect(Collectors.toMap(
                        ClusterProfileFieldRepository.Value::fieldId, v -> v.value() != null ? v.value() : "null"));

        List<String> changedFieldNames = new ArrayList<>();
        for (var entry : entries) {
            String newValue = entry.value() != null ? entry.value() : "null";
            if (entry.origin() == FieldOrigin.CLUSTER) {
                writeClusterAnswer(memberId, entry, oldCluster, newValue, changedBy, changedFieldNames);
                continue;
            }

            String oldValue = oldStation.getOrDefault(entry.fieldId(), "null");
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

        return findValues(memberId);
    }

    /**
     * Saves one answer to a question the cluster asked, when the cluster leaves it to the station.
     *
     * <p>A field the cluster keeps to itself is simply not written: the station's screen shows it without a
     * control, so an entry naming one is a stale form rather than somebody trying something, and refusing
     * the whole save would lose the answers beside it.
     *
     * <p>The change is recorded like any other, which is what puts it in front of the people at the station
     * who acknowledge changes. What is not raised is the cluster's own notification: that one says the
     * cluster changed something, and here the station did.
     */
    private void writeClusterAnswer(
            int memberId,
            FieldValueEntry entry,
            Map<Integer, String> oldValues,
            String newValue,
            int changedBy,
            List<String> changedFieldNames) {
        var field = clusterFieldRepository
                .findById(entry.fieldId())
                .filter(candidate -> !candidate.stationReadonly())
                .orElse(null);
        if (field == null) return;

        String oldValue = oldValues.getOrDefault(field.id(), "null");
        if (Objects.equals(oldValue, newValue)) return;

        clusterFieldRepository.setValue(memberId, field.id(), entry.value());
        changeRepository.createForClusterField(
                field.id(),
                memberId,
                oldValue,
                newValue,
                changedBy,
                field.config().notifyOnChange());
        changedFieldNames.add(field.name());
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
                        c.clusterFieldId(),
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

    /**
     * The changes of the given members, for a caller who may not see the whole station.
     *
     * @param memberIds the members the caller is allowed to see
     * @param limit     page size
     * @param offset    page offset
     * @return the page of changes, enriched the same way the station-wide list is
     */
    public PagedChanges findChangesByMembers(List<Integer> memberIds, int limit, int offset) {
        return enrich(
                changeRepository.findByMembers(memberIds, limit, offset), changeRepository.countByMembers(memberIds));
    }

    /**
     * The member a change was recorded for.
     *
     * @param changeId the change identifier
     * @return the member, empty if there is no such change
     */
    public Optional<Integer> findMemberOfChange(int changeId) {
        return changeRepository.findMemberOfChange(changeId);
    }

    public PagedChanges findChangesByStation(int stationId, int limit, int offset) {
        return enrich(
                changeRepository.findByStation(stationId, limit, offset), changeRepository.countByStation(stationId));
    }

    /**
     * Fills a page of changes with their acknowledgements and the names of the members they belong to.
     */
    private PagedChanges enrich(List<ProfileFieldChange> changes, int total) {
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
                            c.clusterFieldId(),
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
