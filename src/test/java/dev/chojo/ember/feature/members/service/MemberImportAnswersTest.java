/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.accountlink.service.TestAccountLinks;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.service.MemberImportService.ColumnMapping;
import dev.chojo.ember.feature.question.FieldType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What the member import writes into a profile, one cell under one kind of question.
 *
 * <p>A cell is measured against its question as an answer typed on the profile is. One the question
 * does not take is left out and named in a warning on its row, in the preview and in the import.
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
                TestAccountLinks.inviteService(
                        accountRepo, stationRepo, stationMemberRepo, newGroupMemberships(), mock(AuthService.class)));
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
     * What one import of one cell left behind.
     *
     * @param stored   what the profile holds for the question afterwards, empty where nothing was
     *                 written
     * @param warnings what the import said about the row
     * @param previewWarnings what the preview of the same file said about it
     */
    private record Imported(Optional<String> stored, List<String> warnings, List<String> previewWarnings) {}

    /** Imports one person with one cell under a new question of the given kind. */
    private static Imported imported(FieldType type, String config, String cell) {
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
        String csv = "Vorname;Name;Wert\nMax;Muster;" + cell + "\n";
        var mappings =
                List.of(map("Vorname", "firstName"), map("Name", "lastName"), map("Wert", "field:" + field.id()));
        var preview = service.preview(station.id(), csv, ";", mappings, List.of());
        var result = service.importMembers(station.id(), csv, ";", mappings, List.of(), SetupMail.SEND_NOW);
        int member = stationMemberRepo.findByStation(station.id()).getFirst().id();
        var stored = profileFieldRepo.findValues(member).stream()
                .filter(value -> value.fieldId() == field.id())
                .map(value -> value.value())
                .findFirst();
        return new Imported(stored, result.warnings(), preview.warnings());
    }

    private static void leftOutAndNamed(Imported imported, String cell) {
        assertEquals(Optional.empty(), imported.stored());
        assertTrue(
                imported.warnings().stream().anyMatch(warning -> warning.contains(cell)),
                imported.warnings()::toString);
        assertTrue(
                imported.previewWarnings().stream().anyMatch(warning -> warning.contains(cell)),
                imported.previewWarnings()::toString);
    }

    /** An age counts itself from a date, so a column mapped onto one is left out. */
    @Test
    void anAgeColumnIsSkipped() {
        assertEquals(Optional.empty(), imported(FieldType.AGE, "{}", "15").stored());
    }

    @Test
    void aWholeNumberIsKeptAsANumber() {
        var imported = imported(FieldType.NUMBER, "{}", "42");

        assertEquals(Optional.of("42"), imported.stored());
        assertEquals(List.of(), imported.warnings());
    }

    @Test
    void aNumberThatIsNoWholeNumberIsLeftOut() {
        leftOutAndNamed(imported(FieldType.NUMBER, "{}", "viele"), "viele");
        leftOutAndNamed(imported(FieldType.NUMBER, "{}", "2,5"), "2,5");
    }

    @Test
    void aGermanDayIsKeptAsAnIsoDay() {
        assertEquals(
                Optional.of("\"2011-09-01\""),
                imported(FieldType.DATE, "{}", "01.09.2011").stored());
    }

    @Test
    void aDayThatIsNoDayIsLeftOut() {
        leftOutAndNamed(imported(FieldType.DATE, "{}", "irgendwann"), "irgendwann");
    }

    @Test
    void aChoiceTheQuestionDoesNotOfferIsLeftOut() {
        assertEquals(
                Optional.of("\"M\""),
                imported(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "M").stored());
        leftOutAndNamed(imported(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "XL"), "XL");
    }

    @Test
    void aYesOrNoReadsItsWordsInBothLanguagesAndLeavesAnyOtherOut() {
        assertEquals(Optional.of("true"), imported(FieldType.BOOLEAN, "{}", "x").stored());
        assertEquals(
                Optional.of("false"), imported(FieldType.BOOLEAN, "{}", "Nein").stored());
        leftOutAndNamed(imported(FieldType.BOOLEAN, "{}", "vielleicht"), "vielleicht");
    }
}
