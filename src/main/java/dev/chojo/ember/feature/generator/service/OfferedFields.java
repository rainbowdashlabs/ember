/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldAssignment;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The profile questions of a station a template can name for the member, and those it can name for a
 * guardian, each under the heading it stands below on the form.
 *
 * <p>A question is put to people by its assignments. The member's questions are the ones assigned to a
 * group or to any kind of member other than a guardian; a guardian's are the ones assigned to guardians.
 * A question asked of guardians only has no answer of the member's to print, and one never asked of
 * guardians none of theirs.
 *
 * <p>The forms are read in a fixed order, the member's own first, then those of the other kinds of member,
 * the guardians' and the groups', and a question takes the heading it stands below on the first form that
 * asks it. A question above the first heading of that form stands below none.
 *
 * @param member    the questions offered for the member
 * @param guardians the questions offered for each guardian
 */
public record OfferedFields(List<SectionedField> member, List<SectionedField> guardians) {
    private static final List<ProfileFieldScope> FORM_ORDER = List.of(
            ProfileFieldScope.MEMBER,
            ProfileFieldScope.TRIAL,
            ProfileFieldScope.TEAM,
            ProfileFieldScope.MANAGER,
            ProfileFieldScope.GUARDIAN);

    /**
     * A profile question as a template can name it.
     *
     * @param field   the question
     * @param section the heading it stands below, null where it stands below none
     */
    public record SectionedField(
            ProfileField field, @Nullable String section) {}

    private record Form(
            @Nullable ProfileFieldScope role, @Nullable Integer groupId) {}

    /**
     * Sorts the questions of a station by whom they are put to.
     *
     * @param fields      every question of the station, headings included
     * @param assignments every assignment of those questions
     * @return the questions offered for the member and for the guardians
     */
    public static OfferedFields of(List<ProfileField> fields, List<ProfileFieldAssignment> assignments) {
        var byId = fields.stream().collect(Collectors.toMap(ProfileField::id, Function.identity()));
        return new OfferedFields(
                sectioned(byId, assignments, assignment -> assignment.role() != ProfileFieldScope.GUARDIAN),
                sectioned(byId, assignments, assignment -> assignment.role() == ProfileFieldScope.GUARDIAN));
    }

    private static List<SectionedField> sectioned(
            Map<Integer, ProfileField> fields,
            List<ProfileFieldAssignment> assignments,
            Predicate<ProfileFieldAssignment> audience) {
        var forms = assignments.stream()
                .filter(audience)
                .sorted(Comparator.comparingInt(OfferedFields::formRank)
                        .thenComparingInt(assignment -> Objects.requireNonNullElse(assignment.groupId(), 0))
                        .thenComparingInt(ProfileFieldAssignment::position)
                        .thenComparingInt(ProfileFieldAssignment::fieldId))
                .collect(Collectors.groupingBy(
                        assignment -> new Form(assignment.role(), assignment.groupId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        var placed = new LinkedHashMap<Integer, SectionedField>();
        for (var form : forms.values()) {
            String section = null;
            for (var assignment : form) {
                var field = fields.get(assignment.fieldId());
                if (field == null) continue;
                if (field.fieldType() == FieldType.SECTION) section = field.name();
                else if (field.fieldType().holdsValue()) {
                    placed.putIfAbsent(field.id(), new SectionedField(field, section));
                }
            }
        }
        return List.copyOf(placed.values());
    }

    private static int formRank(ProfileFieldAssignment assignment) {
        return assignment.role() == null ? FORM_ORDER.size() : FORM_ORDER.indexOf(assignment.role());
    }
}
