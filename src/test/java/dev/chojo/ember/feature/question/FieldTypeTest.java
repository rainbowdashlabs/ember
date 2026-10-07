/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one list of field types: the kind behind each, the narrowing of member fields, and which of
 * them every feature offers.
 */
class FieldTypeTest {

    private static final Map<FieldType, Optional<QuestionKind>> KINDS = kinds();

    private static Map<FieldType, Optional<QuestionKind>> kinds() {
        var kinds = new EnumMap<FieldType, Optional<QuestionKind>>(FieldType.class);
        kinds.put(FieldType.TEXT, Optional.of(QuestionKind.TEXT));
        kinds.put(FieldType.LONG_TEXT, Optional.of(QuestionKind.LONG_TEXT));
        kinds.put(FieldType.NUMBER, Optional.of(QuestionKind.NUMBER));
        kinds.put(FieldType.DATE, Optional.of(QuestionKind.DATE));
        kinds.put(FieldType.TIME, Optional.of(QuestionKind.TIME));
        kinds.put(FieldType.BOOLEAN, Optional.of(QuestionKind.BOOLEAN));
        kinds.put(FieldType.CHOICE, Optional.of(QuestionKind.CHOICE));
        kinds.put(FieldType.GENDER, Optional.of(QuestionKind.CHOICE));
        kinds.put(FieldType.URL, Optional.of(QuestionKind.URL));
        kinds.put(FieldType.MEMBER, Optional.of(QuestionKind.MEMBER));
        kinds.put(FieldType.MEMBER_LIST, Optional.of(QuestionKind.MEMBER_LIST));
        kinds.put(FieldType.BIRTH_DATE, Optional.of(QuestionKind.DATE));
        kinds.put(FieldType.EXPIRY_DATE, Optional.of(QuestionKind.DATE));
        kinds.put(FieldType.AGE, Optional.empty());
        kinds.put(FieldType.SECTION, Optional.empty());
        kinds.put(FieldType.SPACER, Optional.empty());
        kinds.put(FieldType.LOCATION, Optional.of(QuestionKind.TEXT));
        kinds.put(FieldType.MEMBER_OF_GROUP, Optional.of(QuestionKind.MEMBER));
        kinds.put(FieldType.MEMBER_LIST_OF_GROUP, Optional.of(QuestionKind.MEMBER_LIST));
        kinds.put(FieldType.MEMBER_OF_TYPE, Optional.of(QuestionKind.MEMBER));
        kinds.put(FieldType.MEMBER_LIST_OF_TYPE, Optional.of(QuestionKind.MEMBER_LIST));
        kinds.put(FieldType.MEMBER_OF_TAG, Optional.of(QuestionKind.MEMBER));
        kinds.put(FieldType.MEMBER_LIST_OF_TAG, Optional.of(QuestionKind.MEMBER_LIST));
        kinds.put(FieldType.LANE_ASSIGNEE, Optional.of(QuestionKind.MEMBER));
        return kinds;
    }

    /** A number with a fraction is a step setting on a number, not a type of its own. */
    @Test
    void theVocabularyHasTwentyFourNamesAndNoDecimal() {
        assertEquals(24, FieldType.values().length);
        assertThrows(IllegalArgumentException.class, () -> FieldType.valueOf("DECIMAL"));
    }

    @Test
    void everyTypeHasItsKind() {
        for (FieldType type : FieldType.values()) {
            assertEquals(KINDS.get(type), type.kind(), type.name());
            assertEquals(KINDS.get(type).isPresent(), type.holdsValue(), type.name());
        }
    }

    @Test
    void onlyMemberTypesNameMembers() {
        for (FieldType type : FieldType.values()) {
            boolean expected = type.name().startsWith("MEMBER") || type == FieldType.LANE_ASSIGNEE;
            assertEquals(expected, type.namesMembers(), type.name());
        }
        assertFalse(FieldType.AGE.namesMembers());
    }

    @Test
    void memberTypesCarryTheirNarrowing() {
        assertEquals(MemberConstraint.GROUP, FieldType.MEMBER_OF_GROUP.constraint());
        assertEquals(MemberConstraint.GROUP, FieldType.MEMBER_LIST_OF_GROUP.constraint());
        assertEquals(MemberConstraint.USER_TYPE, FieldType.MEMBER_OF_TYPE.constraint());
        assertEquals(MemberConstraint.USER_TYPE, FieldType.MEMBER_LIST_OF_TYPE.constraint());
        assertEquals(MemberConstraint.TAG, FieldType.MEMBER_OF_TAG.constraint());
        assertEquals(MemberConstraint.TAG, FieldType.MEMBER_LIST_OF_TAG.constraint());
        assertEquals(MemberConstraint.NONE, FieldType.MEMBER.constraint());
        assertEquals(MemberConstraint.NONE, FieldType.LANE_ASSIGNEE.constraint());
        assertEquals(MemberConstraint.NONE, FieldType.TEXT.constraint());
    }

