/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.DecimalNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        return kept(field, answer).orElseThrow();
    }

    /** Answers one question for a new member and reads back what the profile keeps, if anything. */
    private static Optional<String> kept(ProfileField field, String answer) {
        int member = freshMember();
        service.setValues(member, List.of(new FieldValueEntry(field.id(), answer)), member);
        return keptFor(member, field);
    }

    private static Optional<String> keptFor(int member, ProfileField field) {
        return service.findValues(member).stream()
                .filter(value -> value.fieldId() == field.id())
                .findFirst()
                .map(ProfileFieldService.MergedValue::value);
    }

    private static void refused(ProfileField field, String answer) {
        int member = freshMember();
        assertThrows(
                RefusalResponse.class,
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

    /** A number is a whole one, as the box every screen draws for it, and is kept as a number. */
    @Test
    void aNumberIsWholeAndKeptAsANumber() {
        var field = ask(ProfileFieldType.NUMBER, "{}");

        assertEquals("42", stored(field, "42"));
        assertEquals("42", stored(field, "\"42\""));
        refused(field, "42.5");
        refused(field, "\"42,5\"");
        refused(field, "\"zweiundvierzig\"");
    }

    /** An answer stored before anything checked it is not measured again when the save leaves it alone. */
    @Test
    void anUntouchedOldAnswerIsNotMeasuredAgain() {
        var field = ask(ProfileFieldType.NUMBER, "{}");
        int member = freshMember();
        profileFieldRepo.setValue(member, field.id(), DecimalNode.valueOf(new BigDecimal("42.5")));

        service.setValues(member, List.of(new FieldValueEntry(field.id(), "\"42.5\"")), member);

        assertEquals(Optional.of("42.5"), keptFor(member, field));
    }

    @Test
    void aDateTakesAnIsoDayOnly() {
        var field = ask(ProfileFieldType.DATE, "{}");

        assertEquals("\"2011-09-01\"", stored(field, "\"2011-09-01\""));
        refused(field, "\"01.09.2011\"");
    }

    @Test
    void aYesOrNoTakesFourSpellingsInEitherShapeAndKeepsABoolean() {
        var field = ask(ProfileFieldType.BOOLEAN, "{}");

        assertEquals("true", stored(field, "true"));
        assertEquals("true", stored(field, "\"true\""));
        assertEquals("true", stored(field, "\"1\""));
        assertEquals("false", stored(field, "\"0\""));
        refused(field, "\"ja\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        var field = ask(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\",\"L\"]}");

        assertEquals("\"M\"", stored(field, "\"M\""));
        refused(field, "\"XL\"");
    }

    /** Nothing said is nothing kept: the answer is left out rather than stored empty. */
    @Test
    void aRequiredQuestionMayStayEmpty() {
        var field = ask(ProfileFieldType.TEXT, "{}", true);

        assertEquals(Optional.empty(), kept(field, "\"\""));
    }

    /** Emptying an answer takes it off the profile. */
    @Test
    void anEmptiedAnswerIsRemoved() {
        var field = ask(ProfileFieldType.TEXT, "{}");
        int member = freshMember();
        service.setValues(member, List.of(new FieldValueEntry(field.id(), "\"Florian\"")), member);

        service.setValues(member, List.of(new FieldValueEntry(field.id(), "\"\"")), member);

        assertEquals(Optional.empty(), keptFor(member, field));
    }

    /** An age counts itself from a date, so a value written under it is refused and nothing is kept. */
    @Test
    void anAgeTakesNoAnswer() {
        var field = ask(ProfileFieldType.AGE, "{}");
        int member = freshMember();

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> service.setValues(member, List.of(new FieldValueEntry(field.id(), "15")), member));

        assertEquals(Refusal.PROFILE_AGE_TAKES_NO_ANSWER, refusal.refusal());
        assertTrue(service.findValues(member).isEmpty());
        assertEquals(Optional.empty(), kept(field, "\"\""));
    }

    @Test
    void aHeadingKeepsNothingWrittenUnderIt() {
        var field = ask(ProfileFieldType.SECTION, "{}");

        assertEquals(Optional.empty(), kept(field, "\"Notiz\""));
    }

    /** Every spelling of yes is kept as one, so the member table prints each of them as yes. */
    @Test
    void theMemberTablePrintsEverySpellingOfYesAsJa() {
        var field = ask(ProfileFieldType.BOOLEAN, "{}");

        assertEquals("Ja", printed(field, "true"));
        assertEquals("Ja", printed(field, "\"true\""));
        assertEquals("Ja", printed(field, "\"1\""));
        assertEquals("Nein", printed(field, "false"));
    }

    @Test
    void theMemberTablePrintsNumbersChoicesAndDatesAsTheyRead() {
        assertEquals("42", printed(ask(ProfileFieldType.NUMBER, "{}"), "42"));
        assertEquals("M", printed(ask(ProfileFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}"), "\"M\""));
        assertEquals("01.09.2011", printed(ask(ProfileFieldType.DATE, "{}"), "\"2011-09-01\""));
        assertEquals("Florian", printed(ask(ProfileFieldType.TEXT, "{}"), "\"Florian\""));
    }
}
