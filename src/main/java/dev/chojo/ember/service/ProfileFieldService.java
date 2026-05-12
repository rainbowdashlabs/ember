/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.ProfileField;
import dev.chojo.ember.entity.ProfileFieldChange;
import dev.chojo.ember.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.entity.ProfileFieldConfig;
import dev.chojo.ember.entity.ProfileFieldScope;
import dev.chojo.ember.entity.ProfileFieldValue;
import dev.chojo.ember.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.repository.ProfileFieldRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class ProfileFieldService {
    private static final Duration MERGE_WINDOW = Duration.ofMinutes(5);

    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldChangeRepository changeRepository;

    @Inject
    public ProfileFieldService(
            ProfileFieldRepository profileFieldRepository, ProfileFieldChangeRepository changeRepository) {
        this.profileFieldRepository = profileFieldRepository;
        this.changeRepository = changeRepository;
    }

    // -- Field Definitions --

    public List<ProfileField> findByStation(int stationId) {
        return profileFieldRepository.findByStation(stationId);
    }

    public List<ProfileField> findByStationAndScope(int stationId, ProfileFieldScope scope) {
        return profileFieldRepository.findByStationAndScope(stationId, scope);
    }

    public Optional<ProfileField> findById(int id) {
        return profileFieldRepository.findById(id);
    }

    public ProfileField create(
            int stationId, String name, String fieldType, String config, int position, ProfileFieldScope scope) {
        return profileFieldRepository.create(stationId, name, fieldType, config, position, scope);
    }

    public Optional<ProfileField> update(int id, String name, String fieldType, String config, int position) {
        if (profileFieldRepository.update(id, name, fieldType, config, position)) {
            return profileFieldRepository.findById(id);
        }
        return Optional.empty();
    }

    public boolean delete(int id) {
        return profileFieldRepository.delete(id);
    }

    // -- Field Values --

    public List<ProfileFieldValue> findValues(int memberId) {
        return profileFieldRepository.findValues(memberId);
    }

    public List<ProfileFieldValue> setValues(int memberId, List<FieldValueEntry> entries, int changedBy) {
        Map<Integer, String> oldValues = profileFieldRepository.findValues(memberId).stream()
                .collect(Collectors.toMap(ProfileFieldValue::fieldId, v -> v.value() != null ? v.value() : ""));

        for (var entry : entries) {
            String oldValue = oldValues.getOrDefault(entry.fieldId(), "");
            String newValue = entry.value() != null ? entry.value() : "";

            profileFieldRepository.setValue(memberId, entry.fieldId(), entry.value());

            if (!Objects.equals(oldValue, newValue)) {
                recordChange(entry.fieldId(), memberId, oldValue, newValue, changedBy);
            }
        }
        return profileFieldRepository.findValues(memberId);
    }

    /**
     * Record a change for any field value modification.
     * Merges with recent changes from the same person within the 5-minute window.
     * The notify flag is set based on the field's notifyOnChange config.
     */
    private void recordChange(int fieldId, int memberId, String oldValue, String newValue, int changedBy) {
        var field = profileFieldRepository.findById(fieldId).orElse(null);
        if (field == null) return;

        boolean requiresAcknowledgement =
                ProfileFieldConfig.parse(field.config()).notifyOnChange();

        Instant cutoff = Instant.now().minus(MERGE_WINDOW);
        var recent = changeRepository.findRecentChange(fieldId, memberId, changedBy, cutoff);

        if (recent.isPresent()) {
            changeRepository.updateChangeNewValue(recent.get().id(), newValue);
        } else {
            changeRepository.create(fieldId, memberId, oldValue, newValue, changedBy, requiresAcknowledgement);
        }
    }

    public boolean deleteValue(int memberId, int fieldId) {
        return profileFieldRepository.deleteValue(memberId, fieldId);
    }

    // -- Change History --

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
                        acksByChange.getOrDefault(c.id(), List.of())))
                .toList();
    }

    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, String comment) {
        return changeRepository.acknowledge(changeId, acknowledgedBy, comment);
    }

    public List<ProfileFieldChangeAcknowledgement> acknowledgeAll(int memberId, int acknowledgedBy, String comment) {
        var unacknowledgedIds = changeRepository.findUnacknowledgedChangeIds(memberId, acknowledgedBy);
        var result = new ArrayList<ProfileFieldChangeAcknowledgement>();
        for (int changeId : unacknowledgedIds) {
            result.add(changeRepository.acknowledge(changeId, acknowledgedBy, comment));
        }
        return result;
    }

    public record FieldValueEntry(int fieldId, String value) {}
}
