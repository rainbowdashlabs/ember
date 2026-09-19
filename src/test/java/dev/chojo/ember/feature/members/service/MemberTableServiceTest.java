/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTableQuestion;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a table of people shows, and to whom.
 *
 * <p>The whole point of drawing the table in one place is that the answer to the second question is
 * worked out once. A question the station asks only of its team is on the sheet for somebody who
 * keeps the register and absent for somebody who merely may read it, and absent means gone rather
 * than blank: a column of blanks says that something was withheld about these people in particular.
 */
class MemberTableServiceTest extends RepositoryTestBase {
    private static MemberTableService service;
    private static Station station;
    private static StationMember member;
    private static int openFieldId;
    private static int teamFieldId;

    @BeforeAll
    static void setup() {
        service = new MemberTableService(profileFieldRepo, stationMemberRepo);
        station = stationRepo.create("TabellenWache");
        Account account = accountRepo.create("tabelle@test.com", "Toni", "Tabelle");
        member = stationMemberRepo.create(station.id(), account.id());

        var open = profileFieldRepo.create(
                station.id(), "Schuhgröße", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(open.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), open.id(), StringNode.valueOf("43"));
        openFieldId = open.id();

        var team = profileFieldRepo.create(
                station.id(),
                "Ausweisnummer",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(team.id(), ProfileFieldScope.TEAM, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), team.id(), StringNode.valueOf("A-4711"));
        teamFieldId = team.id();
    }

    private static MemberTablePeople thisMember() {
        return MemberTablePeople.of(List.of(member.id()));
    }

    private static List<MemberTableColumn> bothQuestions() {
        return List.of(
                MemberTableColumn.builtin("name"),
                MemberTableColumn.profileField(openFieldId),
                MemberTableColumn.profileField(teamFieldId));
    }

    /** Somebody who keeps the register sees what the station asked of its team. */
    @Test
    void whoeverKeepsTheRegisterFindsTheQuestionsMeantForThem() {
        var table = service.build(
                station,
                thisMember(),
                bothQuestions(),
                Set.of(StationPermission.USER, StationPermission.MEMBER_MANAGER),
                java.util.Map.of());

        var labels = table.columns().stream().map(c -> c.label()).toList();
        assertTrue(labels.contains("Ausweisnummer"), "a question asked of the team reaches whoever keeps the register");
        assertEquals(1, table.rows().size());
        assertTrue(table.rows().getFirst().values().contains("A-4711"), "and so does the answer to it");
    }

    /**
     * A plain member does not, and the column is gone rather than empty.
     *
     * <p>This is the half that matters. A table that answered with the column and no values would
     * tell the room that there is an identity card number and that it was kept from them, which about
     * a named person is itself worth keeping quiet.
     */
    @Test
    void aPlainMemberFindsNoTraceOfIt() {
        var table = service.build(
                station, thisMember(), bothQuestions(), Set.of(StationPermission.USER), java.util.Map.of());

        var labels = table.columns().stream().map(c -> c.label()).toList();
        assertTrue(labels.contains("Schuhgröße"), "what is asked of everybody is still there");
        assertFalse(labels.contains("Ausweisnummer"), "and what is not is not a column at all");
        assertEquals(labels.size(), table.rows().getFirst().values().size(), "every row is as wide as the header");
        assertFalse(
                table.rows().getFirst().values().contains("A-4711"),
                "the answer does not travel under another column either");
    }

