/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.util.List;
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
}
