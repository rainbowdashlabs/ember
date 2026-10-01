/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.service.MemberImportService.ColumnMapping;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * What the member import writes into a profile today, one cell under one kind of question.
 *
 * <p>Nothing measures an imported cell against its question: what does not read as the kind it
 * answers is kept as the text it was. Written down before the field types are brought together, so
 * the change that starts checking them shows what it turned away.
 */
class MemberImportAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static MemberImportService service;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new MemberImportService(
                accountRepo,
                stationMemberRepo,
                memberGroupRepo,
                newGroupMemberships(),
                profileFieldRepo,
                new AccountInviteService(accountRepo, mock(AuthService.class)));
        station = stationRepo.create("ImportAnswersStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    /** Takes everybody an earlier reading brought in away, so the next one creates its person afresh. */
    private static void removeEverybody() {
        for (var member : stationMemberRepo.findByStation(station.id(), true)) {
            var account = member.accountId();
            stationMemberRepo.delete(member.id());
            accountRepo.delete(account);
        }
    }

    private static ColumnMapping map(String column, String target) {
        return new ColumnMapping(column, target, 0, " ", Map.of(), "", 0);
    }

    /**
     * Imports one person with one cell under a new question of the given kind.
     *
     * @return what the profile holds for that question afterwards, empty where nothing was written
     */
    private static Optional<String> imported(ProfileFieldType type, String config, String cell) {
        removeEverybody();
        var field = profileFieldRepo.create(
                station.id(),
                type.name() + " " + NAMES.incrementAndGet(),
                type,
                ProfileFieldConfig.parse(config),
                false,
                false,
                null);
        profileFieldRepo.assignToRole(field.id(), ProfileFieldScope.MEMBER, 99, null, null, null);
        service.importMembers(
                station.id(),
                "Vorname;Name;Wert\nMax;Muster;" + cell + "\n",
                ";",
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Wert", "field:" + field.id())),
                List.of(),
                SetupMail.SEND_NOW);
        int member = stationMemberRepo.findByStation(station.id()).getFirst().id();
        return profileFieldRepo.findValues(member).stream()
                .filter(value -> value.fieldId() == field.id())
                .map(value -> value.value())
                .findFirst();
    }

    /** An age counts itself from a date, so a column mapped onto one is left out. */
    @Test
    void anAgeColumnIsSkipped() {
        assertEquals(Optional.empty(), imported(ProfileFieldType.AGE, "{}", "15"));
    }

    @Test
    void aNumberThatIsNoNumberIsKeptAsText() {
        assertEquals(Optional.of("\"viele\""), imported(ProfileFieldType.NUMBER, "{}", "viele"));
    }

    @Test
    void aDayThatIsNoDayIsKeptAsText() {
        assertEquals(Optional.of("\"irgendwann\""), imported(ProfileFieldType.DATE, "{}", "irgendwann"));
    }

    @Test
    void aChoiceTheQuestionDoesNotOfferIsKept() {
        assertEquals(Optional.of("\"XL\""), imported(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "XL"));
    }

    @Test
    void aYesOrNoReadsSixWordsEachWayAndKeepsAnyOtherAsText() {
        assertEquals(Optional.of("true"), imported(ProfileFieldType.BOOLEAN, "{}", "x"));
        assertEquals(Optional.of("false"), imported(ProfileFieldType.BOOLEAN, "{}", "Nein"));
        assertEquals(Optional.of("\"vielleicht\""), imported(ProfileFieldType.BOOLEAN, "{}", "vielleicht"));
    }
}
