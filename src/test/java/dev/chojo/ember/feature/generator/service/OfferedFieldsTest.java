/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.service.OfferedFields.SectionedField;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldAssignment;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldTarget;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which profile questions a template can name for the member and which for a guardian, and the heading
 * each stands below.
 */
class OfferedFieldsTest {
    private static final ProfileField MEDICAL = field(1, "Medizinisches", FieldType.SECTION);
    private static final ProfileField ALLERGIES = field(2, "Allergien", FieldType.TEXT);
    private static final ProfileField SCHOOL = field(3, "Schule", FieldType.TEXT);
    private static final ProfileField EMPLOYER = field(4, "Arbeitgeber", FieldType.TEXT);
    private static final ProfileField SWIMMER = field(5, "Schwimmabzeichen", FieldType.BOOLEAN);
    private static final ProfileField SPACER = field(6, "Abstand", FieldType.SPACER);
    private static final ProfileField CONTACT = field(7, "Kontakt", FieldType.SECTION);
    private static final List<ProfileField> FIELDS =
            List.of(MEDICAL, ALLERGIES, SCHOOL, EMPLOYER, SWIMMER, SPACER, CONTACT);

    private static ProfileField field(int id, String name, FieldType type) {
        return new ProfileField(id, 1, name, type, ProfileFieldConfig.empty(), false, false, null, false);
    }

    private static ProfileFieldAssignment role(ProfileField field, ProfileFieldScope role, int position) {
        return new ProfileFieldAssignment(
                field.id() * 10 + role.ordinal(),
                field.id(),
                ProfileFieldTarget.ROLE,
                role,
                null,
                position,
                null,
                null,
                null);
    }

    private static ProfileFieldAssignment group(ProfileField field, int groupId, int position) {
        return new ProfileFieldAssignment(
                field.id() * 100 + groupId,
                field.id(),
                ProfileFieldTarget.GROUP,
                null,
                groupId,
                position,
                null,
                null,
                null);
    }

    private static OfferedFields offered(ProfileFieldAssignment... assignments) {
        return OfferedFields.of(FIELDS, Arrays.asList(assignments));
    }

    private static List<String> names(List<SectionedField> fields) {
        return fields.stream()
                .map(sectioned -> sectioned.field().name() + "@" + sectioned.section())
                .toList();
    }

    @Test
    void aQuestionForGuardiansOnlyIsOfferedForTheGuardiansAlone() {
        var offered = offered(
                role(SCHOOL, ProfileFieldScope.MEMBER, 1),
                role(EMPLOYER, ProfileFieldScope.GUARDIAN, 1),
                role(SWIMMER, ProfileFieldScope.MEMBER, 2),
                role(SWIMMER, ProfileFieldScope.GUARDIAN, 2));

        assertEquals(List.of("Schule@null", "Schwimmabzeichen@null"), names(offered.member()));
        assertEquals(List.of("Arbeitgeber@null", "Schwimmabzeichen@null"), names(offered.guardians()));
    }

    @Test
    void aGroupAndEveryOtherKindOfMemberAskTheMember() {
        var offered = offered(
                role(SCHOOL, ProfileFieldScope.TRIAL, 1),
                role(SWIMMER, ProfileFieldScope.MANAGER, 1),
                group(EMPLOYER, 9, 1));

        assertEquals(List.of("Schule@null", "Schwimmabzeichen@null", "Arbeitgeber@null"), names(offered.member()));
        assertEquals(List.of(), offered.guardians());
    }

    @Test
    void aQuestionStandsBelowTheHeadingAboveItOnTheMembersOwnFormFirst() {
        var offered = offered(
                role(SCHOOL, ProfileFieldScope.MEMBER, 1),
                role(MEDICAL, ProfileFieldScope.MEMBER, 2),
                role(SPACER, ProfileFieldScope.MEMBER, 3),
                role(ALLERGIES, ProfileFieldScope.MEMBER, 4),
                role(CONTACT, ProfileFieldScope.TRIAL, 1),
                role(ALLERGIES, ProfileFieldScope.TRIAL, 2),
                role(SWIMMER, ProfileFieldScope.TRIAL, 3),
                role(CONTACT, ProfileFieldScope.GUARDIAN, 1),
                role(EMPLOYER, ProfileFieldScope.GUARDIAN, 2));

        assertEquals(
                List.of("Schule@null", "Allergien@Medizinisches", "Schwimmabzeichen@Kontakt"), names(offered.member()));
        assertEquals(List.of("Arbeitgeber@Kontakt"), names(offered.guardians()));
    }

    @Test
    void anUnassignedQuestionIsOfferedForNobody() {
        var offered = offered();

        assertEquals(List.of(), offered.member());
        assertEquals(List.of(), offered.guardians());
    }
}