    @Test
    void theProfileOffersWhatItOffersToday() {
        assertEquals(
                EnumSet.of(
                        FieldType.TEXT,
                        FieldType.NUMBER,
                        FieldType.DATE,
                        FieldType.BOOLEAN,
                        FieldType.CHOICE,
                        FieldType.GENDER,
                        FieldType.AGE,
                        FieldType.BIRTH_DATE,
                        FieldType.EXPIRY_DATE,
                        FieldType.SECTION,
                        FieldType.SPACER),
                FieldTypes.PROFILE);
    }

    /** The server refuses a date of birth on an association, and nothing else of the profile's types. */
    @Test
    void theAssociationOffersTheProfileTypesButTheDateOfBirth() {
        var expected = EnumSet.copyOf(FieldTypes.PROFILE);
        expected.remove(FieldType.BIRTH_DATE);
        assertEquals(expected, FieldTypes.ASSOCIATION);
    }

    @Test
    void theInventoryOffersWhatItOffersToday() {
        assertEquals(
                EnumSet.of(FieldType.DATE, FieldType.CHOICE, FieldType.TEXT, FieldType.NUMBER, FieldType.BOOLEAN),
                FieldTypes.INVENTORY);
    }

    @Test
    void theBoardOffersWhatItOffersToday() {
        assertEquals(shared(BoardFieldType.values()), FieldTypes.BOARD);
    }

    @Test
    void anAppointmentOffersWhatItOffersToday() {
        assertEquals(shared(EventFieldType.values()), FieldTypes.APPOINTMENT);
    }

    /** The eight types the registration question editor offers, not all seventeen of an appointment. */
    @Test
    void aRegistrationQuestionOffersWhatItsEditorOffers() {
        assertEquals(
                EnumSet.of(
                        FieldType.TEXT,
                        FieldType.LONG_TEXT,
                        FieldType.NUMBER,
                        FieldType.BOOLEAN,
                        FieldType.CHOICE,
                        FieldType.DATE,
                        FieldType.TIME,
                        FieldType.MEMBER),
                FieldTypes.REGISTRATION);
        assertTrue(FieldTypes.APPOINTMENT.containsAll(FieldTypes.REGISTRATION));
    }

    @Test
    void anAttendanceSheetOffersWhatItOffersToday() {
        assertEquals(
                EnumSet.of(
                        FieldType.TEXT,
                        FieldType.NUMBER,
                        FieldType.DATE,
                        FieldType.TIME,
                        FieldType.BOOLEAN,
                        FieldType.CHOICE,
                        FieldType.URL,
                        FieldType.LONG_TEXT,
                        FieldType.MEMBER,
                        FieldType.MEMBER_LIST,
                        FieldType.MEMBER_OF_GROUP,
                        FieldType.MEMBER_LIST_OF_GROUP),
                FieldTypes.ATTENDANCE);
    }

    @Test
    void aWaitingListOffersWhatItOffersToday() {
        assertEquals(
                EnumSet.of(
                        FieldType.TEXT,
                        FieldType.NUMBER,
                        FieldType.DATE,
                        FieldType.BOOLEAN,
                        FieldType.CHOICE,
                        FieldType.BIRTH_DATE),
                FieldTypes.WAITING_LIST);
    }

    @Test
    void theSetsCannotBeChangedFromOutside() {
        assertThrows(UnsupportedOperationException.class, () -> FieldTypes.PROFILE.add(FieldType.URL));
    }

    /** Every wire name stands for one shared type and is found again from it. */
    @Test
    void wireNamesMapOntoTheSharedTypes() {
        for (var type : EventFieldType.values()) {
            assertEquals(type, EventFieldType.of(type.fieldType()));
        }
        for (var type : BoardFieldType.values()) {
            assertEquals(type, BoardFieldType.of(type.fieldType()));
        }
        assertEquals(EventFieldType.STRING, EventFieldType.of(FieldType.TEXT));
        assertEquals(EventFieldType.TEXTAREA, EventFieldType.of(FieldType.LONG_TEXT));
        assertEquals(BoardFieldType.ENUM, BoardFieldType.of(FieldType.CHOICE));
        assertThrows(IllegalArgumentException.class, () -> BoardFieldType.of(FieldType.URL));
    }

    @Test
    void anOldSpellingIsNoLongerRead() {
        assertThrows(IllegalArgumentException.class, () -> FieldType.valueOf("STRING"));
        assertThrows(IllegalArgumentException.class, () -> EventFieldType.of(FieldType.AGE));
        assertThrows(IllegalArgumentException.class, () -> FieldType.valueOf("ENUM"));
    }

    /** Every name a feature spells today, under the shared one it means. */
    private static Set<FieldType> shared(Enum<?>[] featureTypes) {
        return Arrays.stream(featureTypes)
                .map(type -> switch (type.name()) {
                    case "STRING" -> FieldType.TEXT;
                    case "TEXTAREA" -> FieldType.LONG_TEXT;
                    case "ENUM" -> FieldType.CHOICE;
                    default -> FieldType.valueOf(type.name());
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FieldType.class)));
    }
}