    /** Asking for a question by id is not a way around the scopes. */
    @Test
    void namingTheQuestionOutrightChangesNothing() {
        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(teamFieldId)),
                Set.of(StationPermission.USER),
                java.util.Map.of());

        assertTrue(table.columns().isEmpty(), "a question they may not read is no column, however it is asked for");
        assertTrue(table.rows().getFirst().values().isEmpty(), "and the row has nothing in it");
    }

    /** The columns a picker offers are the columns the table will draw, or a station learns to distrust it. */
    @Test
    void whatIsOfferedIsWhatIsDrawn() {
        var offeredToTeam = service.offerableColumns(
                station.id(), Set.of(StationPermission.USER, StationPermission.MEMBER_MANAGER));
        var offeredToMember = service.offerableColumns(station.id(), Set.of(StationPermission.USER));

        assertTrue(
                offeredToTeam.stream().anyMatch(c -> "Ausweisnummer".equals(c.label())),
                "the question is offered to whoever may read it");
        assertFalse(
                offeredToMember.stream().anyMatch(c -> "Ausweisnummer".equals(c.label())),
                "and never offered to somebody the table would drop it for");
    }

    /** Every column says what it holds, so a screen never has to guess a date from how its cells read. */
    @Test
    void everyColumnSaysWhatItHolds() {
        var table = service.build(
                station,
                thisMember(),
                List.of(
                        MemberTableColumn.builtin("name"),
                        MemberTableColumn.builtin("joinDate"),
                        MemberTableColumn.builtin("memberType")),
                Set.of(StationPermission.USER),
                Map.of());

        var types = table.columns().stream()
                .map(MemberTable.MemberTableHeader::type)
                .toList();
        assertEquals(List.of(MemberTableCellType.TEXT, MemberTableCellType.DATE, MemberTableCellType.ENUM), types);
    }

    private static List<String> valuesOf(String... builtinKeys) {
        var columns = java.util.Arrays.stream(builtinKeys)
                .map(MemberTableColumn::builtin)
                .toList();
        var table = service.build(station, thisMember(), columns, Set.of(StationPermission.USER), Map.of());
        return table.rows().getFirst().values();
    }

    /**
     * What a membership says about somebody without anybody having asked a question.
     *
     * <p>These are not guarded by the field scopes, because they are not the station's questions: a
     * reader who may see the person at all may see their name and what kind of member they are.
     */
    @Test
    void theBuiltinsSayWhatTheMembershipHolds() {
        var values = valuesOf("name", "memberType", "groups", "tags", "email", "joinDate");

        assertEquals(6, values.size());
        assertTrue(values.getFirst().contains("Toni"), "the name is the name");
        assertEquals(StationUserType.MEMBER.name(), values.get(1), "the kind travels as itself, not as a word");
        assertEquals("", values.get(2), "somebody in no group has nothing there");
        assertEquals("", values.get(3), "and nothing where they carry no tags");
        assertTrue(values.get(4).contains("@"), "the address is the address");
    }

    /** A key nothing answers to is no column, rather than a column of nothing. */
    @Test
    void anUnknownBuiltinIsNoColumn() {
        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.builtin("erfundeneSpalte")),
                Set.of(StationPermission.USER),
                Map.of());

        assertTrue(table.columns().isEmpty());
    }

    /** A column naming nothing at all is dropped before anything tries to read it. */
    @Test
    void aColumnNamingNothingIsDropped() {
        var table = service.build(
                station,
                thisMember(),
                List.of(new MemberTableColumn(MemberTableColumnKind.PROFILE_FIELD, null, null)),
                Set.of(StationPermission.USER, StationPermission.MEMBER_MANAGER),
                Map.of());

        assertTrue(table.columns().isEmpty(), "a question with no question behind it is not one");
    }

    /**
     * An appointment's own question is a column only where the appointment named it.
     *
     * <p>The table is drawn for the register too, and there is no appointment there to have asked
     * anything, so such a column simply has no label and falls away.
     */
    @Test
    void anAppointmentsQuestionNeedsAnAppointment() {
        var withoutAppointment = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.registrationField(4711)),
                Set.of(StationPermission.USER),
                Map.of());
        assertTrue(withoutAppointment.columns().isEmpty());

        var withAppointment = service.build(
                station,
                new MemberTablePeople(List.of(member.id()), Map.of(member.id(), Map.of(4711, "43")), Map.of()),
                List.of(MemberTableColumn.registrationField(4711)),
                Set.of(StationPermission.USER),
                Map.of(4711, new MemberTableQuestion("Schuhgröße", MemberTableCellType.NUMBER)));
        assertEquals("Schuhgröße", withAppointment.columns().getFirst().label());
        assertEquals(
                MemberTableCellType.NUMBER, withAppointment.columns().getFirst().type());
        assertEquals("43", withAppointment.rows().getFirst().values().getFirst());
    }

    /** Somebody the station does not have is not a row, whoever asked for them. */
    @Test
    void anUnknownPersonIsNoRow() {
        var table = service.build(
                station,
                MemberTablePeople.of(List.of(member.id(), 987654)),
                List.of(MemberTableColumn.builtin("name")),
                Set.of(StationPermission.USER),
                Map.of());

        assertEquals(1, table.rows().size());
    }

    /** A date is shown the way a date is written here, and an answer nobody gave stays empty. */
    @Test
    void aDateReadsAsADate() {
        var dateField = profileFieldRepo.create(
                station.id(), "Eintritt", ProfileFieldType.DATE, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(dateField.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), dateField.id(), StringNode.valueOf("2026-03-09"));

        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(dateField.id())),
                Set.of(StationPermission.USER),
                Map.of());

        assertEquals("09.03.2026", table.rows().getFirst().values().getFirst());
    }

    /** A yes or no reads as a word, and an answer nobody gave stays empty rather than saying no. */
    @Test
    void aYesOrNoReadsAsAWord() {
        var flag = profileFieldRepo.create(
                station.id(),
                "Führerschein",
                ProfileFieldType.BOOLEAN,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(flag.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), flag.id(), StringNode.valueOf("true"));

        var unanswered = profileFieldRepo.create(
                station.id(), "Anhänger", ProfileFieldType.BOOLEAN, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(unanswered.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(flag.id()), MemberTableColumn.profileField(unanswered.id())),
                Set.of(StationPermission.USER),
                Map.of());

        var values = table.rows().getFirst().values();
        assertEquals("Ja", values.getFirst());
        assertEquals("", values.get(1), "a question nobody answered says nothing, rather than no");
    }

    /**
     * An answer that is not the shape its question expects is printed as it stands.
     *
     * <p>A question that was a line of text before somebody made it a date still holds whatever was
     * typed then, and a table that threw its hands up at one cell would take the whole sheet with it.
     */
    @Test
    void ananswerOfTheWrongShapeIsPrintedAsItIs() {
        var date = profileFieldRepo.create(
                station.id(), "Seit wann", ProfileFieldType.DATE, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.assignToRole(date.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), date.id(), StringNode.valueOf("schon lange"));

        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(date.id())),
                Set.of(StationPermission.USER),
                Map.of());

        assertEquals("schon lange", table.rows().getFirst().values().getFirst());
    }

    /** An age whose question counts from nothing has nothing to count, and says so quietly. */
    @Test
    void anAgeCountingFromNothingIsEmpty() {
        var age = profileFieldRepo.create(
                station.id(),
                "Alter ohne Quelle",
                ProfileFieldType.AGE,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(age.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(age.id())),
                Set.of(StationPermission.USER),
                Map.of());

        assertEquals("", table.rows().getFirst().values().getFirst());
    }

    /**
     * How old somebody is, worked out rather than read.
     *
     * <p>Nothing stores an age: the question holds the day somebody was born and the age is counted
     * wherever it is shown, so choosing the age column reads the date behind it and prints neither it
     * nor a number that was true once.
     */
    @Test
    void anAgeIsCountedFromTheDayBehindIt() {
        var born = profileFieldRepo.create(
                station.id(),
                "Geburtstag",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(born.id(), ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(member.id(), born.id(), StringNode.valueOf("2000-01-01"));

        var age = profileFieldRepo.create(
                station.id(),
                "Alter",
                ProfileFieldType.AGE,
                ProfileFieldConfig.parse("{\"sourceFieldId\":" + born.id() + "}"),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(age.id(), ProfileFieldScope.MEMBER, 0, null, null, null);

        var table = service.build(
                station,
                thisMember(),
                List.of(MemberTableColumn.profileField(age.id())),
                Set.of(StationPermission.USER),
                Map.of());

        int expected = LocalDate.now().getYear() - 2000;
        assertEquals(String.valueOf(expected), table.rows().getFirst().values().getFirst());
    }
}
