/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What is known about the members behind a form's results, for counting them by group.
 *
 * <p>A response counts for the member it is for. When a guardian answers for a child, it is the
 * child's type, groups and age that count, because the answer is the child's.
 *
 * <p>User type, groups, tags and profile answers are the member's current ones: nothing records what
 * they were on the day of answering, so a member who has changed group since counts in the new one.
 * Age is the exception. For a response it is counted to the day the response was submitted, which is
 * what "the age of those who voted" means and which does not drift as time passes. For a member who
 * has not answered yet it is counted to today.
 *
 * <p>Everything is read in a handful of queries for the whole form, never one per member.
 */
@Singleton
public class FormRespondents {
    /** The kinds of profile field that fall into a few groups and so can be grouped and filtered by. */
    static final Set<ProfileFieldType> GROUPABLE_FIELDS =
            Set.of(ProfileFieldType.ENUM, ProfileFieldType.BOOLEAN, ProfileFieldType.NUMBER);

    private final StationMemberRepository members;
    private final MemberGroupRepository groups;
    private final UserTagRepository tags;
    private final ProfileFieldRepository profileFields;
    private final StationRepository stations;

    @Inject
    public FormRespondents(
            StationMemberRepository members,
            MemberGroupRepository groups,
            UserTagRepository tags,
            ProfileFieldRepository profileFields,
            StationRepository stations) {
        this.members = members;
        this.groups = groups;
        this.tags = tags;
        this.profileFields = profileFields;
        this.stations = stations;
    }

    /**
     * What is known about the member behind one response, or about one member who has not answered.
     *
     * <p>A response without a member, one sent without signing in, has no type, no groups, no tags,
     * no answers and no age; it lands in the "none" group of whatever the results are grouped by.
     *
     * @param id          the response id, or the member id for a member who has not answered
     * @param userType    the member's type, or {@code null} without a member
     * @param groupIds    the member's groups
     * @param tagIds      the member's tags
     * @param fieldValues the member's answers to the groupable profile fields, by field id
     * @param age         the member's age on the day that counts, or {@code null} where unknown
     */
    public record Respondent(
            int id,
            StationUserType userType,
            Set<Integer> groupIds,
            Set<Integer> tagIds,
            Map<Integer, String> fieldValues,
            Integer age) {}

    private record Facts(
            StationUserType userType,
            Set<Integer> groupIds,
            Set<Integer> tagIds,
            Map<Integer, String> fieldValues,
            LocalDate born) {
        Respondent on(int id, LocalDate day) {
            Integer age = born == null || day.isBefore(born)
                    ? null
                    : Period.between(born, day).getYears();
            return new Respondent(id, userType, groupIds, tagIds, fieldValues, age);
        }
    }

    /**
     * What is known about the member behind each response, with age counted to the day of answering.
     *
     * @param stationId the station the form belongs to
     * @param responses the form's responses
     * @return one respondent per response, in the order of the responses, keyed by response id
     */
    public List<Respondent> ofResponses(int stationId, List<FormResponse> responses) {
        var memberIds = responses.stream()
                .map(FormResponse::memberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var facts = factsOf(stationId, memberIds);
        var zone = zoneOf(stationId);
        return responses.stream()
                .map(response -> {
                    var known = response.memberId() == null ? null : facts.get(response.memberId());
                    if (known == null) return new Respondent(response.id(), null, Set.of(), Set.of(), Map.of(), null);
                    return known.on(response.id(), LocalDate.ofInstant(response.submittedAt(), zone));
                })
                .toList();
    }

    /**
     * What is known about each of these members, with age counted to today.
     *
     * @param stationId the station the members belong to
     * @param memberIds the members
     * @return one respondent per member found, keyed by member id
     */
    public List<Respondent> ofMembers(int stationId, Collection<Integer> memberIds) {
        var facts = factsOf(stationId, Set.copyOf(memberIds));
        var today = LocalDate.now(zoneOf(stationId));
        return memberIds.stream()
                .filter(facts::containsKey)
                .map(memberId -> facts.get(memberId).on(memberId, today))
                .toList();
    }

    /**
     * The profile fields of a station that results can be grouped by.
     *
     * @param stationFields every profile field of the station
     * @return the fields of a groupable kind
     */
    static List<ProfileField> groupableFields(List<ProfileField> stationFields) {
        return stationFields.stream()
                .filter(field -> GROUPABLE_FIELDS.contains(field.fieldType()))
                .toList();
    }

    private Map<Integer, Facts> factsOf(int stationId, Set<Integer> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        var groupsOf = groups.findGroupIdsOfMembers(memberIds);
        var tagsOf = tags.findTagIdsOfMembers(memberIds);
        var stationFields = profileFields.findByStation(stationId);
        var answers = answersOf(groupableFields(stationFields), memberIds);
        var birthDates = birthDatesOf(stationFields, memberIds);
        Map<Integer, Facts> facts = new HashMap<>();
        for (StationMember member : members.findByStation(stationId, true)) {
            int id = member.id();
            if (!memberIds.contains(id)) continue;
            facts.put(
                    id,
                    new Facts(
                            member.userType(),
                            groupsOf.getOrDefault(id, Set.of()),
                            tagsOf.getOrDefault(id, Set.of()),
                            answers.getOrDefault(id, Map.of()),
                            birthDates.get(id)));
        }
        return facts;
    }

    private ZoneId zoneOf(int stationId) {
        return stations.findById(stationId).map(StationFormat::timezoneOf).orElse(ZoneId.systemDefault());
    }

    private Map<Integer, Map<Integer, String>> answersOf(List<ProfileField> fields, Set<Integer> memberIds) {
        Map<Integer, Map<Integer, String>> byMember = new HashMap<>();
        for (var field : fields) {
            for (var value : profileFields.findValuesOfField(field.id())) {
                if (!memberIds.contains(value.memberId())) continue;
                var answer = value.plainValue();
                if (answer == null || answer.isBlank()) continue;
                byMember.computeIfAbsent(value.memberId(), _ -> new HashMap<>()).put(field.id(), answer);
            }
        }
        return byMember;
    }

    private Map<Integer, LocalDate> birthDatesOf(List<ProfileField> stationFields, Set<Integer> memberIds) {
        var birthDateField = stationFields.stream()
                .filter(field -> field.fieldType() == ProfileFieldType.BIRTH_DATE)
                .findFirst();
        if (birthDateField.isEmpty()) return Map.of();
        Map<Integer, LocalDate> born = new HashMap<>();
        for (var value : profileFields.findValuesOfField(birthDateField.get().id())) {
            if (!memberIds.contains(value.memberId())) continue;
            var day = dayOf(value.plainValue());
            if (day != null) born.put(value.memberId(), day);
        }
        return born;
    }

    private static LocalDate dayOf(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return LocalDate.parse(stored.length() > 10 ? stored.substring(0, 10) : stored);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
