/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * What a member profile takes as an answer today, how it keeps it and how the member table prints it.
 *
 * <p>Written down before the field types are brought together, so that every later change can show
 * which of these it meant to change and that it left the rest alone.
 */
class ProfileAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ProfileFieldService service;
    private static MemberTableService table;
    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        service = new ProfileFieldService(
                profileFieldRepo,
                profileFieldChangeRepo,
                mock(Notifier.class),
                stationMemberRepo,
                accountRepo,
                clusterProfileFieldRepo,
                memberGroupRepo,
                memberPermissionResolver);
        table = new MemberTableService(profileFieldRepo, stationMemberRepo);
        station = stationRepo.create("ProfileAnswersStation");
        account = accountRepo.create("profile-answers@test.com", "Paula", "Profil");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static ProfileField ask(ProfileFieldType type, String config) {
        return ask(type, config, false);
    }

    private static ProfileField ask(ProfileFieldType type, String config, boolean required) {
        var field = service.create(
                station.id(),
                type.name() + " " + NAMES.incrementAndGet(),
                type,
                ProfileFieldConfig.parse(config),
                required,
                false,
                null);
        service.assignToRole(field.id(), ProfileFieldScope.MEMBER, NAMES.get(), null, null, null);
        return field;
    }

    private static int freshMember() {
        var other = accountRepo.create("profile-answers-" + NAMES.incrementAndGet() + "@test.com", "Paul", "Profil");
        return stationMemberRepo.create(station.id(), other.id()).id();
    }

    /** Answers one question for a new member and reads back what the profile keeps. */
    private static String stored(ProfileField field, String answer) {
        int member = freshMember();
        service.setValues(member, List.of(new FieldValueEntry(field.id(), answer)), member);
        return service.findValues(member).stream()
                .filter(value -> value.fieldId() == field.id())
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static void refused(ProfileField field, String answer) {
        int member = freshMember();
        assertThrows(
                BadRequestResponse.class,
                () -> service.setValues(member, List.of(new FieldValueEntry(field.id(), answer)), member),
                answer + " under " + field.fieldType());
    }

    /** What the member table prints for one stored answer. */
    private static String printed(ProfileField field, String answer) {
        int member = freshMember();
        service.setValues(member, List.of(new FieldValueEntry(field.id(), answer)), member);
        return table.build(
                        station,
                        MemberTablePeople.of(List.of(member)),
                        List.of(MemberTableColumn.profileField(field.id())),
                        Set.of(StationPermission.USER),
                        Map.of())
                .rows()
                .getFirst()
                .values()
                .getFirst();
    }

    @Test
    void textTakesAnyLineAndKeepsItAsAJsonString() {
        var field = ask(ProfileFieldType.TEXT, "{}");

        assertEquals("\"irgendwas\"", stored(field, "\"irgendwas\""));
        assertEquals("\"42\"", stored(field, "\"42\""));
    }

    @Test
    void aNumberTakesAFractionAndKeepsWhatWasSent() {
        var field = ask(ProfileFieldType.NUMBER, "{}");

        assertEquals("42.5", stored(field, "42.5"));
        assertEquals("\"42,5\"", stored(field, "\"42,5\""));
        refused(field, "\"zweiundvierzig\"");
    }

    @Test
    void aDateTakesAnIsoDayOnly() {
        var field = ask(ProfileFieldType.DATE, "{}");

        assertEquals("\"2011-09-01\"", stored(field, "\"2011-09-01\""));
        refused(field, "\"01.09.2011\"");
    }

    @Test
    void aYesOrNoTakesFourSpellingsInEitherShape() {
        var field = ask(ProfileFieldType.BOOLEAN, "{}");

        assertEquals("true", stored(field, "true"));
        assertEquals("\"true\"", stored(field, "\"true\""));
        assertEquals("\"1\"", stored(field, "\"1\""));
        assertEquals("\"0\"", stored(field, "\"0\""));
        refused(field, "\"ja\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        var field = ask(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\",\"L\"]}");

        assertEquals("\"M\"", stored(field, "\"M\""));
        refused(field, "\"XL\"");
    }

    @Test
    void aRequiredQuestionMayStayEmpty() {
        var field = ask(ProfileFieldType.TEXT, "{}", true);

        assertEquals("\"\"", stored(field, "\"\""));
    }

    @Test
    void anAgeTakesADecimalThatNothingShows() {
        var field = ask(ProfileFieldType.AGE, "{}");

        assertEquals("15", stored(field, "15"));
        refused(field, "\"fünfzehn\"");
    }

    @Test
    void aHeadingKeepsWhateverIsWrittenUnderIt() {
        var field = ask(ProfileFieldType.SECTION, "{}");

        assertEquals("\"Notiz\"", stored(field, "\"Notiz\""));
    }

    @Test
    void theMemberTablePrintsYesAsJaAndEveryOtherSpellingAsNein() {
        var field = ask(ProfileFieldType.BOOLEAN, "{}");

        assertEquals("Ja", printed(field, "true"));
        assertEquals("Ja", printed(field, "\"true\""));
        assertEquals("Nein", printed(field, "\"1\""));
        assertEquals("Nein", printed(field, "false"));
    }

    @Test
    void theMemberTablePrintsNumbersChoicesAndDatesAsTheyRead() {
        assertEquals("42.5", printed(ask(ProfileFieldType.NUMBER, "{}"), "42.5"));
        assertEquals("M", printed(ask(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}"), "\"M\""));
        assertEquals("01.09.2011", printed(ask(ProfileFieldType.DATE, "{}"), "\"2011-09-01\""));
        assertEquals("Florian", printed(ask(ProfileFieldType.TEXT, "{}"), "\"Florian\""));
    }
}
